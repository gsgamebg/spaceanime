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
  return {
    async read() {
      const key = env.SUPABASE_SECRET_KEY || '';
      if (!validSupabaseUrl(env.SUPABASE_URL) || !/^sb_secret_[A-Za-z0-9_-]+$/.test(key)) {
        throw new Error('runtime_config_unavailable');
      }
      const response = await fetchImpl(new URL('/rest/v1/rpc/spaceanime_public_app_config', env.SUPABASE_URL), {
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
      return parseRuntimeConfig(payload);
    },
  };
}

module.exports = { createRuntimeConfigStore, parseRuntimeConfig };
