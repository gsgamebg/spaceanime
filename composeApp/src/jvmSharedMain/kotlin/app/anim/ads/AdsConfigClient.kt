package app.anim.ads

import app.anim.auth.BACKEND_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class AdsConfigClient(
    private val endpoint: String = "$BACKEND_URL/api/app-config",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun fetch(): AdsConfiguration? = withContext(Dispatchers.IO) {
        val url = endpoint.toHttpUrlOrNull() ?: return@withContext null
        if (!url.isHttps || url.host != "spaceanime.onrender.com" ||
            url.encodedPath != "/api/app-config" || url.username.isNotEmpty() ||
            url.password.isNotEmpty() || url.query != null || url.fragment != null
        ) return@withContext null
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (body.contentLength() > MAX_CONFIG_BYTES) return@withContext null
                val text = body.byteStream().use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_CONFIG_BYTES) return@withContext null
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                parseAdsConfiguration(text)
            }
        } catch (_: IOException) {
            null
        }
    }
}

internal fun parseAdsConfiguration(text: String): AdsConfiguration? {
    if (text.toByteArray(Charsets.UTF_8).size > MAX_CONFIG_BYTES) return null
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject ?: return null
    } catch (_: SerializationException) {
        return null
    }
    if (root.keys != setOf("schemaVersion", "ads")) return null
    val schemaVersion = root["schemaVersion"] as? JsonPrimitive ?: return null
    if (schemaVersion.isString || schemaVersion.intOrNull != 1) return null
    val ads = root["ads"] as? JsonObject ?: return null
    if (ads.keys != setOf("enabled", "homeBanner", "catalogBanner")) return null
    val enabled = (ads["enabled"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: return null
    val home = (ads["homeBanner"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: return null
    val catalog = (ads["catalogBanner"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: return null
    return AdsConfiguration(enabled = enabled, homeBanner = home, catalogBanner = catalog)
}

private const val MAX_CONFIG_BYTES = 4 * 1024
