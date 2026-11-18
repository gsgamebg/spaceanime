'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
let collector;
try { collector = require('./notifications-collector'); }
catch { collector = {}; }

test('collector parses only same-host anime episodes and preserves series, variant, and release identity', async () => {
  assert.equal(typeof collector.collectReleases, 'function');
  const calls = [];
  const store = {
    claimScan: async () => true,
    listSubscriptions: async (mediaType, sourceId) => mediaType === 'anime' && sourceId === 'animesdigital'
      ? [
          { content_id: 'anime/a/naruto', audio_variant: 'dubbed' },
          { content_id: 'anime/a/naruto', audio_variant: 'subtitled' },
        ]
      : [],
    recordRelease: async release => { calls.push(release); return calls.length; },
  };
  const fetchImpl = async url => {
    const parsed = new URL(url);
    if (parsed.origin !== 'https://animesdigital.org') throw new Error('unexpected_host');
    if (parsed.pathname === '/') {
      return new Response(
        `<div class="itemE"><a href="/video/a/ep-12/"><div class="title_anime">Naruto Episódio 12</div><div class="number">12</div><span class="epiCC">Dublado</span></a></div>`,
        { status: 200 },
      );
    }
    if (parsed.pathname === '/video/a/ep-12/') {
      return new Response(`<a href="/anime/a/naruto">Naruto</a>`, { status: 200 });
    }
    throw new Error('unexpected_path');
  };

  const result = await collector.collectReleases({ fetchImpl, store, now: () => new Date('2026-11-17T18:00:00Z') });

  assert.equal(result.errors.length, 0);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], {
    media_type: 'anime',
    source_id: 'animesdigital',
    source_item_id: 'video/a/ep-12|dubbed',
    content_id: 'anime/a/naruto',
    audio_variant: 'dubbed',
    title: 'Naruto - Episódio 12',
    source_url: 'https://animesdigital.org/video/a/ep-12/',
  });
});

test('collector isolates an unavailable provider and keeps valid MangaDex chapter releases', async () => {
  assert.equal(typeof collector.collectReleases, 'function');
  const mangaId = 'c0ffee00-cafe-4000-8000-000000000001';
  const chapterId = 'd0ffee00-cafe-4000-8000-000000000001';
  const recorded = [];
  const store = {
    claimScan: async () => true,
    listSubscriptions: async (mediaType, sourceId, after) => {
      if (mediaType === 'anime' && sourceId === 'anitube') return [{ content_id: 'video/88', audio_variant: 'subtitled' }];
      if (mediaType === 'manga' && sourceId === 'dex' && after === '') return [{ content_id: mangaId, audio_variant: 'none' }];
      return [];
    },
    recordRelease: async release => { recorded.push(release); return recorded.length; },
  };
  const fetchImpl = async url => {
    const parsed = new URL(url);
    if (parsed.hostname === 'www.anitube.zip') throw new Error('provider_down');
    if (parsed.hostname === 'api.mangadex.org') {
      assert.equal(parsed.pathname, '/chapter');
      return new Response(JSON.stringify({
        data: [
          {
            id: chapterId,
            attributes: { chapter: '4', title: 'A promessa', readableAt: '2026-11-17T17:00:00Z' },
            relationships: [{ id: mangaId, type: 'manga' }],
          },
          {
            id: 'e0ffee00-cafe-4000-8000-000000000001',
            attributes: { chapter: '3', title: 'Anterior', readableAt: '2026-11-16T17:00:00Z' },
            relationships: [{ id: mangaId, type: 'manga' }],
          },
        ],
        included: [{ id: mangaId, type: 'manga', attributes: { title: { en: 'One Piece' } } }],
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    throw new Error('unexpected_host');
  };

  const result = await collector.collectReleases({ fetchImpl, store, now: () => new Date('2026-11-17T18:00:00Z') });

  assert.equal(result.errors.length, 1);
  assert.equal(result.errors[0].source_id, 'anitube');
  assert.deepEqual(recorded, [{
    media_type: 'manga',
    source_id: 'dex',
    source_item_id: chapterId,
    content_id: mangaId,
    audio_variant: 'none',
    title: 'One Piece - Capítulo 4: A promessa',
    source_url: `https://api.mangadex.org/chapter/${chapterId}`,
  }]);
});

test('collector keeps successful episode releases when an individual detail page is unavailable', async () => {
  assert.equal(typeof collector.collectReleases, 'function');
  const recorded = [];
  const store = {
    claimScan: async sourceId => sourceId === 'animesdigital',
    listSubscriptions: async (_mediaType, sourceId) => sourceId === 'animesdigital'
      ? [{ content_id: 'anime/a/naruto', audio_variant: 'dubbed' }]
      : [],
    recordRelease: async release => { recorded.push(release); return recorded.length; },
  };
  const fetchImpl = async url => {
    const parsed = new URL(url);
    if (parsed.pathname === '/') {
      return new Response(`
        <div class="itemE"><a href="/video/a/ep-12/"><div class="title_anime">Naruto Episódio 12</div><div class="epiCC">Dublado</div></a></div>
        <div class="itemE"><a href="/video/a/ep-13/"><div class="title_anime">Naruto Episódio 13</div><div class="epiCC">Dublado</div></a></div>
        <div class="itemE"><a href="https://attacker.example/video/a/ep-14/"><div class="title_anime">Naruto Episódio 14</div><div class="epiCC">Dublado</div></a></div>
      `, { status: 200 });
    }
    if (parsed.pathname === '/video/a/ep-12/') return new Response('<a href="/anime/a/naruto">Naruto</a>', { status: 200 });
    if (parsed.pathname === '/video/a/ep-13/') throw new Error('one_episode_unavailable');
    throw new Error('unexpected_request');
  };

  const result = await collector.collectReleases({ fetchImpl, store, now: () => new Date('2026-11-17T18:00:00Z') });

  assert.deepEqual(recorded.map(release => release.source_item_id), ['video/a/ep-12|dubbed']);
  assert.deepEqual(result.errors, [{ source_id: 'animesdigital', error: 'feed_unavailable' }]);
  assert.equal(result.releaseCount, 1);
});
