'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { Readable } = require('node:stream');
const { parseTelemetry, clientIp } = require('./telemetry');
const { createHandler } = require('./server');

const valid = {
  schemaVersion: 1,
  install_id: '3f2b8c1e-9a4d-4c7b-8e2f-1a2b3c4d5e6f',
  platform: 'android',
  app_version: '0.7.23',
  app_code: 39,
  os_version: 'Android 14 (API 34)',
  device_model: 'samsung SM-A546E',
  locale: 'pt-BR',
  time_zone: 'America/Sao_Paulo',
};

test('accepts only the allow-listed event shape', () => {
  assert.equal(parseTelemetry(JSON.stringify(valid)).platform, 'android');
  assert.equal(parseTelemetry(JSON.stringify({ ...valid, email: 'x@y.z' })), null);
  assert.equal(parseTelemetry(JSON.stringify({ ...valid, install_id: 'not-a-uuid' })), null);
  assert.equal(parseTelemetry(JSON.stringify({ ...valid, platform: 'ios' })), null);
  assert.equal(parseTelemetry(JSON.stringify({ ...valid, device_model: '<script>' })), null);
  assert.equal(parseTelemetry('not json'), null);
});

test('client IP comes from the first forwarded entry', () => {
  assert.equal(clientIp({ headers: { 'x-forwarded-for': '203.0.113.7, 10.0.0.1' } }), '203.0.113.7');
  assert.equal(clientIp({ headers: {}, socket: { remoteAddress: '::ffff:198.51.100.2' } }), '198.51.100.2');
  assert.equal(clientIp({ headers: { 'x-forwarded-for': 'garbage' } }), null);
});

function call(handler, { method = 'POST', path = '/api/v1/telemetry', headers = {}, body = '' }) {
  return new Promise((resolve) => {
    const req = Readable.from([Buffer.from(body)]);
    Object.assign(req, { method, url: path, headers, socket: { remoteAddress: '127.0.0.1' } });
    const res = {
      writeHead(status) { this.status = status; },
      end(data) { resolve({ status: this.status, body: data ? JSON.parse(data) : null }); },
    };
    handler(req, res);
  });
}

test('route records the event with the client IP', async () => {
  const recorded = [];
  const handler = createHandler({ env: {}, telemetryStore: { record: async (event, ip) => recorded.push({ event, ip }) } });
  const response = await call(handler, {
    headers: { 'content-type': 'application/json', 'x-forwarded-for': '203.0.113.7' },
    body: JSON.stringify(valid),
  });
  assert.equal(response.status, 202);
  assert.equal(recorded[0].ip, '203.0.113.7');
  assert.equal(recorded[0].event.install_id, valid.install_id);
  assert.equal('schemaVersion' in recorded[0].event, false);
});

test('route rejects bad input and reports storage outages', async () => {
  const failing = createHandler({ env: {}, telemetryStore: { record: async () => { throw new Error('down'); } } });
  assert.equal((await call(failing, { headers: { 'content-type': 'text/plain' }, body: '{}' })).status, 415);
  assert.equal((await call(failing, { headers: { 'content-type': 'application/json' }, body: '{}' })).status, 400);
  assert.equal((await call(failing, { headers: { 'content-type': 'application/json' }, body: JSON.stringify(valid) })).status, 503);
});
