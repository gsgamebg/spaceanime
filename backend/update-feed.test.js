'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createHandler } = require('./server');
const { loadRelease, safeArtifact } = require('./update-feed');
const release = { version:'0.7.20', versionCode:36, notes:'Melhorias', android:{url:'https://github.com/gsgamebg/spaceanime-releases/releases/download/v0.7.20/app.apk', sha256:'a'.repeat(64)}, windows:{url:'https://github.com/gsgamebg/spaceanime-releases/releases/download/v0.7.20/app.zip', sha256:'b'.repeat(64)} };
test('only approved artifact paths and formats are accepted', () => {
 assert.equal(loadRelease({SPACEANIME_RELEASE_JSON:JSON.stringify(release)}).code,36);
 for (const url of ['http://github.com/gsgamebg/spaceanime-releases/releases/download/v1/app.apk','https://evil.test/app.apk','https://github.com/other/spaceanime-releases/releases/download/v1/app.apk','https://github.com/gsgamebg/spaceanime-releases/releases/download/v1/app.exe','https://github.com/gsgamebg/spaceanime-releases/releases/download/v1/app.apk?token=x']) assert.equal(safeArtifact(url,'android'),null);
 assert.equal(loadRelease({SPACEANIME_RELEASE_JSON:JSON.stringify({...release,android:{...release.android,sha256:'wrong'}})}),null);
 assert.equal(loadRelease({}),null);
});
test('feed and redirects are versioned and unavailable without complete configuration', async () => {
 const env = {};
 const server = http.createServer(createHandler({env}));
 await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
 try {
  const base=`http://127.0.0.1:${server.address().port}`;
  assert.equal((await fetch(`${base}/updates.json`)).status,503);
  env.SPACEANIME_RELEASE_JSON=JSON.stringify(release);
  const response=await fetch(`${base}/updates.json`);
  assert.equal(response.status,200);
  const body=await response.json();
  assert.equal(body.releases.android.url,'https://spaceanime.onrender.com/downloads/36-android.apk');
  const download=await fetch(`${base}/downloads/36-android.apk`,{redirect:'manual'});
  assert.equal(download.status,302);
  assert.equal(download.headers.get('location'),release.android.url);
  assert.equal((await fetch(`${base}/downloads/35-android.apk`,{redirect:'manual'})).status,404);
  assert.equal((await fetch(`${base}/downloads/36-android.zip`,{redirect:'manual'})).status,404);
 } finally { await new Promise(resolve=>server.close(resolve)); }
});

