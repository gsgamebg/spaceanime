'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');
const { parseAdEvent, createRateLimiter } = require('./ads-events');

const ok = { schemaVersion: 1, provider: 'startio', format: 'banner', event: 'failed', app_version: '0.7.25', reason: 'no fill' };
async function withServer(handler, fn) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}
test('ad events accept only the allow-listed shape', () => {
  assert.equal(parseAdEvent(JSON.stringify(ok)).reason, 'no fill');
  const { reason, ...noReason } = ok;
  assert.ok(parseAdEvent(JSON.stringify(noReason)));
  for (const bad of [{ ...ok, provider: 'x' }, { ...ok, format: 'popup' }, { ...ok, app_version: 'abc' }, { ...ok, reason: '<script>' }, { ...ok, extra: 1 }, { ...ok, reason: 'a'.repeat(81) }])
    assert.equal(parseAdEvent(JSON.stringify(bad)), null);
  assert.equal(parseAdEvent('not json'), null);
});
test('rate limiter blocks the 61st event in a minute and resets next minute', () => {
  let t = 0; const allow = createRateLimiter(60, () => t);
  for (let i = 0; i < 60; i++) assert.equal(allow('ip'), true);
  assert.equal(allow('ip'), false);
  t = 61_000; assert.equal(allow('ip'), true);
});
test('endpoint logs accepted events and rejects bad ones', async () => {
  const lines = []; const original = console.log; console.log = line => lines.push(String(line));
  try {
    await withServer(createHandler({ env: {} }), async base => {
      const send = body => fetch(`${base}/api/v1/ads/events`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
      assert.equal((await send(ok)).status, 202);
      assert.equal((await send({ ...ok, provider: 'x' })).status, 400);
    });
  } finally { console.log = original; }
  assert.ok(lines.some(l => l === '[ads] event provider=startio format=banner event=failed v=0.7.25 reason="no fill"'));
});
