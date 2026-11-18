'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { eventRequiresReconciliation, computeSandboxEntitlement } = require('./entitlements');
function subscription(status='active', lookup='platina_mensal') {
 return { id:'sub_123', livemode:false, customer:'cus_123', status, items:{has_more:false,data:[{price:{lookup_key:lookup,livemode:false,currency:'brl',unit_amount:1689,recurring:{interval:'month',interval_count:1}}}]} };
}
const base={customerId:'cus_123',subscriptions:[],hasMore:false,riskReviewed:true};
test('signed sandbox lifecycle and risk events require reconciliation',()=>{
 assert.equal(eventRequiresReconciliation({id:'evt_123',type:'invoice.payment_failed',livemode:false}),true);
 assert.equal(eventRequiresReconciliation({id:'evt_123',type:'unrelated.event',livemode:false}),false);
 assert.throws(()=>eventRequiresReconciliation({id:'evt_123',type:'invoice.paid',livemode:true}),/invalid_sandbox_event/);
});
test('only an active verified subscription receives entitlement',()=>{
 assert.deepEqual(computeSandboxEntitlement({...base,subscriptions:[subscription()]}),{active:true,tier:'platina',subscriptionId:'sub_123'});
 for(const status of ['canceled','past_due','incomplete','unpaid','trialing']) assert.equal(computeSandboxEntitlement({...base,subscriptions:[subscription(status)]}).active,false);
});
test('missing pages, risk review, or wrong ownership fail closed',()=>{
 assert.throws(()=>computeSandboxEntitlement({...base,hasMore:true}),/reconciliation_incomplete/);
 assert.throws(()=>computeSandboxEntitlement({...base,riskReviewed:false}),/reconciliation_incomplete/);
 assert.throws(()=>computeSandboxEntitlement({...base,subscriptions:[{...subscription(),customer:'cus_other'}]}),/invalid_subscription/);
});
test('modified price or incomplete item pages never grant access',()=>{
 const sub=subscription(); sub.items.data[0].price.unit_amount=1;
 assert.throws(()=>computeSandboxEntitlement({...base,subscriptions:[sub]}),/unrecognized_subscription_price/);
 const incomplete=subscription(); incomplete.items.has_more=true;
 assert.throws(()=>computeSandboxEntitlement({...base,subscriptions:[incomplete]}),/subscription_items_incomplete/);
});
