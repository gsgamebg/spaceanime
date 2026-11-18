'use strict';
const { createHash } = require('node:crypto');

// Catálogo imutável do sandbox; valores em centavos. O cliente nunca envia o preço.
const SANDBOX_PLANS = Object.freeze({
  basic_mensal: Object.freeze({ cents: 1299, interval: 'month' }),
  basic_anual: Object.freeze({ cents: 12800, interval: 'year' }),
  platina_mensal: Object.freeze({ cents: 1689, interval: 'month' }),
  platina_anual: Object.freeze({ cents: 14200, interval: 'year' }),
  premium_mensal: Object.freeze({ cents: 2499, interval: 'month' }),
  premium_anual: Object.freeze({ cents: 23400, interval: 'year' }),
});
function safeReturnUrl(raw) {
  try {
    const url = new URL(raw);
    return url.protocol === 'https:' && url.hostname === 'spaceanime.onrender.com' && !url.username && !url.password && !url.port && !url.hash;
  } catch { return false; }
}
function randomLabel(userId, requestId) {
  const bytes = createHash('sha256').update(`${userId}:${requestId}`).digest();
  let suffix = '';
  for (let i = 0; i < 8; i++) suffix += 'abcdefghijklmnopqrstuvwxyz'[bytes[i] % 26];
  return `spaceanime-sandbox-${suffix}`;
}
async function createSandboxCheckout({ stripe, lookupKey, userId, customerId, requestId, successUrl, cancelUrl }) {
  const plan = SANDBOX_PLANS[lookupKey];
  if (!plan || !/^cus_[A-Za-z0-9]+$/.test(customerId || '') || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(userId || '') ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(requestId || '') ||
      !safeReturnUrl(successUrl) || !safeReturnUrl(cancelUrl)) throw new Error('invalid_checkout_request');
  const prices = await stripe.prices.list({ lookup_keys: [lookupKey], active: true, limit: 2 });
  if (!Array.isArray(prices.data) || prices.data.length !== 1) throw new Error('sandbox_price_unavailable');
  const price = prices.data[0];
  if (price.livemode !== false || price.active !== true || price.currency !== 'brl' || price.unit_amount !== plan.cents ||
      price.recurring?.interval !== plan.interval || price.recurring?.interval_count !== 1 || !/^price_[A-Za-z0-9]+$/.test(price.id || '')) {
    throw new Error('sandbox_price_mismatch');
  }
  const session = await stripe.checkout.sessions.create({
    mode: 'subscription', customer: customerId, line_items: [{ price: price.id, quantity: 1 }],
    client_reference_id: userId,
    success_url: successUrl, cancel_url: cancelUrl,
    integration_identifier: randomLabel(userId, requestId),
  }, { idempotencyKey: `checkout:${userId}:${requestId}` });
  if (session.livemode !== false || session.customer !== customerId || !/^cs_test_[A-Za-z0-9]+$/.test(session.id || '')) throw new Error('unsafe_checkout_response');
  const url = new URL(session.url);
  if (url.protocol !== 'https:' || url.hostname !== 'checkout.stripe.com' || url.username || url.password || url.port) throw new Error('unsafe_checkout_url');
  return { id: session.id, url: url.toString() };
}
module.exports = { SANDBOX_PLANS, safeReturnUrl, createSandboxCheckout };
