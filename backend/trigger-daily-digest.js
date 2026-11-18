'use strict';

const REQUEST_TIMEOUT_MS = 5 * 60 * 1000;

function backendUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password ||
        (url.port && url.port !== '443') || url.pathname !== '/' || url.search || url.hash) return null;
    return url;
  } catch {
    return null;
  }
}

async function triggerDailyDigest({
  env = process.env,
  fetchImpl = fetch,
  timeoutSignal = AbortSignal.timeout,
} = {}) {
  const base = backendUrl(env.BACKEND_BASE_URL);
  const secret = env.CRON_SECRET;
  if (!base || typeof secret !== 'string' || !/^[\x21-\x7e]{32,2048}$/.test(secret)) {
    throw new Error('daily_digest_trigger_not_configured');
  }
  let response;
  try {
    response = await fetchImpl(new URL('/api/workers/daily-digest', base), {
      method: 'POST',
      headers: { Authorization: `Bearer ${secret}`, Accept: 'application/json' },
      signal: timeoutSignal(REQUEST_TIMEOUT_MS),
      redirect: 'error',
    });
  } catch {
    throw new Error('daily_digest_trigger_failed');
  }
  if (!response?.ok) throw new Error('daily_digest_trigger_failed');
  return true;
}

if (require.main === module) {
  triggerDailyDigest()
    .then(() => console.log('Daily digest worker triggered'))
    .catch(() => {
      console.error('Daily digest trigger failed');
      process.exitCode = 1;
    });
}

module.exports = { triggerDailyDigest };
