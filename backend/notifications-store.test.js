'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createNotificationStore } = require('./notifications-store');

const env = {
  SUPABASE_URL: 'https://spaceanime.supabase.co',
  SUPABASE_SECRET_KEY: 'sb_secret_test',
};

test('notification store writes authenticated subscriptions through the private RPC', async () => {
  const calls = [];
  const store = createNotificationStore(env, async (url, request) => {
    calls.push({ url: String(url), request });
    return new Response('true', { status: 200 });
  });
  const subscriptions = [{ media_type: 'anime', source_id: 'anitube', content_id: 'show-1', audio_variant: 'dubbed' }];

  await store.syncSubscriptions('user-a', subscriptions);

  assert.equal(calls[0].url, 'https://spaceanime.supabase.co/rest/v1/rpc/spaceanime_notification_sync_subscriptions');
  assert.equal(calls[0].request.headers.Authorization, `Bearer ${env.SUPABASE_SECRET_KEY}`);
  assert.deepEqual(JSON.parse(calls[0].request.body), { p_user_id: 'user-a', p_subscriptions: subscriptions });
});

test('notification store returns claimed digest rows and passes explicit date and batch size', async () => {
  const rows = [{ user_id: 'user-a', local_date: '2026-11-17', push_tokens: ['token-a'], title: 'Novo episódio', body: 'Capítulo disponível' }];
  const store = createNotificationStore(env, async (_url, request) => {
    assert.deepEqual(JSON.parse(request.body), { p_now: '2026-11-17T23:00:00.000Z', p_limit: 40 });
    return new Response(JSON.stringify(rows), { status: 200 });
  });

  assert.deepEqual(await store.claimDueDigests(new Date('2026-11-17T23:00:00.000Z'), 40), rows);
});

test('notification store fails closed on malformed RPC responses and invalid tokens are sent only to cleanup RPC', async () => {
  const calls = [];
  const store = createNotificationStore(env, async (url) => {
    calls.push(String(url));
    if (calls.length === 1) return new Response('not-json', { status: 200 });
    return new Response('1', { status: 200 });
  });

  await assert.rejects(() => store.claimDueDigests(new Date('2026-11-17T23:00:00Z'), 40));
  await store.removeInvalidTokens(['token-bad']);
  assert.match(calls[1], /spaceanime_notification_remove_invalid_tokens$/);
});

test('notification store pages active subscriptions, leases each source scan, and records release outcomes', async () => {
  const calls = [];
  const store = createNotificationStore(env, async (url, request) => {
    calls.push({ url: String(url), body: JSON.parse(request.body) });
    if (calls.length === 1) return new Response(JSON.stringify([{ content_id: 'manga-a', audio_variant: 'none' }]), { status: 200 });
    if (calls.length === 2) return new Response('true', { status: 200 });
    if (calls.length === 3) return new Response('71', { status: 200 });
    return new Response('true', { status: 200 });
  });
  const release = {
    media_type: 'manga',
    source_id: 'dex',
    source_item_id: 'chapter-a',
    content_id: 'manga-a',
    audio_variant: 'none',
    title: 'Novo capítulo',
    source_url: 'https://api.mangadex.org/chapter/chapter-a',
  };

  assert.deepEqual(await store.listSubscriptions('manga', 'dex', '', 100), [{ content_id: 'manga-a', audio_variant: 'none' }]);
  assert.equal(await store.claimScan('dex', new Date('2026-11-17T20:00:00Z')), true);
  assert.equal(await store.recordRelease(release), 71);
  assert.equal(await store.finishDigest('user-a', '2026-11-17', 'sent'), true);
  assert.deepEqual(calls.map(call => call.url.split('/').pop()), [
    'spaceanime_notification_list_subscriptions',
    'spaceanime_notification_claim_scan',
    'spaceanime_notification_record_release',
    'spaceanime_notification_finish_digest',
  ]);
  assert.deepEqual(calls[0].body, { p_media_type: 'manga', p_source_id: 'dex', p_after: '', p_limit: 100 });
  assert.deepEqual(calls[2].body, { p_release: release });
});
