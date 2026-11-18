'use strict';
const { SANDBOX_PLANS } = require('./billing');
const PLAN_RANK = Object.freeze({ basic: 1, platina: 2, premium: 3 });
const RECONCILE_TYPES = new Set([
  'checkout.session.completed', 'checkout.session.async_payment_succeeded', 'checkout.session.async_payment_failed',
  'customer.subscription.created', 'customer.subscription.updated', 'customer.subscription.deleted',
  'invoice.paid', 'invoice.payment_failed', 'charge.refunded', 'charge.dispute.created', 'radar.early_fraud_warning.created',
]);
function eventRequiresReconciliation(event) {
  if (!event || event.livemode !== false || !/^evt_[A-Za-z0-9]{1,120}$/.test(event.id || '')) throw new Error('invalid_sandbox_event');
  return RECONCILE_TYPES.has(event.type);
}
function computeSandboxEntitlement({ customerId, subscriptions, hasMore, riskReviewed }) {
  if (!/^cus_[A-Za-z0-9]+$/.test(customerId || '') || !Array.isArray(subscriptions) || hasMore !== false || riskReviewed !== true) throw new Error('reconciliation_incomplete');
  let highest = null;
  for (const subscription of subscriptions) {
    if (subscription.livemode !== false || subscription.customer !== customerId || !/^sub_[A-Za-z0-9]+$/.test(subscription.id || '')) throw new Error('invalid_subscription');
    if (subscription.status !== 'active') continue;
    const items = subscription.items;
    if (!items || items.has_more !== false || !Array.isArray(items.data) || items.data.length !== 1) throw new Error('subscription_items_incomplete');
    const price = items.data[0]?.price;
    const lookup = price?.lookup_key;
    const plan = Object.hasOwn(SANDBOX_PLANS, lookup) ? SANDBOX_PLANS[lookup] : null;
    if (!plan || price.livemode !== false || price.currency !== 'brl' || price.unit_amount !== plan.cents ||
        price.recurring?.interval !== plan.interval || price.recurring?.interval_count !== 1) throw new Error('unrecognized_subscription_price');
    const tier = lookup.split('_')[0];
    if (!highest || PLAN_RANK[tier] > PLAN_RANK[highest.tier]) highest = { tier, subscriptionId: subscription.id };
  }
  return highest ? { active: true, tier: highest.tier, subscriptionId: highest.subscriptionId } : { active: false, tier: null, subscriptionId: null };
}
module.exports = { eventRequiresReconciliation, computeSandboxEntitlement };
