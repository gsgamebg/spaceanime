'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');
const { parseSourceHealth, healthSecretOk } = require('./source-health');

const SECRET = 'a-synthetic-shared-secret-with-32-chars!!';
const good = { checks: [{ name: 'anitube · vídeo', ok: true, ms: 1042, detail: 'Servidor principal' }, { name: 'Mangás · servidor 2', ok: false, ms: 552, detail: 'Serviço indisponível (502)' }] };
async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}

test('report shape is strict and text is cleaned', () => {
  assert.equal(parseSourceHealth(JSON.stringify(good)).checks.length, 2);
  const dirty = parseSourceHealth(JSON.stringify({ checks: [{ name: 'x', ok: true, ms: 1, detail: 'a\u0000b\nc' + 'z'.repeat(400) }] }));
  assert.ok(!/[\u0000-\u001f]/.test(dirty.checks[0].detail) && dirty.checks[0].detail.length === 200);
  for (const bad of [{}, { checks: [] }, { checks: [{ ...good.checks[0], extra: 1 }] }, { checks: [{ ...good.checks[0], name: '<script>' }] },
                      { checks: [{ ...good.checks[0], ms: -1 }] }, { checks: [{ ...good.checks[0], ok: 'yes' }] }, { checks: Array(31).fill(good.checks[0]) }, { x: 1, checks: good.checks }])
    assert.equal(parseSourceHealth(JSON.stringify(bad)), null);
  assert.equal(parseSourceHealth('nope'), null);
});

test('secret check needs a long secret and is exact', () => {
  const req = headers => ({ headers });
  assert.equal(healthSecretOk(req({ 'x-health-secret': SECRET }), SECRET), true);
  assert.equal(healthSecretOk(req({ authorization: `Bearer ${SECRET}` }), SECRET), true);
  assert.equal(healthSecretOk(req({ 'x-health-secret': SECRET + 'x' }), SECRET), false);
  assert.equal(healthSecretOk(req({}), SECRET), false);
  assert.equal(healthSecretOk(req({ 'x-health-secret': 'short' }), 'short'), false);
  assert.equal(healthSecretOk(req({ 'x-health-secret': SECRET }), undefined), false);
});

test('the endpoint refuses callers without the secret and stores valid reports', async () => {
  const stored = [];
  const store = { async record(checks) { stored.push(checks); } };
  await withServer(createHandler({ env: { SOURCE_HEALTH_SECRET: SECRET }, sourceHealthStore: store }), async base => {
    const send = (headers, body) => fetch(`${base}/api/v1/source-health`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
    assert.equal((await send({}, good)).status, 401);
    assert.equal((await send({ 'x-health-secret': 'wrong-wrong-wrong-wrong-wrong-wrong-wrong' }, good)).status, 401);
    assert.equal((await send({ 'x-health-secret': SECRET }, { checks: [] })).status, 400);
    assert.equal(stored.length, 0);
    assert.equal((await send({ 'x-health-secret': SECRET }, good)).status, 202);
    assert.equal(stored[0].length, 2);
  });
  await withServer(createHandler({ env: {}, sourceHealthStore: store }), async base => {
    const r = await fetch(`${base}/api/v1/source-health`, { method: 'POST', headers: { 'content-type': 'application/json', 'x-health-secret': SECRET }, body: JSON.stringify(good) });
    assert.equal(r.status, 401, 'unconfigured secret must never accept');
  });
  const failing = { async record() { throw new Error('db'); } };
  await withServer(createHandler({ env: { SOURCE_HEALTH_SECRET: SECRET }, sourceHealthStore: failing }), async base => {
    const r = await fetch(`${base}/api/v1/source-health`, { method: 'POST', headers: { 'content-type': 'application/json', 'x-health-secret': SECRET }, body: JSON.stringify(good) });
    assert.equal(r.status, 503);
  });
});
