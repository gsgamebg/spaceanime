'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler, urlValid } = require('./server');
async function withServer(handler, fn) {
 const server = http.createServer(handler);
 await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
 try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { await new Promise(resolve => server.close(resolve)); }
}
test('health does not disclose secrets', async () => {
 await withServer(createHandler({env:{SUPABASE_SECRET_KEY:'sensitive'}}), async base => {
  const r = await fetch(`${base}/health`); assert.equal(r.status, 200); assert.ok(!(await r.text()).includes('sensitive'));
 });
});
test('identity requires verified bearer token', async () => {
 await withServer(createHandler({env:{},verifyUser:async token => token === 'valid' ? '123' : null}), async base => {
  assert.equal((await fetch(`${base}/api/me`)).status,401);
  assert.equal((await fetch(`${base}/api/me`,{headers:{authorization:'Bearer wrong'}})).status,401);
  const r=await fetch(`${base}/api/me`,{headers:{authorization:'Bearer valid'}}); assert.equal((await r.json()).user_id,'123');
 });
});
test('Supabase URL excludes arbitrary hosts and credentials', () => {
 assert.equal(urlValid('https://example.supabase.co',true),true);
 assert.equal(urlValid('https://example.supabase.co.attacker.test',true),false);
 assert.equal(urlValid('https://user:pass@example.supabase.co',true),false);
 assert.equal(urlValid('http://example.supabase.co',true),false);
});
