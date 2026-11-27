'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');
const { PERMISSIONS, permissionsFor, createAdmin } = require('./admin');

const USER = '11111111-2222-4333-8444-555555555555';
const ORDER = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const ORIGIN = 'https://admin.example.test';
async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}
/** Fake Supabase identity: the token names the situation. */
function fakeAdmin(audits = []) {
  const people = {
    nomfa: { userId: USER, aal2: false, roles: [] },
    norole: { userId: USER, aal2: true, roles: [] },
    support: { userId: USER, aal2: true, roles: ['support'] },
    moderator: { userId: USER, aal2: true, roles: ['moderator'] },
    finance: { userId: USER, aal2: true, roles: ['finance'] },
    owner: { userId: USER, aal2: true, roles: ['owner'] },
  };
  return {
    async staffMe(token) { return people[token] || null; },
    async audit(...args) { audits.push(args); },
    async auditList() { return [{ id: 1, action: 'x' }]; },
    async overview() { return { users: { total: 4 }, active: { d1: 2 }, vip: { active_total: 1 }, revenue: { month_cents: 1000 }, orders: { open: 0 } }; },
    async finance() { return { orders: [{ id: ORDER, mp_payment_id: 123, status: 'pending' }], by_status_30d: {} }; },
    async adsConfig() { return { enabled: true }; },
  };
}
const get = (base, path, token) => fetch(`${base}${path}`, { headers: token ? { authorization: `Bearer ${token}` } : {} });

// Every admin route must appear here with the permission it needs; the test below walks the whole table.
const ADMIN_ROUTES = [
  { path: '/api/admin/me', needs: null },
  { path: '/api/admin/audit', needs: 'staff.audit.read' },
  { path: '/api/admin/overview', needs: 'overview.read' },
  { path: '/api/admin/finance', needs: 'finance.read' },
  { path: '/api/admin/ads', needs: 'ads.read' },
];

test('every admin route rejects anonymous, invalid, no-MFA and no-role callers', async () => {
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    for (const { path } of ADMIN_ROUTES) {
      assert.equal((await get(base, path)).status, 401, `${path} anonymous`);
      assert.equal((await get(base, path, 'forged')).status, 401, `${path} invalid token`);
      const nomfa = await get(base, path, 'nomfa');
      assert.equal(nomfa.status, 403, `${path} without MFA`);
      assert.equal((await nomfa.json()).error, 'mfa_required');
      assert.equal((await get(base, path, 'norole')).status, 403, `${path} without a role`);
    }
  });
});

test('roles only reach what their permissions allow', async () => {
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    for (const { path, needs } of ADMIN_ROUTES) {
      for (const role of ['support', 'moderator', 'finance', 'owner']) {
        const expected = role === 'owner' || needs === null || permissionsFor([role]).includes(needs);
        assert.equal((await get(base, path, role)).status, expected ? 200 : 403, `${path} as ${role}`);
      }
    }
    const me = await (await get(base, '/api/admin/me', 'support')).json();
    assert.deepEqual(me.roles, ['support']);
    assert.ok(me.permissions.includes('users.read') && !me.permissions.includes('finance.read'));
  });
});

test('owner-only permissions are held by no other role', () => {
  for (const name of Object.keys(PERMISSIONS)) assert.ok(permissionsFor(['owner']).includes(name));
  for (const name of ['ads.write', 'releases.write', 'announcements.write', 'staff.audit.read']) {
    for (const role of ['finance', 'support', 'moderator']) assert.ok(!permissionsFor([role]).includes(name), `${role} must not hold ${name}`);
  }
  assert.deepEqual(permissionsFor(['hacker', 'admin']), []);
});

test('unknown admin paths answer 404', async () => {
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    assert.equal((await get(base, '/api/admin/nope', 'owner')).status, 404);
  });
});

test('CORS answers only the configured admin origin', async () => {
  await withServer(createHandler({ env: { ADMIN_ORIGIN: ORIGIN }, admin: fakeAdmin() }), async base => {
    const ok = await fetch(`${base}/api/admin/me`, { method: 'OPTIONS', headers: { origin: ORIGIN, 'access-control-request-method': 'GET' } });
    assert.equal(ok.status, 204);
    assert.equal(ok.headers.get('access-control-allow-origin'), ORIGIN);
    const other = await fetch(`${base}/api/admin/me`, { method: 'OPTIONS', headers: { origin: 'https://evil.example.test' } });
    assert.equal(other.status, 403);
    assert.equal(other.headers.get('access-control-allow-origin'), null);
    const real = await fetch(`${base}/api/admin/me`, { headers: { authorization: 'Bearer owner', origin: ORIGIN } });
    assert.equal(real.headers.get('access-control-allow-origin'), ORIGIN);
    assert.equal(real.headers.get('access-control-allow-origin') === '*', false);
  });
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    const r = await fetch(`${base}/api/admin/me`, { method: 'OPTIONS', headers: { origin: ORIGIN } });
    assert.equal(r.status, 403);
  });
});

test('staffMe drops roles without MFA and the audit write goes through the service key', async () => {
  const calls = [];
  const env = { SUPABASE_URL: 'https://example.supabase.co', SUPABASE_PUBLISHABLE_KEY: 'sb_publishable_synthetic', SUPABASE_SECRET_KEY: 'sb_secret_synthetic' };
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), headers: init.headers, body: init.body });
    if (String(url).endsWith('spaceanime_staff_me')) return new Response(JSON.stringify({ user_id: USER, aal2: false, roles: ['owner'] }), { status: 200 });
    return new Response(null, { status: 204 });
  };
  const admin = createAdmin(env, fetchImpl);
  const me = await admin.staffMe('user-token');
  assert.deepEqual(me.roles, []);
  assert.equal(calls[0].headers.Authorization, 'Bearer user-token');
  assert.equal(calls[0].headers.apikey, 'sb_publishable_synthetic');
  await admin.audit(USER, 'ads.update', 'app_runtime_config', 'singleton', { before: 1, after: 2 });
  assert.equal(calls[1].headers.apikey, 'sb_secret_synthetic');
  assert.equal(JSON.parse(calls[1].body).p_action, 'ads.update');
  const rejecting = createAdmin(env, async () => new Response('{}', { status: 401 }));
  assert.equal(await rejecting.staffMe('bad'), null);
});

test('the panel is served at /admin/ with strict headers and nothing else leaks', async () => {
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    const page = await fetch(`${base}/admin/`);
    assert.equal(page.status, 200);
    assert.match(page.headers.get('content-type'), /text\/html/);
    assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/);
    assert.equal(page.headers.get('x-content-type-options'), 'nosniff');
    assert.match(await page.text(), /SpaceAnime/);
    assert.equal((await fetch(`${base}/admin/app.js`)).status, 200);
    assert.equal((await fetch(`${base}/admin`, { redirect: 'manual' })).status, 301);
    for (const bad of ['/admin/../server.js', '/admin/%2e%2e/server.js', '/admin/README.md', '/admin/.env', '/admin/app.js/../../server.js']) {
      assert.equal((await fetch(`${base}${bad}`)).status, 404, bad);
    }
    assert.equal((await fetch(`${base}/admin/`, { method: 'POST' })).status, 404);
  });
});

test('overview hides money and VIP data from roles without finance access', async () => {
  await withServer(createHandler({ env: {}, admin: fakeAdmin() }), async base => {
    const support = await (await get(base, '/api/admin/overview', 'support')).json();
    assert.equal(support.users.total, 4);
    for (const hidden of ['revenue', 'vip', 'orders']) assert.equal(support[hidden], undefined, `${hidden} must be hidden from support`);
    const finance = await (await get(base, '/api/admin/overview', 'finance')).json();
    assert.equal(finance.revenue.month_cents, 1000);
    assert.equal((await get(base, '/api/admin/finance', 'support')).status, 403);
  });
});

test('recheck is finance-only, audited, and asks Mercado Pago before changing anything', async () => {
  const audits = []; const settled = [];
  const store = { async confirmPayment(order, payment, cents) { settled.push([order, payment, cents]); return { status: 'paid' }; } };
  const mp = { async getPayment() { return { id: 123, status: 'approved', external_reference: ORDER, currency_id: 'BRL', payment_method_id: 'pix', transaction_amount: 10 }; } };
  const env = { MP_ACCESS_TOKEN: 'TEST-0000000000000000-synthetic' };
  await withServer(createHandler({ env, admin: fakeAdmin(audits), vipStore: store, mercadoPago: mp }), async base => {
    const post = token => fetch(`${base}/api/admin/finance/orders/${ORDER}/recheck`, { method: 'POST', headers: token ? { authorization: `Bearer ${token}` } : {} });
    assert.equal((await post()).status, 401);
    for (const role of ['support', 'moderator']) assert.equal((await post(role)).status, 403, role);
    assert.equal(settled.length, 0);
    const r = await post('finance');
    assert.equal(r.status, 200);
    assert.equal((await r.json()).status, 'paid');
    assert.deepEqual(settled[0], [ORDER, 123, 1000]);
    assert.equal(audits[0][1], 'finance.recheck_order');
  });
});
