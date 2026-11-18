'use strict';
function createSandboxStore({ supabaseUrl, secretKey, fetchImpl = fetch }) {
 let base;
 try { base = new URL(supabaseUrl); } catch { throw new Error('invalid_supabase_config'); }
 if (base.protocol !== 'https:' || !base.hostname.endsWith('.supabase.co') || base.username || base.password || base.port || base.pathname !== '/' || base.search || base.hash || !/^sb_secret_[A-Za-z0-9_-]+$/.test(secretKey || '')) throw new Error('invalid_supabase_config');
 async function rpc(name, args) {
  const response = await fetchImpl(new URL(`/rest/v1/rpc/${name}`, base), {
   method:'POST', headers:{apikey:secretKey,'Content-Type':'application/json'}, body:JSON.stringify(args), signal:AbortSignal.timeout(8000), redirect:'error',
  });
  if (!response.ok) throw new Error('sandbox_store_unavailable');
  return response.json();
 }
 return {
  async findCustomer(userId) { const value=await rpc('find_stripe_sandbox_customer',{p_user_id:userId}); if (value !== null && (typeof value !== 'string' || !/^cus_[A-Za-z0-9]+$/.test(value))) throw new Error('sandbox_store_invalid'); return value; },
  async bindCustomer(userId,customer) { const value=await rpc('bind_stripe_sandbox_customer',{p_user_id:userId,p_customer_id:customer}); if (typeof value !== 'string' || !/^cus_[A-Za-z0-9]+$/.test(value)) throw new Error('sandbox_store_invalid'); return value; },
  async findOwner(customer) { const value=await rpc('find_stripe_sandbox_customer_owner',{p_customer_id:customer}); if (value !== null && (typeof value !== 'string' || !/^[0-9a-f-]{36}$/i.test(value))) throw new Error('sandbox_store_invalid'); return value; },
  async beginReconciliation({eventId,customer,userId}) { const value=await rpc('begin_stripe_sandbox_reconciliation',{p_event_id:eventId,p_customer_id:customer,p_user_id:userId}); if (!Number.isSafeInteger(value) || value<1) throw new Error('sandbox_store_invalid'); return value; },
  async commitReconciliation({eventId,customer,userId,generation,entitlement}) {
   const value=await rpc('commit_stripe_sandbox_reconciliation',{p_event_id:eventId,p_customer_id:customer,p_user_id:userId,p_generation:generation,p_active:entitlement.active,p_tier:entitlement.tier,p_subscription_id:entitlement.subscriptionId});
   if (typeof value !== 'boolean') throw new Error('sandbox_store_invalid'); return value;
  },
  // Fail closed until a trusted risk-review workflow exists.
  async hasNoOpenRisks() { return false; },
 };
}
module.exports={createSandboxStore};
