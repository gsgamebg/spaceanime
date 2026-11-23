'use strict';
const net = require('node:net');

const FIELDS = ['app_code', 'app_version', 'device_model', 'install_id', 'locale', 'os_version', 'platform', 'schemaVersion', 'time_zone'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function text(value, max, pattern = /^[\p{L}\p{N} ._()\/+:,-]*$/u) {
  return typeof value === 'string' && value.length <= max && pattern.test(value);
}

/** Strict allow-list: unknown keys or malformed values reject the whole event. */
function parseTelemetry(raw) {
  let value;
  try { value = JSON.parse(raw); } catch { return null; }
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).sort().join(',') !== FIELDS.join(',')) return null;
  if (value.schemaVersion !== 1 || !UUID.test(value.install_id) ||
      !['android', 'windows'].includes(value.platform) ||
      !/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(value.app_version) ||
      !Number.isSafeInteger(value.app_code) || value.app_code < 1 || value.app_code > 2100000000 ||
      !text(value.os_version, 64) || !text(value.device_model, 96) ||
      !text(value.locale, 16, /^[A-Za-z0-9_-]*$/) || !text(value.time_zone, 64, /^[A-Za-z0-9_+\/-]*$/)) return null;
  const { schemaVersion, ...event } = value;
  return { ...event, install_id: value.install_id.toLowerCase() };
}

/** Render sets X-Forwarded-For; the left-most entry is the client. Falls back to the socket address. */
function clientIp(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  const candidate = forwarded || req.socket?.remoteAddress || '';
  const normalized = candidate.startsWith('::ffff:') ? candidate.slice(7) : candidate;
  return net.isIP(normalized) ? normalized : null;
}

function createTelemetryStore(env = process.env, fetchImpl = fetch) {
  return {
    async record(event, ip) {
      const key = env.SUPABASE_SECRET_KEY || '';
      let base;
      try { base = new URL(env.SUPABASE_URL); } catch { throw new Error('telemetry_unavailable'); }
      if (base.protocol !== 'https:' || !base.hostname.endsWith('.supabase.co') || !/^sb_secret_[A-Za-z0-9_-]+$/.test(key)) {
        throw new Error('telemetry_unavailable');
      }
      const response = await fetchImpl(new URL('/rest/v1/rpc/spaceanime_record_telemetry', base), {
        method: 'POST',
        headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_event: event, p_ip: ip }),
        signal: AbortSignal.timeout(8000),
        redirect: 'error',
      });
      if (!response.ok) throw new Error('telemetry_unavailable');
    },
  };
}

module.exports = { parseTelemetry, clientIp, createTelemetryStore };
