package app.anim.ads

import app.anim.auth.BACKEND_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fetches the ad switches: schema 2 first, schema 1 as fallback while the backend is older. */
internal class AdsConfigClient(
    private val backend: String = BACKEND_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    /** Returns the raw JSON (for caching) and its parsed value, or null. */
    suspend fun fetch(): Pair<String, AdsConfiguration>? = withContext(Dispatchers.IO) {
        for (path in listOf("/api/v2/app-config", "/api/app-config")) {
            val text = get(path) ?: continue
            val parsed = parseAdsConfiguration(text) ?: continue
            return@withContext text to parsed
        }
        null
    }

    /**
     * Tells the backend how an ad request ended so it shows in the server log (`[ads] event ...`).
     * Best effort: carries no user or device data, only provider, format, result and app version.
     */
    suspend fun report(provider: String, format: String, event: String, reason: String? = null) = withContext(Dispatchers.IO) {
        val url = (backend + "/api/v1/ads/events").toHttpUrlOrNull() ?: return@withContext
        if (!url.isHttps || url.host != "spaceanime.onrender.com") return@withContext
        val body = buildJsonObject {
            put("schemaVersion", 1)
            put("provider", provider)
            put("format", format)
            put("event", event)
            put("app_version", app.anim.update.APP_VERSION)
            reason?.let { put("reason", it.filter { c -> c.isLetterOrDigit() || c in " _.:-" }.take(80).ifBlank { "unknown" }) }
        }.toString().toRequestBody("application/json".toMediaType())
        try { client.newCall(Request.Builder().url(url).post(body).build()).execute().close() } catch (_: IOException) {}
    }

    private fun get(path: String): String? {
        val url = (backend + path).toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.host != "spaceanime.onrender.com" || url.encodedPath != path ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null
        ) return null
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_CONFIG_BYTES) return null
                body.byteStream().use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_CONFIG_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            }
        } catch (_: IOException) {
            null
        }
    }
}

private val V1_KEYS = setOf("enabled", "homeBanner", "catalogBanner")
private val V2_KEYS = V1_KEYS + setOf("provider", "interstitialEnabled", "interstitialEveryEpisodes", "rewardedEnabled", "rewardedPassMinutes")

/** Strict parser for schema 1 and 2: unknown keys or wrong types make the whole config unavailable. */
internal fun parseAdsConfiguration(text: String): AdsConfiguration? {
    if (text.toByteArray(Charsets.UTF_8).size > MAX_CONFIG_BYTES) return null
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject ?: return null
    } catch (_: SerializationException) {
        return null
    }
    if (root.keys != setOf("schemaVersion", "ads")) return null
    val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return null
    val ads = root["ads"] as? JsonObject ?: return null
    fun bool(key: String) = (ads[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    fun int(key: String, range: IntRange) = (ads[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it in range }
    val base = AdsConfiguration(
        enabled = bool("enabled") ?: return null,
        homeBanner = bool("homeBanner") ?: return null,
        catalogBanner = bool("catalogBanner") ?: return null,
    )
    return when (schemaVersion) {
        1 -> base.takeIf { ads.keys == V1_KEYS }
        2 -> {
            if (ads.keys != V2_KEYS) return null
            val provider = when ((ads["provider"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                "admob" -> AdProvider.AdMob
                "startio" -> AdProvider.StartIo
                else -> return null
            }
            base.copy(
                provider = provider,
                interstitialEnabled = bool("interstitialEnabled") ?: return null,
                interstitialEveryEpisodes = int("interstitialEveryEpisodes", 1..20) ?: return null,
                rewardedEnabled = bool("rewardedEnabled") ?: return null,
                rewardedPassMinutes = int("rewardedPassMinutes", 15..1440) ?: return null,
            )
        }
        else -> null
    }
}

private const val MAX_CONFIG_BYTES = 4 * 1024
