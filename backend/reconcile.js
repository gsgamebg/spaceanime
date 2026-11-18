'use strict';
const { eventRequiresReconciliation, computeSandboxEntitlement } = require('./entitlements');
const LIVE_EVENT_TYPES = new Set(['checkout.session.completed','checkout.session.async_payment_succeeded','checkout.session.async_payment_failed']);
const SUBSCRIPTION_EVENTS = new Set(['customer.subscription.created','customer.subscription.updated','customer.subscription.deleted']);
const INVOICE_EVENTS = new Set(['invoice.paid','invoice.payment_failed']);
const RISK_EVENTS = new Set(['charge.refunded','charge.dispute.created','radar.early_fraud_warning.created']);
function validId(id, prefix) { return typeof id === 'string' && new RegExp(`^${prefix}_[A-Za-z0-9]{1,120}$`).test(id); }
function customerId(resource) {
 const value = typeof resource.customer === 'string' ? resource.customer : resource.customer?.id;
 if (resource.livemode !== false || !validId(value, 'cus')) throw new Error('invalid_sandbox_resource');
 return value;
}
async function reconcileSandboxEvent(event, { stripe, store }) {
 if (!eventRequiresReconciliation(event)) return { status:'ignored' };
 if (RISK_EVENTS.has(event.type)) return { status:'risk_review_required' };
 const objectId = event.data?.object?.id;
 let resource;
 if (LIVE_EVENT_TYPES.has(event.type)) {
  if (!validId(objectId, 'cs_test')) throw new Error('invalid_session_id');
  resource = await stripe.checkout.sessions.retrieve(objectId);
 } else if (SUBSCRIPTION_EVENTS.has(event.type)) {
  if (!validId(objectId, 'sub')) throw new Error('invalid_subscription_id');
  resource = await stripe.subscriptions.retrieve(objectId);
 } else if (INVOICE_EVENTS.has(event.type)) {
  if (!validId(objectId, 'in')) throw new Error('invalid_invoice_id');
  resource = await stripe.invoices.retrieve(objectId);
 } else throw new Error('unsupported_event');
 const customer = customerId(resource);
 // A associação user/customer é cadastrada pelo servidor, nunca pelo conteúdo do webhook.
 const owner = await store.findOwner(customer);
 if (!owner || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(owner)) throw new Error('unknown_customer');
 // A geração deve ser persistida ANTES da consulta externa. Commits antigos são rejeitados pelo banco.
 const generation = await store.beginReconciliation({eventId:event.id, customer, userId:owner});
 if (!Number.isSafeInteger(generation) || generation < 1) throw new Error('invalid_generation');
 const current = await stripe.subscriptions.list({customer, status:'all', limit:100});
 const riskReviewed = await store.hasNoOpenRisks(customer);
 const entitlement = computeSandboxEntitlement({customerId:customer,subscriptions:current.data,hasMore:current.has_more,riskReviewed});
 const committed = await store.commitReconciliation({eventId:event.id,customer,userId:owner,generation,entitlement});
 if (committed !== true) return {status:'superseded'};
 return {status:'reconciled',active:entitlement.active,tier:entitlement.tier};
}
module.exports = { reconcileSandboxEvent };
