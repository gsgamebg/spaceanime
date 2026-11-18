'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {reconcileSandboxEvent}=require('./reconcile');
const uid='11111111-1111-4111-8111-111111111111';
const event={id:'evt_123',type:'invoice.paid',livemode:false,data:{object:{id:'in_123'}}};
function fixtures() {
 const calls=[];
 const stripe={ invoices:{retrieve:async id=>({id,customer:'cus_123',livemode:false})},subscriptions:{list:async()=>({data:[],has_more:false})} };
 const store={findOwner:async()=>uid,beginReconciliation:async()=>{calls.push('begin');return 2},hasNoOpenRisks:async()=>true,commitReconciliation:async record=>{calls.push(record);return true}};
 return {stripe,store,calls};
}
test('reconciles current subscription state instead of trusting event payload',async()=>{
 const {stripe,store,calls}=fixtures();
 const result=await reconcileSandboxEvent(event,{stripe,store});
 assert.equal(result.status,'reconciled');assert.equal(result.active,false);assert.equal(calls[0],'begin');assert.equal(calls[1].userId,uid);
});
test('unknown customer cannot receive access',async()=>{
 const f=fixtures();f.store.findOwner=async()=>null;
 await assert.rejects(reconcileSandboxEvent(event,f),/unknown_customer/);assert.equal(f.calls.length,0);
});
test('older or concurrent result cannot overwrite newer state',async()=>{
 const f=fixtures();f.store.commitReconciliation=async()=>false;
 assert.deepEqual(await reconcileSandboxEvent(event,f),{status:'superseded'});
});
test('incomplete subscription list or unresolved risk fails closed',async()=>{
 let f=fixtures();f.stripe.subscriptions.list=async()=>({data:[],has_more:true});
 await assert.rejects(reconcileSandboxEvent(event,f),/reconciliation_incomplete/);
 f=fixtures();f.store.hasNoOpenRisks=async()=>false;
 await assert.rejects(reconcileSandboxEvent(event,f),/reconciliation_incomplete/);
});
test('refunds and disputes are not silently acknowledged',async()=>{
 const f=fixtures();const e={...event,type:'charge.dispute.created'};
 assert.deepEqual(await reconcileSandboxEvent(e,f),{status:'risk_review_required'});assert.equal(f.calls.length,0);
});
test('real-mode or mismatched Stripe resources are rejected',async()=>{
 const f=fixtures();await assert.rejects(reconcileSandboxEvent({...event,livemode:true},f),/invalid_sandbox_event/);
 f.stripe.invoices.retrieve=async()=>({customer:'cus_123',livemode:true});
 await assert.rejects(reconcileSandboxEvent(event,f),/invalid_sandbox_resource/);
});
