'use strict';

const cheerio = require('cheerio');

const SOURCES = [
  { id: 'anitube', origin: 'https://www.anitube.zip', mediaType: 'anime' },
  { id: 'animesdigital', origin: 'https://animesdigital.org', mediaType: 'anime' },
  { id: 'dex', origin: 'https://api.mangadex.org', mediaType: 'manga' },
];
const PAGE_SIZE = 500;
const MAX_SUBSCRIPTIONS = 10000;
const FETCH_TIMEOUT_MS = 12000;
const RELEASE_WINDOW_MS = 8 * 60 * 60 * 1000;

function safeUrl(raw, origin) {
  try {
    const url = new URL(raw, origin);
    if (url.protocol !== 'https:' || url.origin !== origin || url.username || url.password || url.hash) return null;
    return url;
  } catch {
    return null;
  }
}

async function fetchText(fetchImpl, url) {
  const response = await fetchImpl(url, {
    headers: { Accept: 'text/html,application/json', 'User-Agent': 'SpaceAnime/1.0' },
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    redirect: 'error',
  });
  if (!response.ok) throw new Error('feed_unavailable');
  const reader = response.body?.getReader();
  if (!reader) throw new Error('feed_invalid_response');
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > 2 * 1024 * 1024) {
        await reader.cancel();
        throw new Error('feed_response_too_large');
      }
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks, length).toString('utf8');
}

async function fetchJson(fetchImpl, url) {
  const text = await fetchText(fetchImpl, url);
  try {
    const value = JSON.parse(text);
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch {
    throw new Error('feed_invalid_response');
  }
}

async function listSubscriptions(store, mediaType, sourceId) {
  const rows = [];
  let after = '';
  while (rows.length < MAX_SUBSCRIPTIONS) {
    const page = await store.listSubscriptions(mediaType, sourceId, after, PAGE_SIZE);
    if (page.length === 0) return rows;
    rows.push(...page);
    const next = page[page.length - 1].content_id;
    if (next <= after) throw new Error('notification_store_invalid');
    after = next;
    if (page.length < PAGE_SIZE) return rows;
  }
  throw new Error('notification_subscription_limit');
}

function parseAnimeCard($, element, source) {
  const card = $(element);
  const link = card.find('a[href]').first();
  const episodeUrl = safeUrl(link.attr('href'), source.origin);
  if (!episodeUrl) return null;
  const episodePath = episodeUrl.pathname.replace(/^\/|\/$/g, '');
  const validPath = source.id === 'anitube'
    ? /^(?:video\/)?\d+(?:[bz]\d*)?$/.test(episodePath)
    : /^video\/a\/[\w-]+$/.test(episodePath);
  if (!validPath) return null;
  const rawTitle = source.id === 'anitube'
    ? link.attr('title') || ''
    : `${card.find('.title_anime').first().text()} – ${card.find('.number').first().text()}`;
  const episodeMatch = /Epis[oó]dio\s+(\d+(?:\.\d+)?)/i.exec(rawTitle);
  if (!episodeMatch) return null;
  const episodeNumber = episodeMatch[1].replace(/^0+(?=\d)/, '');
  const title = rawTitle.slice(0, episodeMatch.index)
    .trim().replace(/[–-]\s*$/, '')
    .replace(/\s*\(?Dublado\)?/ig, '').trim();
  if (!title) return null;
  const dubbed = /dublado/i.test(rawTitle) || /dublado/i.test(card.find('.epiCC').text());
  return {
    source,
    title: title.slice(0, 180),
    episodeNumber,
    audioVariant: dubbed ? 'dubbed' : 'subtitled',
    episodePath,
    episodeUrl: `${source.origin}/${episodePath}/`,
  };
}

function animeContentId(html, source) {
  const doc = cheerio.load(html);
  const selector = source.id === 'anitube' ? 'a.listaPagAni[href]' : 'a[href*="/anime/a/"]';
  for (const element of doc(selector).toArray()) {
    const url = safeUrl(doc(element).attr('href'), source.origin);
    if (!url) continue;
    const path = url.pathname.replace(/^\/|\/$/g, '');
    const valid = source.id === 'anitube'
      ? /^(?:video\/)?\d+(?:[bz]\d*)?$/.test(path)
      : /^anime\/a\/[\w-]+$/.test(path);
    if (valid) return path;
  }
  return null;
}

async function mapLimit(values, limit, mapper) {
  const results = new Array(values.length);
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(limit, values.length) }, async () => {
    while (cursor < values.length) {
      const index = cursor++;
      results[index] = await mapper(values[index]);
    }
  }));
  return results;
}

async function collectAnime(source, { fetchImpl, store }) {
  const subscriptions = await listSubscriptions(store, 'anime', source.id);
  if (subscriptions.length === 0) return { count: 0, failed: false };
  const subscriptionKeys = new Set(subscriptions.map(item => `${item.content_id}\0${item.audio_variant}`));
  const html = await fetchText(fetchImpl, `${source.origin}/`);
  const $ = cheerio.load(html);
  const selector = source.id === 'anitube' ? '.epiItem' : '.itemE';
  const candidates = $(selector).toArray().map(element => parseAnimeCard($, element, source)).filter(Boolean).slice(0, 60);
  const releases = await mapLimit(candidates, 4, async candidate => {
    try {
      const detail = await fetchText(fetchImpl, candidate.episodeUrl);
      const contentId = animeContentId(detail, source);
      if (!contentId || !subscriptionKeys.has(`${contentId}\0${candidate.audioVariant}`)) return null;
      return {
        media_type: 'anime',
        source_id: source.id,
        source_item_id: `${candidate.episodePath}|${candidate.audioVariant}`,
        content_id: contentId,
        audio_variant: candidate.audioVariant,
        title: `${candidate.title} - Episódio ${candidate.episodeNumber}`.slice(0, 300),
        source_url: candidate.episodeUrl,
      };
    } catch {
      return { failed: true };
    }
  });
  let count = 0;
  let failed = releases.some(release => release?.failed === true);
  for (const release of releases.filter(release => release && !release.failed)) {
    try { await store.recordRelease(release); }
    catch { failed = true; continue; }
    count++;
  }
  return { count, failed };
}

function mangaDexTitle(item, included) {
  const mangaId = item.relationships?.find(relation => relation?.type === 'manga')?.id;
  const title = included.find(entry => entry?.type === 'manga' && entry.id === mangaId)?.attributes?.title;
  if (!title || typeof title !== 'object' || Array.isArray(title)) return 'Novo capítulo de mangá';
  return title['pt-br'] || title.en || Object.values(title).find(value => typeof value === 'string') || 'Novo capítulo de mangá';
}

async function collectMangaDex({ fetchImpl, store, now }) {
  const subscriptions = await listSubscriptions(store, 'manga', 'dex');
  if (subscriptions.length === 0) return { count: 0, failed: false };
  const ids = [...new Set(subscriptions.map(item => item.content_id).filter(id =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)))];
  if (ids.length === 0) return { count: 0, failed: false };
  const subscribed = new Set(ids);
  const cutoff = now.getTime() - RELEASE_WINDOW_MS;
  let count = 0;
  for (let offset = 0; offset < ids.length; offset += 25) {
    const params = new URLSearchParams([
      ['limit', '100'],
      ['order[readableAt]', 'desc'],
      ['translatedLanguage[]', 'pt-br'],
      ['contentRating[]', 'safe'],
      ['includes[]', 'manga'],
    ]);
    for (const id of ids.slice(offset, offset + 25)) params.append('manga[]', id);
    for (let page = 0; page < 5; page++) {
      if (page > 0) params.set('offset', String(page * 100));
      const response = await fetchJson(fetchImpl, `${sourceUrl('dex', `/chapter?${params}`)}`);
      if (!Array.isArray(response.data) || !Array.isArray(response.included || [])) throw new Error('feed_invalid_response');
      const included = response.included || [];
      let pageIsOld = response.data.length === 0;
      for (const item of response.data) {
        const contentId = item?.relationships?.find(relation => relation?.type === 'manga')?.id;
        const readableAt = Date.parse(item?.attributes?.readableAt || '');
        if (Number.isFinite(readableAt) && readableAt < cutoff) pageIsOld = true;
        if (!subscribed.has(contentId) || !Number.isFinite(readableAt) || readableAt < cutoff ||
            readableAt > now.getTime() + 5 * 60 * 1000 || typeof item.id !== 'string' ||
            !/^[0-9a-f-]{36}$/i.test(item.id)) continue;
        const chapter = item.attributes?.chapter || 'especial';
        const chapterTitle = typeof item.attributes?.title === 'string' ? item.attributes.title.trim() : '';
        const mangaTitle = mangaDexTitle(item, included).trim().slice(0, 180);
        const release = {
          media_type: 'manga',
          source_id: 'dex',
          source_item_id: item.id,
          content_id: contentId,
          audio_variant: 'none',
          title: `${mangaTitle} - Capítulo ${chapter}${chapterTitle ? `: ${chapterTitle.slice(0, 100)}` : ''}`.slice(0, 300),
          source_url: `https://api.mangadex.org/chapter/${item.id}`,
        };
        await store.recordRelease(release);
        count++;
      }
      if (pageIsOld || response.data.length < 100) break;
    }
  }
  return { count, failed: false };
}

function sourceUrl(sourceId, path) {
  const source = SOURCES.find(item => item.id === sourceId);
  if (!source) throw new Error('unsupported_notification_source');
  return `${source.origin}${path}`;
}

async function collectReleases({ fetchImpl = fetch, store, now = () => new Date() }) {
  if (!store || typeof store.claimScan !== 'function') throw new Error('notification_store_unavailable');
  const currentTime = now();
  if (!(currentTime instanceof Date) || !Number.isFinite(currentTime.getTime())) throw new Error('invalid_worker_time');
  const errors = [];
  let scannedSources = 0;
  let releaseCount = 0;
  for (const source of SOURCES) {
    try {
      if (!await store.claimScan(source.id, currentTime)) continue;
      scannedSources++;
      const scan = source.mediaType === 'anime'
        ? await collectAnime(source, { fetchImpl, store })
        : await collectMangaDex({ fetchImpl, store, now: currentTime });
      releaseCount += scan.count;
      if (scan.failed) errors.push({ source_id: source.id, error: 'feed_unavailable' });
    } catch {
      errors.push({ source_id: source.id, error: 'feed_unavailable' });
    }
  }
  return { scannedSources, releaseCount, errors };
}

module.exports = { collectReleases };
