'use strict';

const PROVIDERS = ['admob', 'startio'];
const FORMATS = ['banner', 'interstitial', 'rewarded'];
const EVENTS = ['loaded', 'failed', 'impression', 'rewarded'];
const KEYS = 'app_version,event,format,provider,reason,schemaVersion';

/** Strict allow-list: unknown keys or values reject the whole event, so nothing arbitrary reaches the logs. */
function parseAdEvent(raw) {
  let value;
  try { value = JSON.parse(raw); } catch { return null; }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const keys = Object.keys(value).sort().join(',');
  const allowed = keys === KEYS || keys === KEYS.replace(',reason', '');
  if (!allowed || value.schemaVersion !== 1 || !PROVIDERS.includes(value.provider) || !FORMATS.includes(value.format) ||
      !EVENTS.includes(value.event) || !/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(value.app_version) ||
      (value.reason !== undefined && !(typeof value.reason === 'string' && /^[A-Za-z0-9_ .:-]{1,80}$/.test(value.reason)))) return null;
  return { provider: value.provider, format: value.format, event: value.event, app_version: value.app_version, reason: value.reason };
}

/** At most [limit] events per client per minute (in memory; resets on restart). */
function createRateLimiter(limit = 60, now = Date.now) {
  const hits = new Map();
  return key => {
    const minute = Math.floor(now() / 60_000);
    const entry = hits.get(key);
    if (!entry || entry.minute !== minute) {
      if (hits.size > 5000) hits.clear();
      hits.set(key, { minute, count: 1 });
      return true;
    }
    entry.count += 1;
    return entry.count <= limit;
  };
}

function formatAdEvent(event) {
  return `[ads] event provider=${event.provider} format=${event.format} event=${event.event} v=${event.app_version}` +
    (event.reason ? ` reason="${event.reason}"` : '');
}

function formatConfig(config, schema) {
  const ads = config.ads;
  return `[ads] config schema=${schema} enabled=${ads.enabled} provider=${ads.provider || 'admob'} banner(home=${ads.homeBanner},catalog=${ads.catalogBanner})` +
    (schema === 2 ? ` interstitial=${ads.interstitialEnabled}/every${ads.interstitialEveryEpisodes} rewarded=${ads.rewardedEnabled}/${ads.rewardedPassMinutes}min` : '');
}

module.exports = { parseAdEvent, createRateLimiter, formatAdEvent, formatConfig };
