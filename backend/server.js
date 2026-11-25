'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const Stripe = require('stripe');
const { loadRelease, manifest } = require('./update-feed');
const { createNotificationStore } = require('./notifications-store');
const { createFirebaseSender } = require('./notifications-fcm');
const { runDailyDigest } = require('./notifications-worker');
const { createRuntimeConfigStore } = require('./runtime-config-store');
const { parseTelemetry, clientIp, createTelemetryStore } = require('./telemetry');
const { VIP_PLANS, UUID, mpConfigured, createMercadoPago, createVipStore, verifyWebhookSignature, settlePayment } = require('./vip');

const MAX_BODY = 65536;
function json(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(data));
}
function urlValid(value, supabase = false) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password && !u.hash && (!u.port || u.port === '443') && (!supabase || (u.hostname.endsWith('.supabase.co') && u.pathname === '/' && !u.search));
  } catch { return false; }
}
async function readBody(req) {
  const chunks = []; let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > MAX_BODY) throw new Error('body_too_large');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
function createHandler({
  env = process.env,
  stripeFactory = (key) => new Stripe(key),
  verifyUser,
  authorizeFinancial,
  persistSandboxEvent,
  notificationStore: notificationStoreOverride,
  runtimeConfigStore: runtimeConfigStoreOverride,
  telemetryStore: telemetryStoreOverride,
  runDailyDigest: runDailyDigestOverride,
  firebaseSender: firebaseSenderOverride,
  vipStore: vipStoreOverride,
  mercadoPago: mercadoPagoOverride,
  verifyAccount,
} = {}) {
  const vipStore = vipStoreOverride || createVipStore(env);
  const mercadoPago = mercadoPagoOverride || createMercadoPago(env);
  const notificationStore = notificationStoreOverride || createNotificationStore(env);
  const runtimeConfigStore = runtimeConfigStoreOverride || createRuntimeConfigStore(env);
  const telemetryStore = telemetryStoreOverride || createTelemetryStore(env);
  const firebaseSender = firebaseSenderOverride || createFirebaseSender({ env });
  const dailyDigestWorker = runDailyDigestOverride || (options =>
    runDailyDigest({ ...options, store: notificationStore, sender: firebaseSender }));
  const authenticate = verifyUser || (async (token) => {
    if (!urlValid(env.SUPABASE_URL, true) || !env.SUPABASE_PUBLISHABLE_KEY) return null;
    const res = await fetch(new URL('/auth/v1/user', env.SUPABASE_URL), {
      headers: { apikey: env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!res.ok) return null;
    const user = await res.json();
    return typeof user.id === 'string' && /^[0-9a-f-]{36}$/i.test(user.id) ? user.id : null;
  });
  // Same check as [authenticate], but also returns the e-mail Mercado Pago needs as the payer.
  const account = verifyAccount || (async (token) => {
    if (!urlValid(env.SUPABASE_URL, true) || !env.SUPABASE_PUBLISHABLE_KEY) return null;
    const res = await fetch(new URL('/auth/v1/user', env.SUPABASE_URL), {
      headers: { apikey: env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!res.ok) return null;
    const user = await res.json();
    if (typeof user.id !== 'string' || !UUID.test(user.id)) return null;
    return { id: user.id, email: typeof user.email === 'string' && user.email.length <= 254 ? user.email : null };
  });
  async function authenticatedAccount(req, res) {
    const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
    if (!match) { json(res, 401, { error: 'unauthorized' }); return null; }
    let user;
    try { user = await account(match[1]); }
    catch { json(res, 503, { error: 'auth_unavailable' }); return null; }
    if (!user) { json(res, 401, { error: 'unauthorized' }); return null; }
    return user;
  }
  async function vipStatus(userId) {
    const value = await vipStore.status(userId);
    const active = value?.active === true && Object.hasOwn(VIP_PLANS, value.plan) && typeof value.expires_at === 'string';
    return active ? { active: true, plan: value.plan, expires_at: value.expires_at } : { active: false, plan: null, expires_at: null };
  }
  const persistEvent = persistSandboxEvent || (async (event) => {
    if (!urlValid(env.SUPABASE_URL, true) || !/^sb_secret_[A-Za-z0-9_-]+$/.test(env.SUPABASE_SECRET_KEY || '')) throw new Error('event_store_unavailable');
    const response = await fetch(new URL('/rest/v1/rpc/record_stripe_sandbox_event', env.SUPABASE_URL), {
      method: 'POST', headers: { apikey: env.SUPABASE_SECRET_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_event_id: event.id, p_event_type: event.type }),
      signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!response.ok) throw new Error('event_store_unavailable');
    const inserted = await response.json();
    if (typeof inserted !== 'boolean') throw new Error('event_store_invalid');
    return inserted;
  });
  const canReadFinancial = authorizeFinancial || (async (token) => {
    if (!urlValid(env.SUPABASE_URL, true) || !env.SUPABASE_PUBLISHABLE_KEY) return false;
    const res = await fetch(new URL('/rest/v1/rpc/authorize_financial_read', env.SUPABASE_URL), {
      method: 'POST',
      headers: { apikey: env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: '{}', signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!res.ok) throw new Error('authorization_unavailable');
    return (await res.json()) === true;
  });
  async function authenticatedNotificationUser(req, res) {
    const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
    if (!match) { json(res, 401, { error: 'unauthorized' }); return null; }
    let userId;
    try { userId = await authenticate(match[1]); }
    catch { json(res, 503, { error: 'auth_unavailable' }); return null; }
    if (!userId) { json(res, 401, { error: 'unauthorized' }); return null; }
    return userId;
  }
  function readJsonBody(req, res) {
    if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) {
      json(res, 415, { error: 'json_required' });
      return null;
    }
    return readBody(req).then(bytes => {
      try {
        const value = JSON.parse(bytes.toString('utf8'));
        if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid_json');
        return value;
      } catch {
        json(res, 400, { error: 'invalid_json' });
        return null;
      }
    });
  }
  function validDeviceId(value) {
    return typeof value === 'string' && /^[A-Za-z0-9._:-]{1,128}$/.test(value);
  }
  function validPreferences(value) {
    return value && typeof value === 'object' && !Array.isArray(value) &&
      Object.keys(value).sort().join(',') === 'anime,announcements,manga' &&
      ['anime', 'manga', 'announcements'].every(key => typeof value[key] === 'boolean');
  }
  function validTimeZone(value) {
    if (typeof value !== 'string' || value.length > 100) return false;
    try { new Intl.DateTimeFormat('en-US', { timeZone: value }); return true; }
    catch { return false; }
  }
  function validDigestTime(value) {
    return typeof value === 'string' && /^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/.test(value);
  }
  function hasConsent(preferences) {
    return preferences.anime || preferences.manga || preferences.announcements;
  }
  function normalizeSubscriptions(value) {
    if (!Array.isArray(value) || value.length > 500) return null;
    const subscriptions = new Map();
    for (const item of value) {
      if (!item || typeof item !== 'object' || Array.isArray(item) ||
          Object.keys(item).sort().join(',') !== 'audio_variant,content_id,media_type,source_id') {
        return null;
      }
      const { media_type, source_id, content_id, audio_variant } = item;
      if (typeof content_id !== 'string' || !/^[A-Za-z0-9._:/-]{1,180}$/.test(content_id) ||
          (media_type === 'anime' && !['anitube', 'animesdigital'].includes(source_id)) ||
          (media_type === 'manga' && !['dex', 'yomu'].includes(source_id)) ||
          !['dubbed', 'subtitled', 'none'].includes(audio_variant) ||
          (media_type === 'manga' && audio_variant !== 'none') ||
          !['anime', 'manga'].includes(media_type)) {
        return null;
      }
      const normalized = { media_type, source_id, content_id, audio_variant };
      subscriptions.set([media_type, source_id, content_id, audio_variant].join('\0'), normalized);
    }
    return [...subscriptions.values()];
  }
  return async (req, res) => {
    try {
      const path = new URL(req.url, 'http://local').pathname;
      if (req.method === 'GET' && path === '/health') return json(res, 200, { status: 'ok', payments: 'sandbox_only', entitlements: 'disabled' });
      if (req.method === 'GET' && path === '/api/app-config') {
        try {
          return json(res, 200, await runtimeConfigStore.read());
        } catch {
          return json(res, 503, { error: 'app_config_unavailable' });
        }
      }
      if (req.method === 'GET' && path === '/api/v2/app-config') {
        try {
          return json(res, 200, await runtimeConfigStore.readV2());
        } catch {
          return json(res, 503, { error: 'app_config_unavailable' });
        }
      }
      if (req.method === 'POST' && path === '/api/vip/create-pix') {
        if (!mpConfigured(env)) return json(res, 503, { error: 'payments_not_configured' });
        const user = await authenticatedAccount(req, res);
        if (!user) return;
        const body = await readJsonBody(req, res);
        if (!body) return;
        if (Object.keys(body).join(',') !== 'plan' || !Object.hasOwn(VIP_PLANS, body.plan)) return json(res, 400, { error: 'invalid_plan' });
        if (!user.email) return json(res, 400, { error: 'email_required' });
        console.log(`[vip] create-pix plan=${body.plan}`);
        let orderId;
        try { orderId = await vipStore.createOrder(user.id, body.plan); }
        catch (error) {
          if (error?.message === 'vip_too_many_orders') return json(res, 429, { error: 'too_many_orders' });
          console.error(`[vip] create-pix: order not created (${error?.message})`);
          return json(res, 503, { error: 'vip_store_unavailable' });
        }
        if (!UUID.test(orderId || '')) { console.error('[vip] create-pix: order id invalid'); return json(res, 503, { error: 'vip_store_unavailable' }); }
        let pix;
        try { pix = await mercadoPago.createPix({ orderId, plan: body.plan, email: user.email }); }
        catch (error) { console.error(`[vip] create-pix order=${orderId}: Mercado Pago failed (${error?.message})`); return json(res, 502, { error: 'payment_provider_unavailable' }); }
        try { await vipStore.attachPayment(orderId, pix.paymentId); }
        catch (error) { console.error(`[vip] create-pix order=${orderId}: payment not attached (${error?.message})`); return json(res, 503, { error: 'vip_store_unavailable' }); }
        console.log(`[vip] create-pix order=${orderId} payment=${pix.paymentId} ok`);
        return json(res, 201, {
          order_id: orderId, plan: body.plan, amount_cents: VIP_PLANS[body.plan].cents,
          qr_code: pix.qrCode, qr_code_base64: pix.qrCodeBase64, ticket_url: pix.ticketUrl, expires_at: pix.expiresAt,
        });
      }
      const vipOrderMatch = /^\/api\/vip\/orders\/([0-9a-fA-F-]{36})$/.exec(path);
      if (req.method === 'GET' && vipOrderMatch) {
        const user = await authenticatedAccount(req, res);
        if (!user) return;
        if (!UUID.test(vipOrderMatch[1])) return json(res, 400, { error: 'invalid_order' });
        try {
          let order = await vipStore.orderStatus(vipOrderMatch[1], user.id);
          if (!order || typeof order.status !== 'string') return json(res, 404, { error: 'order_not_found' });
          // Webhooks can be late: while the buyer waits, ask Mercado Pago directly.
          if (order.status === 'pending' && order.payment_id && mpConfigured(env)) {
            try { await settlePayment({ mp: mercadoPago, store: vipStore, paymentId: order.payment_id }); } catch {}
            order = await vipStore.orderStatus(vipOrderMatch[1], user.id);
          }
          return json(res, 200, { status: order.status, vip: await vipStatus(user.id) });
        } catch { return json(res, 503, { error: 'vip_store_unavailable' }); }
      }
      if (req.method === 'GET' && path === '/api/vip/me') {
        const user = await authenticatedAccount(req, res);
        if (!user) return;
        try { return json(res, 200, await vipStatus(user.id)); }
        catch { return json(res, 503, { error: 'vip_store_unavailable' }); }
      }
      if (req.method === 'POST' && path === '/api/vip/webhook') {
        if (!mpConfigured(env)) return json(res, 503, { error: 'payments_not_configured' });
        const requestUrl = new URL(req.url, 'http://local');
        let body = {};
        try { const raw = (await readBody(req)).toString('utf8'); body = raw ? JSON.parse(raw) : {}; } catch { body = {}; }
        const type = requestUrl.searchParams.get('type') || requestUrl.searchParams.get('topic') || body?.type;
        const dataId = String(requestUrl.searchParams.get('data.id') || requestUrl.searchParams.get('id') || body?.data?.id || '');
        if (type !== 'payment') return json(res, 200, { status: 'ignored' });
        if (!/^[0-9]{1,20}$/.test(dataId)) return json(res, 400, { error: 'invalid_payment_id' });
        if (env.MP_WEBHOOK_SECRET && !verifyWebhookSignature({
          secret: env.MP_WEBHOOK_SECRET, header: req.headers['x-signature'], requestId: req.headers['x-request-id'], dataId,
        })) { console.error(`[vip] webhook payment=${dataId}: invalid signature`); return json(res, 401, { error: 'invalid_signature' }); }
        try {
          const result = await settlePayment({ mp: mercadoPago, store: vipStore, paymentId: dataId });
          console.log(`[vip] webhook payment=${dataId} -> ${result.status}`);
          return json(res, 200, { status: result.status });
        } catch (error) { console.error(`[vip] webhook payment=${dataId} failed (${error?.message})`); return json(res, 500, { error: 'payment_processing_failed' }); }
      }
      if (req.method === 'POST' && path === '/api/v1/telemetry') {
        if (!/^application\/json\b/i.test(req.headers['content-type'] || '')) return json(res, 415, { error: 'unsupported_media_type' });
        const event = parseTelemetry((await readBody(req)).toString('utf8'));
        if (!event) return json(res, 400, { error: 'invalid_telemetry' });
        try { await telemetryStore.record(event, clientIp(req)); } catch { return json(res, 503, { error: 'telemetry_unavailable' }); }
        return json(res, 202, { status: 'accepted' });
      }
      if (req.method === 'GET' && path === '/updates.json') {
        const release = loadRelease(env);
        return release ? json(res, 200, manifest(release)) : json(res, 503, { error: 'updates_not_configured' });
      }
      if (req.method === 'GET' && /^\/downloads\/[0-9]{1,10}-(android\.apk|windows\.zip)$/.test(path)) {
        const release = loadRelease(env);
        const match = /^\/downloads\/([0-9]+)-(android|windows)\.(apk|zip)$/.exec(path);
        if (!release || !match || Number(match[1]) !== release.code || (match[2] === 'android' ? match[3] !== 'apk' : match[3] !== 'zip')) return json(res, 404, { error: 'release_not_found' });
        res.writeHead(302, { Location: release.artifacts[match[2]].url, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
        return res.end();
      }
      if (req.method === 'POST' && path === '/webhooks/stripe') {
        const body = await readBody(req);
        const sig = req.headers['stripe-signature'];
        if (!env.STRIPE_WEBHOOK_SECRET || !sig || !/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '')) return json(res, 503, { error: 'webhook_not_configured' });
        const stripe = stripeFactory(env.STRIPE_RESTRICTED_KEY);
        let event;
        try { event = stripe.webhooks.constructEvent(body, sig, env.STRIPE_WEBHOOK_SECRET); }
        catch { return json(res, 400, { error: 'invalid_signature' }); }
        if (event.livemode !== false || !/^evt_[A-Za-z0-9]{1,120}$/.test(event.id || '') || !/^[a-z][a-z0-9_.]{1,100}$/.test(event.type || '')) return json(res, 400, { error: 'invalid_sandbox_event' });
        try { await persistEvent(event); }
        catch { return json(res, 503, { error: 'event_store_unavailable' }); }
        // The event is durably queued, but fulfillment is not implemented; Stripe must retry.
        return json(res, 503, { error: 'event_processing_not_enabled' });
      }
      if (req.method === 'POST' && path === '/api/billing/checkout') {
        if (env.CHECKOUT_SANDBOX_ENABLED !== 'true' || !/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '') || !env.STRIPE_WEBHOOK_SECRET) return json(res, 503, { error: 'sandbox_checkout_disabled' });
        // Prevent accepting money before an actual fulfillment handler is deployed.
        return json(res, 503, { error: 'fulfillment_not_implemented' });
      }
      if (req.method === 'POST' && path === '/api/v1/notifications/devices') {
        const userId = await authenticatedNotificationUser(req, res);
        if (!userId) return;
        const body = await readJsonBody(req, res);
        if (!body) return;
        const allowed = ['device_id', 'digest_time', 'preferences', 'push_token', 'time_zone'];
        if (Object.keys(body).some(key => !allowed.includes(key)) ||
            !validDeviceId(body.device_id) ||
            typeof body.push_token !== 'string' || body.push_token.length < 1 || body.push_token.length > 4096 ||
            /[\u0000-\u0020\u007f]/.test(body.push_token) ||
            !validPreferences(body.preferences) || !hasConsent(body.preferences) ||
            !validTimeZone(body.time_zone) || !validDigestTime(body.digest_time)) {
          return json(res, 400, { error: 'invalid_notification_device' });
        }
        try { await notificationStore.saveDevice(userId, body); }
        catch { return json(res, 503, { error: 'notification_store_unavailable' }); }
        return json(res, 201, { status: 'registered' });
      }
      if (req.method === 'PUT' && path === '/api/v1/notifications/subscriptions') {
        const userId = await authenticatedNotificationUser(req, res);
        if (!userId) return;
        const body = await readJsonBody(req, res);
        if (!body) return;
        if (Object.keys(body).some(key => key !== 'subscriptions')) {
          return json(res, 400, { error: 'invalid_notification_subscriptions' });
        }
        const subscriptions = normalizeSubscriptions(body.subscriptions);
        if (!subscriptions) return json(res, 400, { error: 'invalid_notification_subscriptions' });
        try { await notificationStore.syncSubscriptions(userId, subscriptions); }
        catch { return json(res, 503, { error: 'notification_store_unavailable' }); }
        return json(res, 200, { status: 'synchronized', count: subscriptions.length });
      }
      if (['GET', 'POST'].includes(req.method) && path === '/api/workers/daily-digest') {
        const secret = env.CRON_SECRET;
        if (typeof secret !== 'string' || secret.length < 32 || secret.length > 2048) {
          return json(res, 503, { error: 'notification_worker_not_configured' });
        }
        const requestUrl = new URL(req.url, 'http://local');
        const querySecrets = requestUrl.searchParams.getAll('secret');
        if (querySecrets.length > 1) return json(res, 401, { error: 'unauthorized' });
        const bearerMatch = /^Bearer ([\x21-\x7e]{1,2048})$/.exec(req.headers.authorization || '');
        const suppliedSecrets = [
          req.headers['x-cron-secret'],
          querySecrets[0],
          bearerMatch?.[1],
        ].filter(value => typeof value === 'string' && /^[\x21-\x7e]{1,2048}$/.test(value));
        const expectedHash = crypto.createHash('sha256').update(secret, 'utf8').digest();
        const authorized = suppliedSecrets.some(value => {
          const suppliedHash = crypto.createHash('sha256').update(value, 'utf8').digest();
          return crypto.timingSafeEqual(suppliedHash, expectedHash);
        });
        if (!authorized) return json(res, 401, { error: 'unauthorized' });
        let result;
        try { result = await dailyDigestWorker({ now: () => new Date() }); }
        catch { return json(res, 503, { error: 'notification_worker_unavailable' }); }
        return json(res, 200, result);
      }
      if (req.method === 'PUT' && path === '/api/v1/notifications/preferences') {
        const userId = await authenticatedNotificationUser(req, res);
        if (!userId) return;
        const body = await readJsonBody(req, res);
        if (!body) return;
        const allowed = ['device_id', 'digest_time', 'preferences', 'time_zone'];
        if (Object.keys(body).some(key => !allowed.includes(key)) ||
            !validDeviceId(body.device_id) || !validPreferences(body.preferences) ||
            !validTimeZone(body.time_zone) || !validDigestTime(body.digest_time)) {
          return json(res, 400, { error: 'invalid_notification_preferences' });
        }
        try {
          if (hasConsent(body.preferences)) {
            await notificationStore.updatePreferences(userId, body.device_id, body.preferences, body.time_zone, body.digest_time);
          } else {
            await notificationStore.deleteDevice(userId, body.device_id);
          }
        } catch { return json(res, 503, { error: 'notification_store_unavailable' }); }
        return json(res, 200, { status: hasConsent(body.preferences) ? 'updated' : 'removed' });
      }
      const deleteDeviceMatch = /^\/api\/v1\/notifications\/devices\/([^/]+)$/.exec(path);
      if (req.method === 'DELETE' && deleteDeviceMatch) {
        const userId = await authenticatedNotificationUser(req, res);
        if (!userId) return;
        let deviceId;
        try { deviceId = decodeURIComponent(deleteDeviceMatch[1]); }
        catch { return json(res, 400, { error: 'invalid_device_id' }); }
        if (!validDeviceId(deviceId)) return json(res, 400, { error: 'invalid_device_id' });
        try { await notificationStore.deleteDevice(userId, deviceId); }
        catch { return json(res, 503, { error: 'notification_store_unavailable' }); }
        return json(res, 200, { status: 'removed' });
      }
      if (req.method === 'GET' && path === '/api/admin/finance/summary') {
        const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
        if (!match) return json(res, 401, { error: 'unauthorized' });
        let userId, allowed;
        try { userId = await authenticate(match[1]); if (userId) allowed = await canReadFinancial(match[1]); }
        catch { return json(res, 503, { error: 'authorization_unavailable' }); }
        if (!userId) return json(res, 401, { error: 'unauthorized' });
        if (!allowed) return json(res, 403, { error: 'forbidden' });
        if (!/^(rk|sk)_test_/.test(env.STRIPE_RESTRICTED_KEY || '')) return json(res, 503, { error: 'stripe_sandbox_not_configured' });
        const balance = await stripeFactory(env.STRIPE_RESTRICTED_KEY).balance.retrieve();
        const clean = values => Array.isArray(values) ? values.map(v => ({ currency: v.currency, amount: v.amount })) : [];
        return json(res, 200, { mode: 'sandbox', available: clean(balance.available), pending: clean(balance.pending) });
      }
      if (req.method === 'GET' && path === '/api/me') {
        const match = /^Bearer ([A-Za-z0-9._~-]{1,8192})$/.exec(req.headers.authorization || '');
        if (!match) return json(res, 401, { error: 'unauthorized' });
        let userId; try { userId = await authenticate(match[1]); } catch { return json(res, 503, { error: 'auth_unavailable' }); }
        if (!userId) return json(res, 401, { error: 'unauthorized' });
        return json(res, 200, { user_id: userId });
      }
      return json(res, 404, { error: 'not_found' });
    } catch (error) {
      if (error?.message === 'body_too_large') return json(res, 413, { error: 'body_too_large' });
      return json(res, 500, { error: 'internal_error' });
    }
  };
}
if (require.main === module) {
  const port = Number(process.env.PORT || 10000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT inválida');
  http.createServer(createHandler()).listen(port, '0.0.0.0', () => console.log('SpaceAnime backend iniciado'));
}
module.exports = { createHandler, urlValid };
