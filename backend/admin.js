'use strict';

/**
 * Admin panel access control. Identity and roles come from Supabase (roles only count after the
 * second factor, see spaceanime_staff_me); this file maps roles to permissions and writes the audit trail.
 */
const ROLES = Object.freeze(['owner', 'finance', 'support', 'moderator']);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// permission -> roles that hold it. `owner` holds every permission (see permissionsFor).
const PERMISSIONS = Object.freeze({
  'overview.read': ['finance', 'support', 'moderator'],
  'finance.read': ['finance'],
  'vip.read': ['finance', 'support'],
  'vip.grant': ['finance'],
  'ads.read': ['finance'],
  'ads.write': [],
  'users.read': ['support'],
  'versions.read': ['finance', 'support'],
  'sources.read': ['finance', 'support', 'moderator'],
  'releases.write': [],
  'announcements.write': [],
  'moderation.read': ['moderator', 'support'],
  'moderation.write': ['moderator'],
  'tickets.write': ['support'],
  'staff.audit.read': [],
});

function permissionsFor(roles) {
  const owned = new Set(Array.isArray(roles) ? roles.filter(r => ROLES.includes(r)) : []);
  return Object.keys(PERMISSIONS).filter(name => owned.has('owner') || PERMISSIONS[name].some(role => owned.has(role)));
}

function validSupabase(env) {
  try {
    const u = new URL(env.SUPABASE_URL);
    return u.protocol === 'https:' && u.hostname.endsWith('.supabase.co') && u.pathname === '/' && !u.search && !u.username && !u.password;
  } catch { return false; }
}

function createAdmin(env = process.env, fetchImpl = fetch) {
  async function rpc(name, { token, body = {} }) {
    const key = token ? env.SUPABASE_PUBLISHABLE_KEY : env.SUPABASE_SECRET_KEY;
    if (!validSupabase(env) || !key || (!token && !/^sb_secret_[A-Za-z0-9_-]+$/.test(key))) throw new Error('admin_unavailable');
    const response = await fetchImpl(new URL(`/rest/v1/rpc/${name}`, env.SUPABASE_URL), {
      method: 'POST',
      headers: { apikey: key, Authorization: `Bearer ${token || key}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body), signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (response.status === 401) return { unauthorized: true };
    if (!response.ok) { console.error(`[admin] rpc ${name} -> HTTP ${response.status}`); throw new Error('admin_unavailable'); }
    const text = await response.text();
    return { value: text.trim() ? JSON.parse(text) : null };
  }
  return {
    /** Verifies the token against Supabase and returns { userId, aal2, roles } or null for an invalid token. */
    async staffMe(token) {
      const result = await rpc('spaceanime_staff_me', { token });
      const me = result.value;
      if (result.unauthorized || !me || !UUID.test(me.user_id || '')) return null;
      return { userId: me.user_id, aal2: me.aal2 === true, roles: me.aal2 === true && Array.isArray(me.roles) ? me.roles.filter(r => ROLES.includes(r)) : [] };
    },
    /** Every admin write must call this; failing to record blocks the action. */
    async audit(actorId, action, targetType, targetId, detail = null) {
      await rpc('spaceanime_staff_audit_write', { body: { p_actor: actorId, p_action: action, p_target_type: targetType, p_target_id: String(targetId), p_detail: detail } });
    },
    async auditList(limit = 100) {
      return (await rpc('spaceanime_staff_audit_list', { body: { p_limit: limit } })).value || [];
    },
    async overview() { return (await rpc('spaceanime_admin_overview', {})).value || {}; },
    async finance(limit = 50) { return (await rpc('spaceanime_admin_finance', { body: { p_limit: limit } })).value || {}; },
    async adsConfig() { return (await rpc('spaceanime_admin_ads_config', {})).value || {}; },
  };
}

module.exports = { ROLES, PERMISSIONS, permissionsFor, createAdmin };
