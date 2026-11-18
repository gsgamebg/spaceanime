'use strict';
const test=require('node:test');const assert=require('node:assert/strict');
const {getOrCreateSandboxCustomer}=require('./sandbox-customer');
const userId='11111111-1111-4111-8111-111111111111';
function setup(existing=null) {
 let created=0;let bound=0;let options;
 const stripe={customers:{create:async(_data,o)=>{created++;options=o;return {id:'cus_123',livemode:false}},retrieve:async id=>({id,livemode:false})}};
 const store={findCustomer:async()=>existing,bindCustomer:async(_u,c)=>{bound++;return c}};
 return {stripe,store,stats:()=>({created,bound,options})};
}
test('creates sandbox customer once with stable idempotency and persists binding',async()=>{
 const f=setup(); const result=await getOrCreateSandboxCustomer({...f,userId,sandboxKey:'rk_test_example'});
 assert.equal(result,'cus_123');assert.equal(f.stats().bound,1);assert.equal(f.stats().options.idempotencyKey,`spaceanime-customer:${userId}`);
});
test('reuses existing customer and verifies it belongs to sandbox',async()=>{
 const f=setup('cus_existing');assert.equal(await getOrCreateSandboxCustomer({...f,userId,sandboxKey:'rk_test_example'}),'cus_existing');assert.equal(f.stats().created,0);
});
test('rejects live key or a live/deleted customer',async()=>{
 const f=setup();await assert.rejects(getOrCreateSandboxCustomer({...f,userId,sandboxKey:'rk_live_example'}),/invalid_sandbox_context/);assert.equal(f.stats().created,0);
 f.stripe.customers.create=async()=>({id:'cus_123',livemode:true});await assert.rejects(getOrCreateSandboxCustomer({...f,userId,sandboxKey:'rk_test_example'}),/invalid_sandbox_customer/);
});
test('concurrent binding uses the authoritative customer stored in database',async()=>{
 const f=setup();f.store.bindCustomer=async()=> 'cus_existing';
 assert.equal(await getOrCreateSandboxCustomer({...f,userId,sandboxKey:'rk_test_example'}),'cus_existing');
});
