'use strict';
const crypto = require('node:crypto');

/** Strict shape for the monitor's report: at most 30 checks, short printable text, bounded timings. */
function parseSourceHealth(raw) {
  let value;
  try { value = JSON.parse(raw); } catch { return null; }
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).join(',') !== 'checks') return null;
  const checks = value.checks;
  if (!Array.isArray(checks) || checks.length < 1 || checks.length > 30) return null;
  const clean = [];
  for (const check of checks) {
    if (!check || typeof check !== 'object' || Array.isArray(check)) return null;
    const keys = Object.keys(check).sort().join(',');
    if (keys !== 'detail,ms,name,ok') return null;
    if (typeof check.name !== 'string' || !/^[\p{L}\p{N} ·.:()\/+-]{1,60}$/u.test(check.name)) return null;
    if (typeof check.ok !== 'boolean' || !Number.isSafeInteger(check.ms) || check.ms < 0 || check.ms > 600000) return null;
    if (typeof check.detail !== 'string') return null;
    // Control characters are dropped and the text is capped; the monitor's text is never trusted as markup.
    clean.push({ name: check.name, ok: check.ok, ms: check.ms, detail: check.detail.replace(/[\u0000-\u001f\u007f]/g, ' ').slice(0, 200) });
  }
  return { checks: clean };
}

/** Constant-time check of the shared secret (header x-health-secret or Bearer). Needs >= 32 characters. */
function healthSecretOk(req, secret) {
  if (typeof secret !== 'string' || secret.length < 32 || secret.length > 2048) return false;
  const bearer = /^Bearer ([\x21-\x7e]{1,2048})$/.exec(req.headers.authorization || '')?.[1];
  const supplied = [req.headers['x-health-secret'], bearer].filter(v => typeof v === 'string' && /^[\x21-\x7e]{1,2048}$/.test(v));
  const expected = crypto.createHash('sha256').update(secret, 'utf8').digest();
  return supplied.some(v => crypto.timingSafeEqual(crypto.createHash('sha256').update(v, 'utf8').digest(), expected));
}

function createSourceHealthStore(env = process.env, fetchImpl = fetch) {
  return {
    async record(checks) {
      const key = env.SUPABASE_SECRET_KEY || '';
      let base;
      try { base = new URL(env.SUPABASE_URL); } catch { throw new Error('source_health_unavailable'); }
      if (base.protocol !== 'https:' || !base.hostname.endsWith('.supabase.co') || !/^sb_secret_[A-Za-z0-9_-]+$/.test(key)) throw new Error('source_health_unavailable');
      const response = await fetchImpl(new URL('/rest/v1/rpc/spaceanime_source_health_record', base), {
        method: 'POST',
        headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_checks: checks }), signal: AbortSignal.timeout(8000), redirect: 'error',
      });
      if (!response.ok) { console.error(`[sources] record -> HTTP ${response.status}`); throw new Error('source_health_unavailable'); }
    },
  };
}

module.exports = { parseSourceHealth, healthSecretOk, createSourceHealthStore };
