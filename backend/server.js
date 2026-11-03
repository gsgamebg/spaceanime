'use strict';
const http = require('node:http');
const Stripe = require('stripe');
const { loadRelease, manifest } = require('./update-feed');

const MAX_BODY = 65536;
function json(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(data));
}
function urlValid(value, supabase = false) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password && !u.hash && (!u.port || u.port === '443') && (!supabase || (u.hostname.endsWith('.supabase.co') && u.pathname === '/' && !u.search));
  } catch { return false; }
}
async function readBody(req) {
  const chunks = []; let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > MAX_BODY) throw new Error('body_too_large');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
function createHandler({ env = process.env, stripeFactory = (key) => new Stripe(key), verifyUser, authorizeFinancial } = {}) {
  const authenticate = verifyUser || (async (token) => {
    if (!urlValid(env.SUPABASE_URL, true) || !env.SUPABASE_PUBLISHABLE_KEY) return null;
    const res = await fetch(new URL('/auth/v1/user', env.SUPABASE_URL), {
      headers: { apikey: env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!res.ok) return null;
    const user = await res.json();
    return typeof user.id === 'string' && /^[0-9a-f-]{36}$/i.test(user.id) ? user.id : null;
  });
  const canReadFinancial = authorizeFinancial || (async (token) => {
    if (!urlValid(env.SUPABASE_URL, true) || !env.SUPABASE_PUBLISHABLE_KEY) return false;
    const res = await fetch(new URL('/rest/v1/rpc/authorize_financial_read', env.SUPABASE_URL), {
      method: 'POST',
      headers: { apikey: env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: '{}', signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!res.ok) throw new Error('authorization_unavailable');
    return (await res.json()) === true;
  });
  return async (req, res) => {
    try {
      const path = new URL(req.url, 'http://local').pathname;
      if (req.method === 'GET' && path === '/health') return json(res, 200, { status: 'ok', payments: 'sandbox_only', entitlements: 'disabled' });
      if (req.method === 'GET' && path === '/updates.json') {
        const release = loadRelease(env);
        return release ? json(res, 200, manifest(release)) : json(res, 503, { error: 'updates_not_configured' });
      }
      if (req.method === 'GET' && /^\/downloads\/[0-9]{1,10}-(android\.apk|windows\.zip)$/.test(path)) {
        const release = loadRelease(env);
        const match = /^\/downloads\/([0-9]+)-(android|windows)\.(apk|zip)$/.exec(path);
        if (!release || !match || Number(match[1]) !== release.code || (match[2] === 'android' ? match[3] !== 'apk' : match[3] !== 'zip')) return json(res, 404, { error: 'release_not_found' });
        res.writeHead(302, { Location: release.artifacts[match[2]].url, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
        return res.end();
      }
      if (req.method === 'POST' && path === '/webhooks/stripe') {
        const body = await readBody(req);
        const sig = req.headers['stripe-signature'];
        if (!env.STRIPE_WEBHOOK_SECRET || !sig || !/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '')) return json(res, 503, { error: 'webhook_not_configured' });
        const stripe = stripeFactory(env.STRIPE_RESTRICTED_KEY);
        try { stripe.webhooks.constructEvent(body, sig, env.STRIPE_WEBHOOK_SECRET); }
        catch { return json(res, 400, { error: 'invalid_signature' }); }
        // Do not acknowledge until durable idempotent event handling and entitlement reconciliation exist.
        return json(res, 503, { error: 'event_processing_not_enabled' });
      }
      if (req.method === 'POST' && path === '/api/billing/checkout') {
        if (env.CHECKOUT_SANDBOX_ENABLED !== 'true' || !/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '') || !env.STRIPE_WEBHOOK_SECRET) return json(res, 503, { error: 'sandbox_checkout_disabled' });
        // Prevent accepting money before an actual fulfillment handler is deployed.
        return json(res, 503, { error: 'fulfillment_not_implemented' });
      }
      if (req.method === 'GET' && path === '/api/admin/finance/summary') {
        const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
        if (!match) return json(res, 401, { error: 'unauthorized' });
        let userId, allowed;
        try { userId = await authenticate(match[1]); if (userId) allowed = await canReadFinancial(match[1]); }
        catch { return json(res, 503, { error: 'authorization_unavailable' }); }
        if (!userId) return json(res, 401, { error: 'unauthorized' });
        if (!allowed) return json(res, 403, { error: 'forbidden' });
        if (!/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '')) return json(res, 503, { error: 'stripe_sandbox_not_configured' });
        const balance = await stripeFactory(env.STRIPE_RESTRICTED_KEY).balance.retrieve();
        const clean = values => Array.isArray(values) ? values.map(v => ({ currency: v.currency, amount: v.amount })) : [];
        return json(res, 200, { mode: 'sandbox', available: clean(balance.available), pending: clean(balance.pending) });
      }
      if (req.method === 'GET' && path === '/api/me') {
        const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
        if (!match) return json(res, 401, { error: 'unauthorized' });
        let userId; try { userId = await authenticate(match[1]); } catch { return json(res, 503, { error: 'auth_unavailable' }); }
        if (!userId) return json(res, 401, { error: 'unauthorized' });
        return json(res, 200, { user_id: userId });
      }
      return json(res, 404, { error: 'not_found' });
    } catch (error) {
      if (error?.message === 'body_too_large') return json(res, 413, { error: 'body_too_large' });
      return json(res, 500, { error: 'internal_error' });
    }
  };
}
if (require.main === module) {
  const port = Number(process.env.PORT || 10000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT inválida');
  http.createServer(createHandler()).listen(port, '0.0.0.0', () => console.log('SpaceAnime backend iniciado'));
}
module.exports = { createHandler, urlValid };
