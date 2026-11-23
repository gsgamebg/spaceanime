package app.anim.update

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Legacy Render manifest client, kept for its validation tests; the app uses GitHubReleaseUpdates.
internal class HttpUpdates(private val endpoint: String, private val platform: String) : UpdateClient {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    override suspend fun check(): AppRelease? {
        require(endpoint.isNotBlank()) { "O canal de atualizações ainda não foi configurado." }
        val address = endpoint.toHttpUrlOrNull()
        require(address != null && address.isHttps && address.username.isEmpty() && address.password.isEmpty()) { "Canal de atualização inválido." }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(address).header("Accept", "application/json").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Não foi possível consultar as atualizações.")) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val release = response.use {
                            require(it.isSuccessful) { "Canal indisponível (HTTP ${it.code})." }
                            val body = requireNotNull(it.body)
                            val bytes = body.byteStream().use { stream ->
                                val out = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    require(out.size() + count <= 65536) { "Manifesto de atualização muito grande." }
                                    out.write(buffer, 0, count)
                                }
                                out.toByteArray()
                            }
                            parseRelease(bytes.toString(Charsets.UTF_8), endpoint, platform)
                        }
                        if (continuation.isActive) continuation.resume(release)
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("A atualização não pôde ser validada. Tente novamente mais tarde."))
                    }
                }
            })
        }
    }
}

internal fun parseRelease(text: String, endpoint: String, platform: String): AppRelease? {
    val root = Json.parseToJsonElement(text).jsonObject
    require(root.getValue("schemaVersion").jsonPrimitive.int == 1)
    val item = root.getValue("releases").jsonObject.getValue(platform).jsonObject
    val code = item.getValue("versionCode").jsonPrimitive.int
    require(code > 0)
    if (code <= APP_VERSION_CODE) return null
    val version = item.getValue("version").jsonPrimitive.content
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version) && version.length <= 32)
    val url = item.getValue("url").jsonPrimitive.content.toHttpUrlOrNull() ?: error("Invalid URL")
    val origin = endpoint.toHttpUrlOrNull() ?: error("Invalid origin")
    // Downloads must belong to the configured HTTPS distribution origin, never arbitrary hosts.
    require(url.isHttps && url.host == origin.host && url.port == origin.port && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
    require(url.encodedPath.endsWith(if (platform == "android") ".apk" else ".zip"))
    val hash = item.getValue("sha256").jsonPrimitive.content
    require(Regex("[a-fA-F0-9]{64}").matches(hash))
    val notes = item.getValue("notes").jsonPrimitive.content
    require(notes.length <= 4000)
    return AppRelease(version, code, url.toString(), hash.lowercase(), notes)
}
