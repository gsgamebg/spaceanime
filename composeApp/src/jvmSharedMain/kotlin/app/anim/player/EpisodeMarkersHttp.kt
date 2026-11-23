package app.anim.player

import app.anim.auth.SUPABASE_PUBLISHABLE_KEY
import app.anim.auth.SUPABASE_URL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createEpisodeMarkersClient(): EpisodeMarkersClient = EpisodeMarkersHttp()

internal class EpisodeMarkersHttp(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : EpisodeMarkersClient {
    override suspend fun load(key: EpisodeMarkerKey): EpisodeMarkers? {
        if (!key.isValid()) throw EpisodeMarkersLoadException()
        val endpoint = (SUPABASE_URL + "/rest/v1/rpc/spaceanime_public_episode_markers")
            .toHttpUrlOrNull()
            ?: throw EpisodeMarkersLoadException()
        if (!endpoint.isHttps || endpoint.username.isNotEmpty() || endpoint.password.isNotEmpty()) {
            throw EpisodeMarkersLoadException()
        }
        val payload = buildJsonObject {
            put("p_source_id", key.sourceId)
            put("p_content_id", key.contentId)
            put("p_episode_id", key.episodeId)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Accept", "application/json")
            .post(payload)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(EpisodeMarkersLoadException(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val markers = response.use {
                            if (!it.isSuccessful) throw IOException("Não foi possível carregar os marcadores do episódio (HTTP ${it.code}).")
                            val bytes = requireNotNull(it.body) { "Resposta de marcadores vazia." }
                                .byteStream().use { stream ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(2048)
                                    while (true) {
                                        val count = stream.read(buffer)
                                        if (count < 0) break
                                        if (output.size() + count > MAX_MARKER_RESPONSE_BYTES) {
                                            throw IOException("Resposta de marcadores muito grande.")
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                            parseEpisodeMarkers(bytes.toString(Charsets.UTF_8), key)
                        }
                        if (continuation.isActive) continuation.resume(markers)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(EpisodeMarkersLoadException(e))
                    }
                }
            })
        }
    }
}

internal fun parseEpisodeMarkers(text: String, expected: EpisodeMarkerKey): EpisodeMarkers? {
    require(expected.isValid()) { "Chave de episódio inválida." }
    require(text.toByteArray(Charsets.UTF_8).size <= MAX_MARKER_RESPONSE_BYTES) { "Resposta de marcadores muito grande." }
    val rootElement = Json.parseToJsonElement(text)
    if (rootElement is JsonNull) return null
    val root = rootElement.jsonObject
    val fields = setOf(
        "source_id",
        "content_id",
        "episode_id",
        "intro_start_ms",
        "intro_end_ms",
        "outro_start_ms",
        "outro_end_ms",
    )
    require(root.keys == fields) { "Resposta de marcadores incompleta." }
    require(root.getValue("source_id").jsonPrimitive.content == expected.sourceId)
    require(root.getValue("content_id").jsonPrimitive.content == expected.contentId)
    require(root.getValue("episode_id").jsonPrimitive.content == expected.episodeId)

    fun interval(name: String): Segment? {
        val start = root.getValue("${name}_start_ms")
        val end = root.getValue("${name}_end_ms")
        if (start is JsonNull && end is JsonNull) return null
        require(start !is JsonNull && end !is JsonNull) { "Intervalo $name incompleto." }
        val startMs = start.jsonPrimitive.longOrNull ?: throw IllegalArgumentException("Início de $name inválido.")
        val endMs = end.jsonPrimitive.longOrNull ?: throw IllegalArgumentException("Fim de $name inválido.")
        return Segment(startMs, endMs)
    }

    return EpisodeMarkers(intro = interval("intro"), outro = interval("outro"))
}

private const val MAX_MARKER_RESPONSE_BYTES = 16 * 1024
