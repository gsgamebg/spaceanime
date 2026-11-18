'use strict';

function validSupabaseUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' &&
      url.hostname.endsWith('.supabase.co') &&
      !url.username && !url.password && !url.hash &&
      (!url.port || url.port === '443') &&
      url.pathname === '/' && !url.search;
  } catch {
    return false;
  }
}

function createNotificationStore(env = process.env, fetchImpl = fetch) {
  async function rpc(name, payload, parseJson = false) {
    const key = env.SUPABASE_SECRET_KEY || '';
    if (!validSupabaseUrl(env.SUPABASE_URL) || !/^sb_secret_[A-Za-z0-9_-]+$/.test(key)) {
      throw new Error('notification_store_unavailable');
    }
    const response = await fetchImpl(new URL(`/rest/v1/rpc/${name}`, env.SUPABASE_URL), {
      method: 'POST',
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(8000),
      redirect: 'error',
    });
    if (!response.ok) throw new Error('notification_store_unavailable');
    if (!parseJson) return true;
    const text = await response.text();
    if (text.length > 1024 * 1024) throw new Error('notification_store_invalid');
    try { return JSON.parse(text); }
    catch { throw new Error('notification_store_invalid'); }
  }
  async function rpcJson(name, payload) {
    return rpc(name, payload, true);
  }

  return {
    saveDevice: (userId, device) => rpc('spaceanime_notification_upsert_device', {
      p_user_id: userId,
      p_device_id: device.device_id,
      p_push_token: device.push_token,
      p_preferences: device.preferences,
      p_time_zone: device.time_zone,
      p_digest_time: device.digest_time,
    }),
    updatePreferences: (userId, deviceId, preferences, timeZone, digestTime) =>
      rpc('spaceanime_notification_update_preferences', {
        p_user_id: userId,
        p_device_id: deviceId,
        p_preferences: preferences,
        p_time_zone: timeZone,
        p_digest_time: digestTime,
      }),
    deleteDevice: (userId, deviceId) => rpc('spaceanime_notification_delete_device', {
      p_user_id: userId,
      p_device_id: deviceId,
    }),
    syncSubscriptions: (userId, subscriptions) => rpc('spaceanime_notification_sync_subscriptions', {
      p_user_id: userId,
      p_subscriptions: subscriptions,
    }),
    listSubscriptions: async (mediaType, sourceId, after, limit) => {
      const rows = await rpcJson('spaceanime_notification_list_subscriptions', {
        p_media_type: mediaType,
        p_source_id: sourceId,
        p_after: after,
        p_limit: limit,
      });
      if (!Array.isArray(rows) || rows.some(row =>
        !row || typeof row.content_id !== 'string' || typeof row.audio_variant !== 'string')) {
        throw new Error('notification_store_invalid');
      }
      return rows;
    },
    claimScan: async (sourceId, now) => {
      const claimed = await rpcJson('spaceanime_notification_claim_scan', {
        p_source_id: sourceId,
        p_now: now.toISOString(),
      });
      if (typeof claimed !== 'boolean') throw new Error('notification_store_invalid');
      return claimed;
    },
    recordRelease: async (release) => {
      const releaseId = await rpcJson('spaceanime_notification_record_release', {
        p_release: release,
      });
      if (!Number.isSafeInteger(releaseId) || releaseId < 1) throw new Error('notification_store_invalid');
      return releaseId;
    },
    claimDueDigests: async (now, limit) => {
      const rows = await rpcJson('spaceanime_notification_claim_due_digests', {
        p_now: now.toISOString(),
        p_limit: limit,
      });
      if (!Array.isArray(rows)) throw new Error('notification_store_invalid');
      return rows;
    },
    finishDigest: (userId, localDate, result) => rpc('spaceanime_notification_finish_digest', {
      p_user_id: userId,
      p_local_date: localDate,
      p_result: result,
    }),
    removeInvalidTokens: (tokens) => rpc('spaceanime_notification_remove_invalid_tokens', {
      p_tokens: tokens,
    }),
  };
}

module.exports = { createNotificationStore };
