'use strict';

const MAX_CONFIG_BYTES = 4096;

function validSupabaseUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' &&
      url.hostname.endsWith('.supabase.co') &&
      !url.username && !url.password && !url.hash &&
      (!url.port || url.port === '443') &&
      url.pathname === '/' && !url.search;
  } catch {
    return false;
  }
}

function parseRuntimeConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).sort().join(',') !== 'ads,schemaVersion' ||
      value.schemaVersion !== 1) {
    throw new Error('runtime_config_invalid');
  }
  const ads = value.ads;
  if (!ads || typeof ads !== 'object' || Array.isArray(ads) ||
      Object.keys(ads).sort().join(',') !== 'catalogBanner,enabled,homeBanner' ||
      typeof ads.enabled !== 'boolean' ||
      typeof ads.homeBanner !== 'boolean' ||
      typeof ads.catalogBanner !== 'boolean') {
    throw new Error('runtime_config_invalid');
  }
  return {
    schemaVersion: 1,
    ads: {
      enabled: ads.enabled,
      homeBanner: ads.homeBanner,
      catalogBanner: ads.catalogBanner,
    },
  };
}

const V2_KEYS = 'catalogBanner,enabled,homeBanner,interstitialEnabled,interstitialEveryEpisodes,provider,rewardedEnabled,rewardedPassMinutes';

/** Schema 2 (app 0.7.24+): ad provider plus interstitial and rewarded settings. Schema 1 stays as-is for old apps. */
function parseRuntimeConfigV2(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).sort().join(',') !== 'ads,schemaVersion' || value.schemaVersion !== 2) {
    throw new Error('runtime_config_invalid');
  }
  const ads = value.ads;
  const int = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;
  if (!ads || typeof ads !== 'object' || Array.isArray(ads) || Object.keys(ads).sort().join(',') !== V2_KEYS ||
      !['enabled', 'homeBanner', 'catalogBanner', 'interstitialEnabled', 'rewardedEnabled'].every(k => typeof ads[k] === 'boolean') ||
      !['admob', 'startio'].includes(ads.provider) ||
      !int(ads.interstitialEveryEpisodes, 1, 20) || !int(ads.rewardedPassMinutes, 15, 1440)) {
    throw new Error('runtime_config_invalid');
  }
  return {
    schemaVersion: 2,
    ads: {
      enabled: ads.enabled, provider: ads.provider, homeBanner: ads.homeBanner, catalogBanner: ads.catalogBanner,
      interstitialEnabled: ads.interstitialEnabled, interstitialEveryEpisodes: ads.interstitialEveryEpisodes,
      rewardedEnabled: ads.rewardedEnabled, rewardedPassMinutes: ads.rewardedPassMinutes,
    },
  };
}

async function readBoundedBody(body) {
  if (!body) throw new Error('runtime_config_invalid');
  const chunks = [];
  let length = 0;
  for await (const chunk of body) {
    length += chunk.length;
    if (length > MAX_CONFIG_BYTES) throw new Error('runtime_config_too_large');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function createRuntimeConfigStore(env = process.env, fetchImpl = fetch) {
  async function load(fn, parse) {
      const key = env.SUPABASE_SECRET_KEY || '';
      if (!validSupabaseUrl(env.SUPABASE_URL) || !/^sb_secret_[A-Za-z0-9_-]+$/.test(key)) {
        throw new Error('runtime_config_unavailable');
      }
      const response = await fetchImpl(new URL(`/rest/v1/rpc/${fn}`, env.SUPABASE_URL), {
        method: 'POST',
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: '{}',
        signal: AbortSignal.timeout(8000),
        redirect: 'error',
      });
      if (!response.ok) throw new Error('runtime_config_unavailable');
      let payload;
      try {
        payload = JSON.parse(await readBoundedBody(response.body));
      } catch {
        throw new Error('runtime_config_invalid');
      }
      return parse(payload);
  }
  return {
    read: () => load('spaceanime_public_app_config', parseRuntimeConfig),
    readV2: () => load('spaceanime_public_app_config_v2', parseRuntimeConfigV2),
  };
}

module.exports = { createRuntimeConfigStore, parseRuntimeConfig, parseRuntimeConfigV2 };
