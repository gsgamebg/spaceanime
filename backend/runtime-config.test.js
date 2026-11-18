'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');
const { createRuntimeConfigStore } = require('./runtime-config-store');

const config = {
  schemaVersion: 1,
  ads: { enabled: true, homeBanner: true, catalogBanner: false },
};

async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('runtime config returns only public ad flags', async () => {
  const env = {
    SUPABASE_URL: 'https://project.supabase.co',
    SUPABASE_SECRET_KEY: 'sb_secret_do_not_return',
    FIREBASE_PRIVATE_KEY: 'private value',
  };
  let request;
  const store = createRuntimeConfigStore(env, async (url, options) => {
    request = { url: String(url), options };
    return new Response(JSON.stringify(config), { status: 200 });
  });

  const result = await store.read();

  assert.deepEqual(result, config);
  assert.deepEqual(Object.keys(result), ['schemaVersion', 'ads']);
  assert.deepEqual(Object.keys(result.ads).sort(), ['catalogBanner', 'enabled', 'homeBanner']);
  assert.equal(JSON.stringify(result).includes(env.SUPABASE_SECRET_KEY), false);
  assert.equal(JSON.stringify(result).includes(env.FIREBASE_PRIVATE_KEY), false);
  assert.equal(request.url, 'https://project.supabase.co/rest/v1/rpc/spaceanime_public_app_config');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.headers.apikey, env.SUPABASE_SECRET_KEY);
  assert.equal(request.options.redirect, 'error');
});

test('runtime config rejects malformed or oversized remote config', async () => {
  const env = {
    SUPABASE_URL: 'https://project.supabase.co',
    SUPABASE_SECRET_KEY: 'sb_secret_example',
  };
  const malformed = [
    { schemaVersion: 2, ads: config.ads },
    { schemaVersion: 1, ads: { enabled: true, homeBanner: 'yes', catalogBanner: false } },
    { schemaVersion: 1, ads: { enabled: true, homeBanner: true } },
    { schemaVersion: 1, ads: { enabled: true, homeBanner: true, catalogBanner: false, adminKey: 'secret' } },
  ];
  for (const body of malformed) {
    const store = createRuntimeConfigStore(env, async () => new Response(JSON.stringify(body), { status: 200 }));
    await assert.rejects(store.read());
  }
  const oversized = createRuntimeConfigStore(env, async () => new Response(' '.repeat(4097), { status: 200 }));
  await assert.rejects(oversized.read());
});

test('app config route returns only validated public configuration', async () => {
  const env = { SUPABASE_SECRET_KEY: 'sb_secret_not_public' };
  await withServer(createHandler({
    env,
    runtimeConfigStore: { read: async () => config },
  }), async base => {
    const response = await fetch(`${base}/api/app-config`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), config);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.equal(response.headers.get('content-type'), 'application/json; charset=utf-8');
  });
});

test('app config route returns unavailable when the store fails', async () => {
  await withServer(createHandler({
    env: {},
    runtimeConfigStore: { read: async () => { throw new Error('private database detail'); } },
  }), async base => {
    const response = await fetch(`${base}/api/app-config`);
    assert.equal(response.status, 503);
    const body = await response.text();
    assert.deepEqual(JSON.parse(body), { error: 'app_config_unavailable' });
    assert.equal(body.includes('private database detail'), false);
  });
});
