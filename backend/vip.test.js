'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const { createHandler } = require('./server');
const { verifyWebhookSignature, settlePayment, safeTicketUrl } = require('./vip');
const { parseRuntimeConfigV2 } = require('./runtime-config-store');

const USER = '11111111-2222-4333-8444-555555555555';
const ORDER = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const env = { MP_ACCESS_TOKEN: 'TEST-0000000000000000-synthetic' };

async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}
function fakeStore() {
  const state = { orders: new Map(), vip: null, confirmed: [] };
  return {
    state,
    async createOrder(userId, plan) { state.orders.set(ORDER, { userId, plan, status: 'created', payment_id: null }); return ORDER; },
    async attachPayment(orderId, paymentId) { const o = state.orders.get(orderId); o.payment_id = paymentId; o.status = 'pending'; },
    async orderStatus(orderId, userId) { const o = state.orders.get(orderId); return o && o.userId === userId ? { status: o.status, payment_id: o.payment_id } : null; },
    async confirmPayment(orderId, paymentId, cents) {
      state.confirmed.push({ orderId, paymentId, cents });
      const o = state.orders.get(orderId);
      const price = { ouro: 1000, platina: 2000, ametista: 3000 }[o.plan];
      if (cents !== price || paymentId !== o.payment_id) return { status: 'rejected' };
      o.status = 'paid'; state.vip = { active: true, plan: o.plan, expires_at: '2026-12-25T00:00:00Z' };
      return { status: 'paid' };
    },
    async status() { return state.vip || { active: false }; },
  };
}
function fakeMp(payment) {
  return {
    created: [],
    async createPix(args) { this.created.push(args); return { paymentId: 123456, qrCode: '000201pix', qrCodeBase64: 'iVBORw0KGgo=', ticketUrl: 'https://www.mercadopago.com.br/payments/123456/ticket', expiresAt: '2026-11-25T03:00:00.000-00:00' }; },
    async getPayment() { return payment(); },
  };
}
const account = async token => token === 'valid' ? { id: USER, email: 'buyer@example.com' } : null;
const post = (base, body, token = 'valid') => fetch(`${base}/api/vip/create-pix`, {
  method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body),
});

test('create-pix needs a signed-in user and a known plan; the price comes from the server', async () => {
  const store = fakeStore(); const mp = fakeMp(() => ({}));
  await withServer(createHandler({ env, vipStore: store, mercadoPago: mp, verifyAccount: account }), async base => {
    assert.equal((await post(base, { plan: 'ouro' }, 'wrong')).status, 401);
    assert.equal((await post(base, { plan: 'diamante' })).status, 400);
    assert.equal((await post(base, { plan: 'ouro', amount: 1 })).status, 400);
    const r = await post(base, { plan: 'platina' });
    assert.equal(r.status, 201);
    const body = await r.json();
    assert.equal(body.amount_cents, 2000);
    assert.equal(body.qr_code, '000201pix');
    assert.equal(body.order_id, ORDER);
    assert.deepEqual(mp.created[0], { orderId: ORDER, plan: 'platina', email: 'buyer@example.com' });
    assert.equal(store.state.orders.get(ORDER).payment_id, 123456);
  });
});

test('create-pix is closed when the Mercado Pago token is missing', async () => {
  await withServer(createHandler({ env: {}, vipStore: fakeStore(), mercadoPago: fakeMp(() => ({})), verifyAccount: account }), async base => {
    assert.equal((await post(base, { plan: 'ouro' })).status, 503);
  });
});

test('order polling activates VIP only for an approved Pix of the exact price', async () => {
  const store = fakeStore();
  let payment = { id: 123456, status: 'pending', external_reference: ORDER, currency_id: 'BRL', payment_method_id: 'pix', transaction_amount: 30 };
  const mp = fakeMp(() => payment);
  await withServer(createHandler({ env, vipStore: store, mercadoPago: mp, verifyAccount: account }), async base => {
    await post(base, { plan: 'ametista' });
    const get = () => fetch(`${base}/api/vip/orders/${ORDER}`, { headers: { authorization: 'Bearer valid' } }).then(r => r.json());
    assert.equal((await get()).status, 'pending');
    payment = { ...payment, status: 'approved' };
    const done = await get();
    assert.equal(done.status, 'paid');
    assert.equal(done.vip.plan, 'ametista');
    const me = await fetch(`${base}/api/vip/me`, { headers: { authorization: 'Bearer valid' } }).then(r => r.json());
    assert.equal(me.active, true);
  });
});

test('settlement ignores wrong amounts, currencies and foreign references', async () => {
  const store = fakeStore();
  await store.createOrder(USER, 'ametista'); await store.attachPayment(ORDER, 9);
  const run = p => settlePayment({ mp: { getPayment: async () => p }, store, paymentId: '9' });
  assert.equal((await run({ id: 9, status: 'approved', external_reference: ORDER, currency_id: 'BRL', payment_method_id: 'pix', transaction_amount: 1 })).status, 'rejected');
  assert.equal((await run({ id: 9, status: 'approved', external_reference: ORDER, currency_id: 'USD', payment_method_id: 'pix', transaction_amount: 30 })).status, 'ignored');
  assert.equal((await run({ id: 9, status: 'approved', external_reference: 'not-ours', currency_id: 'BRL', payment_method_id: 'pix', transaction_amount: 30 })).status, 'ignored');
  assert.equal(store.state.vip, null);
});

test('webhook checks the Mercado Pago signature when a secret is set', async () => {
  const secret = 'synthetic-webhook-secret';
  const store = fakeStore();
  await store.createOrder(USER, 'ouro'); await store.attachPayment(ORDER, 777);
  const mp = fakeMp(() => ({ id: 777, status: 'approved', external_reference: ORDER, currency_id: 'BRL', payment_method_id: 'pix', transaction_amount: 10 }));
  await withServer(createHandler({ env: { ...env, MP_WEBHOOK_SECRET: secret }, vipStore: store, mercadoPago: mp }), async base => {
    const send = headers => fetch(`${base}/api/vip/webhook?data.id=777&type=payment`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: '{"type":"payment","data":{"id":"777"}}' });
    assert.equal((await send({ 'x-signature': 'ts=1,v1=00', 'x-request-id': 'r1' })).status, 401);
    assert.equal(store.state.vip, null);
    const ts = String(Math.floor(Date.now() / 1000));
    const v1 = crypto.createHmac('sha256', secret).update(`id:777;request-id:r1;ts:${ts};`).digest('hex');
    const ok = await send({ 'x-signature': `ts=${ts},v1=${v1}`, 'x-request-id': 'r1' });
    assert.equal(ok.status, 200);
    assert.equal(store.state.vip.plan, 'ouro');
  });
});

test('signature helper rejects stale timestamps', () => {
  const secret = 's'; const ts = String(Math.floor(Date.now() / 1000) - 3600);
  const v1 = crypto.createHmac('sha256', secret).update(`id:1;request-id:r;ts:${ts};`).digest('hex');
  assert.equal(verifyWebhookSignature({ secret, header: `ts=${ts},v1=${v1}`, requestId: 'r', dataId: '1' }), false);
});

test('ticket links must point to Mercado Pago', () => {
  assert.equal(safeTicketUrl('https://evil.example/pay'), null);
  assert.ok(safeTicketUrl('https://www.mercadopago.com.br/payments/1/ticket'));
});

test('runtime config v2 validates provider and limits', () => {
  const ads = { enabled: true, provider: 'startio', homeBanner: true, catalogBanner: false, interstitialEnabled: true, interstitialEveryEpisodes: 3, rewardedEnabled: true, rewardedPassMinutes: 120 };
  assert.equal(parseRuntimeConfigV2({ schemaVersion: 2, ads }).ads.provider, 'startio');
  assert.throws(() => parseRuntimeConfigV2({ schemaVersion: 2, ads: { ...ads, provider: 'other' } }));
  assert.throws(() => parseRuntimeConfigV2({ schemaVersion: 2, ads: { ...ads, interstitialEveryEpisodes: 0 } }));
});

test('Supabase void RPCs (204, empty body) count as success', async () => {
  const { createVipStore } = require('./vip');
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(String(url));
    if (String(url).endsWith('spaceanime_vip_attach_payment')) return new Response(null, { status: 204 });
    return new Response(JSON.stringify(ORDER), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  const store = createVipStore({ SUPABASE_URL: 'https://example.supabase.co', SUPABASE_SECRET_KEY: 'sb_secret_synthetic' }, fetchImpl);
  assert.equal(await store.createOrder(USER, 'ouro'), ORDER);
  assert.equal(await store.attachPayment(ORDER, 1), null);
  assert.equal(calls.length, 2);
});
