'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
let worker;
try { worker = require('./notifications-worker'); }
catch { worker = {}; }

test('daily digest sends only claimed user-date rows and removes only explicitly invalid FCM tokens', async () => {
  assert.equal(typeof worker.runDailyDigest, 'function');
  const finished = [];
  const removed = [];
  const sent = [];
  const store = {
    claimDueDigests: async () => [{
      user_id: 'user-a',
      local_date: '2026-11-17',
      title: 'Novos lançamentos',
      body: 'Naruto - Episódio 12',
      push_tokens: ['token-valid', 'token-invalid'],
    }],
    finishDigest: async (...args) => { finished.push(args); },
    removeInvalidTokens: async tokens => { removed.push(tokens); },
  };
  const sender = {
    send: async (tokens, notification) => {
      sent.push({ tokens, notification });
      return [
        { success: true },
        { success: false, errorCode: 'messaging/registration-token-not-registered' },
      ];
    },
  };
  const collect = async () => ({ scannedSources: 0, releaseCount: 0, errors: [] });

  const result = await worker.runDailyDigest({ store, sender, now: () => new Date('2026-11-17T23:00:00Z'), collect });

  assert.equal(result.sent, 1);
  assert.equal(result.failed, 0);
  assert.deepEqual(sent, [{
    tokens: ['token-valid', 'token-invalid'],
    notification: { title: 'Novos lançamentos', body: 'Naruto - Episódio 12' },
  }]);
  assert.deepEqual(removed, [['token-invalid']]);
  assert.deepEqual(finished, [['user-a', '2026-11-17', 'sent']]);
});

test('overlapping worker runs send only rows claimed by the atomic store operation', async () => {
  assert.equal(typeof worker.runDailyDigest, 'function');
  let claimed = false;
  let sends = 0;
  const store = {
    claimDueDigests: async () => {
      if (claimed) return [];
      claimed = true;
      await new Promise(resolve => setTimeout(resolve, 5));
      return [{ user_id: 'user-a', local_date: '2026-11-17', title: 'Novos', body: 'Um', push_tokens: ['token-a'] }];
    },
    finishDigest: async () => {},
    removeInvalidTokens: async () => {},
  };
  const sender = { send: async () => { sends++; return [{ success: true }]; } };
  const collect = async () => ({ scannedSources: 0, releaseCount: 0, errors: [] });

  await Promise.all([
    worker.runDailyDigest({ store, sender, now: () => new Date('2026-11-17T23:00:00Z'), collect }),
    worker.runDailyDigest({ store, sender, now: () => new Date('2026-11-17T23:00:00Z'), collect }),
  ]);

  assert.equal(sends, 1);
});

test('daily digest marks failed sends and fails the worker request for retry', async () => {
  assert.equal(typeof worker.runDailyDigest, 'function');
  const outcomes = [];
  const store = {
    claimDueDigests: async () => [{
      user_id: 'user-a', local_date: '2026-11-17', title: 'Novos', body: 'Um', push_tokens: ['token-a'],
    }],
    finishDigest: async (_user, _date, result) => outcomes.push(result),
    removeInvalidTokens: async () => {},
  };
  const sender = { send: async () => { throw new Error('private FCM details'); } };
  const collect = async () => ({ scannedSources: 0, releaseCount: 0, errors: [] });

  await assert.rejects(
    () => worker.runDailyDigest({ store, sender, now: () => new Date('2026-11-17T23:00:00Z'), collect }),
    error => error.message === 'notification_delivery_failed',
  );
  assert.deepEqual(outcomes, ['failed']);
});
