'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
let trigger;
try { trigger = require('./trigger-daily-digest'); }
catch { trigger = {}; }

const env = {
  BACKEND_BASE_URL: 'https://spaceanime.onrender.com',
  CRON_SECRET: 'cron-secret-value-which-is-at-least-32-chars',
};

test('daily trigger posts to the worker endpoint with the bearer secret and a bounded timeout', async () => {
  assert.equal(typeof trigger.triggerDailyDigest, 'function');
  let request;
  let timeout;
  const result = await trigger.triggerDailyDigest({
    env,
    fetchImpl: async (url, options) => {
      request = { url: String(url), options };
      return new Response('{"status":"ok"}', { status: 200 });
    },
    timeoutSignal: milliseconds => {
      timeout = milliseconds;
      return new AbortController().signal;
    },
  });

  assert.equal(request.url, 'https://spaceanime.onrender.com/api/workers/daily-digest');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.headers.Authorization, `Bearer ${env.CRON_SECRET}`);
  assert.equal(request.options.redirect, 'error');
  assert.equal(request.options.signal instanceof AbortSignal, true);
  assert.ok(timeout > 0 && timeout <= 5 * 60 * 1000);
  assert.equal(result, true);
});

test('daily trigger rejects unsafe backend URLs and missing secrets before making a request', async () => {
  assert.equal(typeof trigger.triggerDailyDigest, 'function');
  let requests = 0;
  const fetchImpl = async () => { requests++; return new Response('', { status: 200 }); };

  await assert.rejects(() => trigger.triggerDailyDigest({
    env: { ...env, BACKEND_BASE_URL: 'http://spaceanime.onrender.com' },
    fetchImpl,
  }), error => error.message === 'daily_digest_trigger_not_configured');
  await assert.rejects(() => trigger.triggerDailyDigest({
    env: { BACKEND_BASE_URL: env.BACKEND_BASE_URL },
    fetchImpl,
  }), error => error.message === 'daily_digest_trigger_not_configured');
  assert.equal(requests, 0);
});

test('daily trigger reports non-2xx responses without exposing its secret', async () => {
  assert.equal(typeof trigger.triggerDailyDigest, 'function');
  await assert.rejects(() => trigger.triggerDailyDigest({
    env,
    fetchImpl: async () => new Response('private service detail', { status: 503 }),
  }), error => error.message === 'daily_digest_trigger_failed' &&
    !error.message.includes(env.CRON_SECRET));
});
