'use strict';

// Published metadata contains no secrets. Invalid/incomplete configuration never announces a release.
const ORIGIN = 'https://spaceanime.onrender.com';
const STORAGE_HOST = 'btftbtkbebbkiogsybjn.supabase.co';
function safeArtifact(raw, platform) {
  try {
    const u = new URL(raw);
    if (u.protocol !== 'https:' || u.username || u.password || u.port || u.search || u.hash) return null;
    const suffix = platform === 'android' ? '.apk' : '.zip';
    if (!u.pathname.toLowerCase().endsWith(suffix)) return null;
    const storage = u.hostname === STORAGE_HOST && u.pathname.startsWith('/storage/v1/object/public/spaceanime-releases/');
    const github = u.hostname === 'github.com' && /^\/gsgamebg\/spaceanime-releases\/releases\/download\/[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(u.pathname);
    return storage || github ? u.href : null;
  } catch { return null; }
}
function loadRelease(env) {
  try {
    const raw = env.SPACEANIME_RELEASE_JSON;
    if (typeof raw !== 'string' || raw.length > 8192) return null;
    const value = JSON.parse(raw);
    if (!/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(value.version) || !Number.isSafeInteger(value.versionCode) || value.versionCode < 1 || value.versionCode > 2100000000 || typeof value.notes !== 'string' || value.notes.length > 1000) return null;
    const artifacts = {};
    for (const platform of ['android', 'windows']) {
      const item = value[platform];
      const url = safeArtifact(item?.url, platform);
      if (!url || !/^[a-f0-9]{64}$/i.test(item?.sha256 || '')) return null;
      artifacts[platform] = { url, sha256: item.sha256.toLowerCase() };
    }
    return { version: value.version, code: value.versionCode, notes: value.notes, artifacts };
  } catch { return null; }
}
function manifest(release) {
  const releases = {};
  for (const platform of ['android', 'windows']) {
    const suffix = platform === 'android' ? 'apk' : 'zip';
    releases[platform] = { version: release.version, versionCode: release.code, url: `${ORIGIN}/downloads/${release.code}-${platform}.${suffix}`, sha256: release.artifacts[platform].sha256, notes: release.notes };
  }
  return { schemaVersion: 1, releases };
}
module.exports = { loadRelease, manifest, safeArtifact };

