'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createSandboxCheckout, safeReturnUrl } = require('./billing');
const userId = '11111111-1111-4111-8111-111111111111';
const requestId = '22222222-2222-4222-8222-222222222222';
const defaults = { userId, customerId:'cus_123', requestId, lookupKey:'basic_mensal', successUrl:'https://spaceanime.onrender.com/success', cancelUrl:'https://spaceanime.onrender.com/cancel' };
function fakeStripe(priceOverride = {}, sessionOverride = {}) {
 let calls = 0, sent;
 return { prices: { list: async () => ({ data:[{id:'price_123', active:true, livemode:false, currency:'brl', unit_amount:1299, recurring:{interval:'month',interval_count:1}, ...priceOverride}] }) },
  checkout: { sessions: { create: async (params, opts) => { calls++; sent = { params, opts }; return {id:'cs_test_123',livemode:false,customer:'cus_123',url:'https://checkout.stripe.com/c/pay/test', ...sessionOverride}; } } },
  get calls() { return calls; }, get sent() { return sent; } };
}
test('checkout uses validated sandbox price, subscription and idempotency', async () => {
 const stripe = fakeStripe();
 const result = await createSandboxCheckout({stripe,...defaults});
 assert.equal(result.id,'cs_test_123');
 assert.equal(stripe.sent.params.mode,'subscription');
 assert.equal(stripe.sent.params.customer,'cus_123');
 assert.equal(stripe.sent.params.client_reference_id,userId);
 assert.deepEqual(stripe.sent.params.line_items,[{price:'price_123',quantity:1}]);
 assert.equal(stripe.sent.params.payment_method_types,undefined);
 assert.equal(stripe.sent.opts.idempotencyKey,`checkout:${userId}:${requestId}`);
});
test('rejects modified or live price before creating checkout', async () => {
 for (const override of [{livemode:true},{unit_amount:1},{currency:'usd'},{recurring:{interval:'year',interval_count:1}}]) {
  const stripe=fakeStripe(override);
  await assert.rejects(createSandboxCheckout({stripe,...defaults}),/sandbox_price_mismatch/);
  assert.equal(stripe.calls,0);
 }
});
test('rejects unsafe return and checkout urls', async () => {
 assert.equal(safeReturnUrl('https://spaceanime.onrender.com.evil.example/a'),false);
 assert.equal(safeReturnUrl('http://spaceanime.onrender.com/a'),false);
 await assert.rejects(createSandboxCheckout({stripe:fakeStripe(),...defaults,successUrl:'https://evil.example'}),/invalid_checkout_request/);
 await assert.rejects(createSandboxCheckout({stripe:fakeStripe({}, {url:'https://evil.example'}),...defaults}),/unsafe_checkout_url/);
});
test('rejects unknown plan or invalid authenticated identifiers', async () => {
 await assert.rejects(createSandboxCheckout({stripe:fakeStripe(),...defaults,lookupKey:'premium_gratis'}),/invalid_checkout_request/);
 await assert.rejects(createSandboxCheckout({stripe:fakeStripe(),...defaults,userId:'fake'}),/invalid_checkout_request/);
});
test('retries reuse identical Stripe parameters for the same request', async () => {
 const stripe=fakeStripe();
 await createSandboxCheckout({stripe,...defaults});
 const first=stripe.sent;
 await createSandboxCheckout({stripe,...defaults});
 assert.deepEqual(stripe.sent,first);
});
