'use strict';
const test=require('node:test'); const assert=require('node:assert/strict');
const {createSandboxStore}=require('./sandbox-store');
const uid='11111111-1111-4111-8111-111111111111';
const options={supabaseUrl:'https://example.supabase.co',secretKey:'sb_secret_synthetic'};
test('store only talks to trusted Supabase URL and fails closed on risk',async()=>{
 const calls=[];
 const store=createSandboxStore({...options,fetchImpl:async(url,opts)=>{calls.push({url:String(url),opts});return {ok:true,json:async()=>uid}}});
 assert.equal(await store.findOwner('cus_123'),uid); assert.equal(await store.hasNoOpenRisks('cus_123'),false);
 assert.equal(calls[0].url,'https://example.supabase.co/rest/v1/rpc/find_stripe_sandbox_customer_owner');
 assert.equal(calls[0].opts.redirect,'error');
 assert.throws(()=>createSandboxStore({...options,supabaseUrl:'https://example.supabase.co.evil.test'}),/invalid_supabase_config/);
});
test('store rejects failed or malformed database responses',async()=>{
 const offline=createSandboxStore({...options,fetchImpl:async()=>({ok:false})});
 await assert.rejects(offline.findOwner('cus_123'),/sandbox_store_unavailable/);
 const malformed=createSandboxStore({...options,fetchImpl:async()=>({ok:true,json:async()=>({})})});
 await assert.rejects(malformed.beginReconciliation({}),/sandbox_store_invalid/);
});
