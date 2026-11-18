'use strict';
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CUSTOMER=/^cus_[A-Za-z0-9]+$/;
async function getOrCreateSandboxCustomer({stripe,store,userId,sandboxKey}) {
 if (!UUID.test(userId || '') || !/^(rk|sk)_test_/.test(sandboxKey || '')) throw new Error('invalid_sandbox_context');
 const existing=await store.findCustomer(userId);
 let chosen=existing;
 if (!chosen) {
  const created=await stripe.customers.create({}, {idempotencyKey:`spaceanime-customer:${userId}`});
  if (created.livemode !== false || created.deleted === true || !CUSTOMER.test(created.id || '')) throw new Error('invalid_sandbox_customer');
  chosen=await store.bindCustomer(userId,created.id);
 }
 if (!CUSTOMER.test(chosen || '')) throw new Error('invalid_sandbox_customer');
 const verified=await stripe.customers.retrieve(chosen);
 if (verified.livemode !== false || verified.deleted === true || verified.id !== chosen) throw new Error('invalid_sandbox_customer');
 return chosen;
}
module.exports={getOrCreateSandboxCustomer};
