'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const Stripe = require('stripe');
const { createHandler, urlValid } = require('./server');
async function withServer(handler, fn) {
 const server = http.createServer(handler);
 await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
 try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}
test('health does not disclose secrets', async () => {
 await withServer(createHandler({env:{STRIPE_WEBHOOK_SECRET:'sensitive'}}), async base => {
  const r = await fetch(`${base}/health`); assert.equal(r.status, 200); assert.ok(!(await r.text()).includes('sensitive'));
 });
});
test('identity requires verified bearer token', async () => {
 await withServer(createHandler({env:{},verifyUser:async token => token === 'valid' ? '123' : null}), async base => {
  assert.equal((await fetch(`${base}/api/me`)).status,401);
  assert.equal((await fetch(`${base}/api/me`,{headers:{authorization:'Bearer wrong'}})).status,401);
  const r=await fetch(`${base}/api/me`,{headers:{authorization:'Bearer valid'}}); assert.equal((await r.json()).user_id,'123');
 });
});
test('sandbox checkout stays closed until fulfillment exists', async () => {
 const env={CHECKOUT_SANDBOX_ENABLED:'true',STRIPE_RESTRICTED_KEY:'rk_test_example',STRIPE_WEBHOOK_SECRET:'test'};
 await withServer(createHandler({env}), async base => {
  const r=await fetch(`${base}/api/billing/checkout`,{method:'POST'}); assert.equal(r.status,503); assert.equal((await r.json()).error,'fulfillment_not_implemented');
 });
});
test('webhook rejects fake signatures and does not acknowledge unprocessed events', async () => {
 const secret='whsec_synthetic_test', key='rk_test_synthetic';
 const body=JSON.stringify({id:'evt_synthetic',type:'checkout.session.completed',data:{object:{}}});
 const signature=new Stripe(key).webhooks.generateTestHeaderString({payload:body,secret});
 await withServer(createHandler({env:{STRIPE_RESTRICTED_KEY:key,STRIPE_WEBHOOK_SECRET:secret}}), async base => {
  const request=sig=>fetch(`${base}/webhooks/stripe`,{method:'POST',headers:{'stripe-signature':sig},body});
  assert.equal((await request('invalid')).status,400);
  const r=await request(signature); assert.equal(r.status,503); assert.equal((await r.json()).error,'event_processing_not_enabled');
 });
});
test('Supabase URL excludes arbitrary hosts and credentials', () => {
 assert.equal(urlValid('https://example.supabase.co',true),true);
 assert.equal(urlValid('https://example.supabase.co.attacker.test',true),false);
 assert.equal(urlValid('https://user:pass@example.supabase.co',true),false);
 assert.equal(urlValid('http://example.supabase.co',true),false);
});

test('financial summary denies unauthenticated and unauthorized users', async () => {
 let reads=0;
 const env={STRIPE_RESTRICTED_KEY:'rk_test_synthetic'};
 const stripeFactory=()=>({balance:{retrieve:async()=>{reads++;return {available:[],pending:[]}}}});
 await withServer(createHandler({env,verifyUser:async()=> '123',authorizeFinancial:async()=>false,stripeFactory}), async base => {
  assert.equal((await fetch(`${base}/api/admin/finance/summary`)).status,401);
  assert.equal((await fetch(`${base}/api/admin/finance/summary`,{headers:{authorization:'Bearer valid'}})).status,403);
 });
 assert.equal(reads,0);
});
test('financial summary returns sandbox balances to an authorized identity only', async () => {
 const env={STRIPE_RESTRICTED_KEY:'rk_test_synthetic'};
 const stripeFactory=()=>({balance:{retrieve:async()=>({available:[{amount:123,currency:'brl',secret:'ignored'}],pending:[]})}});
 await withServer(createHandler({env,verifyUser:async()=> '123',authorizeFinancial:async()=>true,stripeFactory}), async base => {
  const r=await fetch(`${base}/api/admin/finance/summary`,{headers:{authorization:'Bearer valid'}});
  assert.equal(r.status,200); assert.deepEqual(await r.json(),{mode:'sandbox',available:[{amount:123,currency:'brl'}],pending:[]});
 });
});
test('financial endpoint refuses a live key even when authorized', async () => {
 await withServer(createHandler({env:{STRIPE_RESTRICTED_KEY:'rk_live_synthetic'},verifyUser:async()=> '123',authorizeFinancial:async()=>true}), async base => {
  const r=await fetch(`${base}/api/admin/finance/summary`,{headers:{authorization:'Bearer valid'}}); assert.equal(r.status,503);
 });
});
