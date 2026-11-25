'use strict';
const crypto = require('node:crypto');

// Prices live only here and in the database; the app sends a plan id, never an amount.
const VIP_PLANS = Object.freeze({
  ouro: Object.freeze({ name: 'Ouro', cents: 1000 }),
  platina: Object.freeze({ name: 'Platina', cents: 2000 }),
  ametista: Object.freeze({ name: 'Ametista', cents: 3000 }),
});
const MP_API = 'https://api.mercadopago.com';
const ORIGIN = 'https://spaceanime.onrender.com';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PIX_MINUTES = 30;

function validSupabase(env) {
  try {
    const u = new URL(env.SUPABASE_URL);
    return u.protocol === 'https:' && u.hostname.endsWith('.supabase.co') && u.pathname === '/' && !u.search &&
      /^sb_secret_[A-Za-z0-9_-]+$/.test(env.SUPABASE_SECRET_KEY || '');
  } catch { return false; }
}
function mpConfigured(env) {
  return /^(APP_USR|TEST)-[A-Za-z0-9_-]{10,}$/.test(env.MP_ACCESS_TOKEN || '');
}
function safeTicketUrl(raw) {
  try {
    const u = new URL(raw);
    return u.protocol === 'https:' && !u.username && !u.password && !u.port &&
      (u.hostname === 'mercadopago.com.br' || u.hostname.endsWith('.mercadopago.com.br') ||
       u.hostname === 'mercadopago.com' || u.hostname.endsWith('.mercadopago.com')) ? u.href : null;
  } catch { return null; }
}

/** Thin Mercado Pago client: creates Pix payments and reads them back. The token never leaves the server. */
function createMercadoPago(env = process.env, fetchImpl = fetch) {
  async function call(path, init) {
    const response = await fetchImpl(new URL(path, MP_API), {
      ...init,
      headers: { Authorization: `Bearer ${env.MP_ACCESS_TOKEN}`, 'Content-Type': 'application/json', ...(init.headers || {}) },
      signal: AbortSignal.timeout(15000), redirect: 'error',
    });
    if (!response.ok) {
      // Mercado Pago explains rejections in `message`/`cause`; log them (never the token).
      const detail = await response.text().catch(() => '');
      console.error(`[vip] Mercado Pago ${init.method} ${path} -> HTTP ${response.status}: ${detail.slice(0, 500)}`);
      throw new Error(`mercadopago_${response.status}`);
    }
    return response.json();
  }
  return {
    async createPix({ orderId, plan, email }) {
      const expires = new Date(Date.now() + PIX_MINUTES * 60_000).toISOString().replace('Z', '-00:00');
      const payment = await call('/v1/payments', {
        method: 'POST',
        headers: { 'X-Idempotency-Key': orderId },
        body: JSON.stringify({
          transaction_amount: VIP_PLANS[plan].cents / 100,
          description: `SpaceAnime VIP ${VIP_PLANS[plan].name} - 30 dias`,
          payment_method_id: 'pix',
          external_reference: orderId,
          notification_url: `${ORIGIN}/api/vip/webhook`,
          date_of_expiration: expires,
          payer: { email },
        }),
      });
      const data = payment?.point_of_interaction?.transaction_data || {};
      if (!Number.isSafeInteger(payment?.id) || typeof data.qr_code !== 'string' || data.qr_code.length > 1024 ||
          typeof data.qr_code_base64 !== 'string' || data.qr_code_base64.length > 200_000 || !/^[A-Za-z0-9+/=\s]+$/.test(data.qr_code_base64)) {
        throw new Error('mercadopago_invalid_pix');
      }
      return {
        paymentId: payment.id,
        qrCode: data.qr_code,
        qrCodeBase64: data.qr_code_base64.replace(/\s/g, ''),
        ticketUrl: safeTicketUrl(data.ticket_url),
        expiresAt: payment.date_of_expiration || expires,
      };
    },
    async getPayment(id) {
      if (!/^[0-9]{1,20}$/.test(String(id))) throw new Error('invalid_payment_id');
      return call(`/v1/payments/${id}`, { method: 'GET' });
    },
  };
}

/** Supabase RPCs (service role). All VIP state is written by these functions only. */
function createVipStore(env = process.env, fetchImpl = fetch) {
  async function rpc(name, args) {
    if (!validSupabase(env)) { console.error('[vip] SUPABASE_URL/SUPABASE_SECRET_KEY missing or invalid'); throw new Error('vip_store_unavailable'); }
    const response = await fetchImpl(new URL(`/rest/v1/rpc/${name}`, env.SUPABASE_URL), {
      method: 'POST',
      headers: { apikey: env.SUPABASE_SECRET_KEY, Authorization: `Bearer ${env.SUPABASE_SECRET_KEY}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(args), signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    const text = await response.text().catch(() => '');
    if (!response.ok) {
      if (text.includes('vip_too_many_orders')) throw new Error('vip_too_many_orders');
      console.error(`[vip] Supabase rpc ${name} -> HTTP ${response.status}: ${text.slice(0, 500)}`);
      throw new Error('vip_store_unavailable');
    }
    // Functions returning void answer 204 with an empty body: that is success, not an error.
    if (!text.trim()) return null;
    try { return JSON.parse(text); }
    catch { console.error(`[vip] Supabase rpc ${name} returned invalid JSON`); throw new Error('vip_store_unavailable'); }
  }
  return {
    createOrder: (userId, plan) => rpc('spaceanime_vip_create_order', { p_user: userId, p_plan: plan }),
    attachPayment: (orderId, paymentId) => rpc('spaceanime_vip_attach_payment', { p_order: orderId, p_payment_id: paymentId }),
    orderStatus: (orderId, userId) => rpc('spaceanime_vip_order_status', { p_order: orderId, p_user: userId }),
    confirmPayment: (orderId, paymentId, amountCents) => rpc('spaceanime_vip_confirm_payment', { p_order: orderId, p_payment_id: paymentId, p_amount_cents: amountCents }),
    status: (userId) => rpc('spaceanime_vip_status', { p_user: userId }),
  };
}

/**
 * Mercado Pago webhook signature (x-signature: ts=…,v1=…). The manifest uses the payment id from
 * the query string, the x-request-id header and the timestamp. Rejects anything older than 10 min.
 */
function verifyWebhookSignature({ secret, header, requestId, dataId, now = Date.now() }) {
  if (typeof header !== 'string' || typeof requestId !== 'string' || typeof dataId !== 'string') return false;
  const parts = Object.fromEntries(header.split(',').map(p => p.trim().split('=')).filter(p => p.length === 2));
  if (!/^[0-9]{10,13}$/.test(parts.ts || '') || !/^[0-9a-f]{64}$/i.test(parts.v1 || '')) return false;
  const ts = Number(parts.ts);
  const tsMs = parts.ts.length > 10 ? ts : ts * 1000;
  if (Math.abs(now - tsMs) > 10 * 60_000) return false;
  const manifest = `id:${dataId.toLowerCase()};request-id:${requestId};ts:${parts.ts};`;
  const expected = crypto.createHmac('sha256', secret).update(manifest).digest();
  const supplied = Buffer.from(parts.v1, 'hex');
  return supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);
}

/**
 * Reads the payment from Mercado Pago (the only source of truth) and activates VIP when it is an
 * approved Pix in BRL for exactly the order's plan price. Safe to call repeatedly.
 */
async function settlePayment({ mp, store, paymentId }) {
  const payment = await mp.getPayment(paymentId);
  const orderId = payment?.external_reference;
  if (!UUID.test(orderId || '')) return { status: 'ignored' };
  if (payment.status !== 'approved') return { status: payment.status === 'cancelled' || payment.status === 'rejected' ? 'failed' : 'pending', orderId };
  if (payment.currency_id !== 'BRL' || payment.payment_method_id !== 'pix') return { status: 'ignored', orderId };
  const cents = Math.round(Number(payment.transaction_amount) * 100);
  if (!Number.isSafeInteger(cents) || cents <= 0) return { status: 'ignored', orderId };
  const result = await store.confirmPayment(orderId, Number(payment.id), cents);
  return { status: result?.status || 'pending', orderId };
}

module.exports = { VIP_PLANS, UUID, mpConfigured, safeTicketUrl, createMercadoPago, createVipStore, verifyWebhookSignature, settlePayment };
