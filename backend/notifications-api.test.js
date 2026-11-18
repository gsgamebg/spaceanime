'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');

async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

const device = {
  device_id: 'android-main',
  push_token: 'fcm-token-secret-value',
  preferences: { anime: true, manga: false, announcements: true },
  time_zone: 'America/Sao_Paulo',
  digest_time: '20:00',
};

test('notification registration requires an authenticated user and never returns the token', async () => {
  const saved = [];
  const handler = createHandler({
    env: {},
    verifyUser: async token => token === 'valid' ? 'user-a' : null,
    notificationStore: { saveDevice: async (userId, value) => saved.push({ userId, value }) },
  });
  await withServer(handler, async base => {
    assert.equal((await fetch(`${base}/api/v1/notifications/devices`, { method: 'POST' })).status, 401);
    const response = await fetch(`${base}/api/v1/notifications/devices`, {
      method: 'POST',
      headers: { authorization: 'Bearer valid', 'content-type': 'application/json' },
      body: JSON.stringify(device),
    });
    assert.equal(response.status, 201);
    const body = await response.text();
    assert.equal(body.includes(device.push_token), false);
    assert.deepEqual(saved, [{ userId: 'user-a', value: device }]);
  });
});

test('registration rejects client-supplied ownership and invalid consent payloads', async () => {
  let saves = 0;
  const handler = createHandler({
    env: {},
    verifyUser: async () => 'user-a',
    notificationStore: { saveDevice: async () => { saves++; } },
  });
  await withServer(handler, async base => {
    async function send(payload) {
      return fetch(`${base}/api/v1/notifications/devices`, {
        method: 'POST',
        headers: { authorization: 'Bearer valid', 'content-type': 'application/json' },
        body: JSON.stringify(payload),
      });
    }
    assert.equal((await send({ ...device, user_id: 'user-b' })).status, 400);
    assert.equal((await send({ ...device, preferences: { anime: false, manga: false, announcements: false } })).status, 400);
    assert.equal((await send({ ...device, time_zone: 'Not/A_Timezone' })).status, 400);
    assert.equal(saves, 0);
  });
});

test('turning all notification preferences off removes the authenticated device', async () => {
  const removed = [];
  const handler = createHandler({
    env: {},
    verifyUser: async () => 'user-a',
    notificationStore: {
      updatePreferences: async (userId, deviceId, preferences, timeZone, digestTime) => {
        assert.deepEqual(preferences, { anime: false, manga: false, announcements: false });
        removed.push({ userId, deviceId, timeZone, digestTime });
      },
      deleteDevice: async (userId, deviceId) => removed.push({ userId, deviceId }),
    },
  });
  await withServer(handler, async base => {
    const response = await fetch(`${base}/api/v1/notifications/preferences`, {
      method: 'PUT',
      headers: { authorization: 'Bearer valid', 'content-type': 'application/json' },
      body: JSON.stringify({
        device_id: device.device_id,
        preferences: { anime: false, manga: false, announcements: false },
        time_zone: device.time_zone,
        digest_time: device.digest_time,
      }),
    });
    assert.equal(response.status, 200);
    assert.deepEqual(removed, [{ userId: 'user-a', deviceId: device.device_id }]);
  });
});

test('opt-out delete requires authentication and deletes only the authenticated user device', async () => {
  const deleted = [];
  const handler = createHandler({
    env: {},
    verifyUser: async token => token === 'valid' ? 'user-a' : null,
    notificationStore: { deleteDevice: async (userId, deviceId) => deleted.push({ userId, deviceId }) },
  });
  await withServer(handler, async base => {
    assert.equal((await fetch(`${base}/api/v1/notifications/devices/android-main`, { method: 'DELETE' })).status, 401);
    const response = await fetch(`${base}/api/v1/notifications/devices/android-main`, {
      method: 'DELETE',
      headers: { authorization: 'Bearer valid' },
    });
    assert.equal(response.status, 200);
    assert.deepEqual(deleted, [{ userId: 'user-a', deviceId: 'android-main' }]);
  });
});

test('store failure and oversized payload fail explicitly without a success response', async () => {
  const handler = createHandler({
    env: {},
    verifyUser: async () => 'user-a',
    notificationStore: { saveDevice: async () => { throw new Error('private database details'); } },
  });
  await withServer(handler, async base => {
    const request = payload => fetch(`${base}/api/v1/notifications/devices`, {
      method: 'POST',
      headers: { authorization: 'Bearer valid', 'content-type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const failed = await request(device);
    assert.equal(failed.status, 503);
    assert.deepEqual(await failed.json(), { error: 'notification_store_unavailable' });
    const oversized = await request({ ...device, push_token: 'x'.repeat(70_000) });
    assert.equal(oversized.status, 413);
    assert.deepEqual(await oversized.json(), { error: 'body_too_large' });
  });
});

test('subscription replacement is authenticated, rejects client ownership, and passes only validated IDs to the store', async () => {
  const saved = [];
  const handler = createHandler({
    env: {},
    verifyUser: async token => token === 'valid' ? 'user-a' : null,
    notificationStore: { syncSubscriptions: async (userId, subscriptions) => saved.push({ userId, subscriptions }) },
  });
  const subscriptions = [{
    media_type: 'anime',
    source_id: 'anitube',
    content_id: 'anime/123',
    audio_variant: 'dubbed',
  }];
  await withServer(handler, async base => {
    const send = payload => fetch(`${base}/api/v1/notifications/subscriptions`, {
      method: 'PUT',
      headers: { authorization: 'Bearer valid', 'content-type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const unauthenticated = await fetch(`${base}/api/v1/notifications/subscriptions`, { method: 'PUT' });
    assert.equal(unauthenticated.status, 401);
    assert.equal((await send({ user_id: 'user-b', subscriptions })).status, 400);
    assert.equal((await send({ subscriptions: [{ ...subscriptions[0], source_id: 'untrusted' }] })).status, 400);
    assert.equal((await send({ subscriptions })).status, 200);
    assert.deepEqual(saved, [{ userId: 'user-a', subscriptions }]);
  });
});

test('daily digest worker requires a configured secret and constant-time bearer authorization', async () => {
  const secret = 'cron-secret-value-which-is-at-least-32-chars';
  let runs = 0;
  const worker = async () => {
    runs++;
    return { scannedSources: 2, releaseCount: 3, sent: 1, failed: 0 };
  };
  await withServer(createHandler({ env: {}, runDailyDigest: worker }), async base => {
    assert.equal((await fetch(`${base}/api/workers/daily-digest`, { method: 'POST' })).status, 503);
  });
  await withServer(createHandler({ env: { CRON_SECRET: secret }, runDailyDigest: worker }), async base => {
    const request = authorization => fetch(`${base}/api/workers/daily-digest`, {
      method: 'POST',
      headers: authorization ? { authorization } : {},
    });
    assert.equal((await request(`Bearer ${secret.slice(0, -1)}x`)).status, 401);
    const response = await request(`Bearer ${secret}`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { scannedSources: 2, releaseCount: 3, sent: 1, failed: 0 });
    assert.equal(runs, 1);
  });
});

test('daily digest worker accepts GET query secrets and x-cron-secret without breaking bearer POST', async () => {
  const secret = 'cron-secret-value-which-is-at-least-32-chars';
  let runs = 0;
  const handler = createHandler({
    env: { CRON_SECRET: secret },
    runDailyDigest: async () => {
      runs++;
      return { sent: 1 };
    },
  });
  await withServer(handler, async base => {
    const getByQuery = await fetch(`${base}/api/workers/daily-digest?secret=${encodeURIComponent(secret)}`);
    assert.equal(getByQuery.status, 200);
    assert.deepEqual(await getByQuery.json(), { sent: 1 });

    const getByHeader = await fetch(`${base}/api/workers/daily-digest`, {
      headers: { 'x-cron-secret': secret },
    });
    assert.equal(getByHeader.status, 200);

    const postByBearer = await fetch(`${base}/api/workers/daily-digest`, {
      method: 'POST',
      headers: { authorization: `Bearer ${secret}` },
    });
    assert.equal(postByBearer.status, 200);
    assert.equal(runs, 3);
  });
});

test('daily digest worker rejects incorrect and duplicate query secrets', async () => {
  const secret = 'cron-secret-value-which-is-at-least-32-chars';
  let runs = 0;
  const handler = createHandler({
    env: { CRON_SECRET: secret },
    runDailyDigest: async () => {
      runs++;
      return { sent: 1 };
    },
  });
  await withServer(handler, async base => {
    const incorrect = await fetch(`${base}/api/workers/daily-digest?secret=wrong`);
    assert.equal(incorrect.status, 401);

    const duplicated = await fetch(
      `${base}/api/workers/daily-digest?secret=${encodeURIComponent(secret)}&secret=wrong`,
    );
    assert.equal(duplicated.status, 401);
    assert.equal(runs, 0);
  });
});

test('daily digest worker failure is explicit and does not expose internal errors', async () => {
  const handler = createHandler({
    env: { CRON_SECRET: 'cron-secret-value-which-is-at-least-32-chars' },
    runDailyDigest: async () => { throw new Error('private FCM service account detail'); },
  });
  await withServer(handler, async base => {
    const response = await fetch(`${base}/api/workers/daily-digest`, {
      method: 'POST',
      headers: { authorization: 'Bearer cron-secret-value-which-is-at-least-32-chars' },
    });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: 'notification_worker_unavailable' });
  });
});
