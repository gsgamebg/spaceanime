'use strict';

const { collectReleases } = require('./notifications-collector');

const INVALID_TOKEN_CODES = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered',
]);
const DIGEST_BATCH_SIZE = 40;

function validDigest(row) {
  return row && typeof row.user_id === 'string' &&
    typeof row.local_date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(row.local_date) &&
    typeof row.title === 'string' && typeof row.body === 'string' &&
    Array.isArray(row.push_tokens) &&
    row.push_tokens.every(token => typeof token === 'string' && token.length > 0);
}

async function runDailyDigest({ store, sender, now = () => new Date(), fetchImpl = fetch, collect = collectReleases }) {
  if (!store || !sender) throw new Error('notification_worker_unavailable');
  const currentTime = now();
  if (!(currentTime instanceof Date) || !Number.isFinite(currentTime.getTime())) throw new Error('invalid_worker_time');
  let collection;
  try {
    collection = await collect({ store, fetchImpl, now: () => currentTime });
  } catch {
    throw new Error('notification_collection_failed');
  }
  if (!collection || !Array.isArray(collection.errors) ||
      !Number.isSafeInteger(collection.scannedSources) || !Number.isSafeInteger(collection.releaseCount)) {
    throw new Error('notification_collection_invalid');
  }
  const dueDigests = await store.claimDueDigests(currentTime, DIGEST_BATCH_SIZE);
  if (!Array.isArray(dueDigests)) throw new Error('notification_store_invalid');
  const seen = new Set();
  let sent = 0;
  let failed = 0;

  for (const digest of dueDigests) {
    if (!validDigest(digest)) throw new Error('notification_store_invalid');
    const key = `${digest.user_id}\0${digest.local_date}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const tokens = [...new Set(digest.push_tokens)];
    let result = 'failed';
    try {
      if (tokens.length === 0) throw new Error('notification_delivery_failed');
      const responses = await sender.send(tokens, { title: digest.title, body: digest.body });
      if (!Array.isArray(responses) || responses.length !== tokens.length) throw new Error('notification_delivery_failed');
      const invalidTokens = responses.flatMap((response, index) =>
        INVALID_TOKEN_CODES.has(response?.errorCode) ? [tokens[index]] : []);
      if (invalidTokens.length > 0) await store.removeInvalidTokens(invalidTokens);
      if (responses.some(response => response?.success === true) ||
          responses.every(response => INVALID_TOKEN_CODES.has(response?.errorCode))) {
        result = 'sent';
      }
    } catch {
      result = 'failed';
    }
    await store.finishDigest(digest.user_id, digest.local_date, result);
    if (result === 'sent') sent++;
    else failed++;
  }

  if (failed > 0) throw new Error('notification_delivery_failed');
  return {
    scannedSources: collection.scannedSources,
    releaseCount: collection.releaseCount,
    collectionFailures: collection.errors.length,
    sent,
    failed,
  };
}

module.exports = { runDailyDigest };
