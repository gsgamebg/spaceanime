package app.anim.sources

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** AniList is metadata only; exact unambiguous matches avoid merging unrelated works. */
internal class AniListAliases {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val cache = LinkedHashMap<String, Pair<Long, AniListMetadata>>()
    suspend fun lookup(query: String): List<String> = lookupMetadata(query).names
    suspend fun cover(query: String): String? = lookupMetadata(query).cover
    private suspend fun lookupMetadata(query: String): AniListMetadata {
        val value = query.trim().take(120)
        if (value.length < 2) return AniListMetadata()
        val key = normalizeTitle(value)
        synchronized(cache) { cache[key]?.takeIf { it.first > System.nanoTime() }?.let { return it.second } }
        val body = buildJsonObject {
            put("query", "query (\$search: String!) { Page(page: 1, perPage: 8) { media(search: \$search, type: ANIME, isAdult: false) { id title { romaji english } synonyms coverImage { extraLarge } } } }")
            put("variables", buildJsonObject { put("search", value) })
        }.toString().toRequestBody("application/json".toMediaType())
        val result = suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url("https://graphql.anilist.co/").header("Accept", "application/json").post(body).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use { r ->
                            if (!r.isSuccessful) throw IOException("Metadata unavailable")
                            val data = requireNotNull(r.body).byteStream().use { stream ->
                                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                                while (true) { val n = stream.read(buffer); if (n < 0) break; if (output.size() + n > 131072) throw IOException("Metadata too large"); output.write(buffer, 0, n) }
                                output.toString(Charsets.UTF_8.name())
                            }
                            parseAniListMetadata(data, value)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
        synchronized(cache) { cache[key] = (System.nanoTime() + TimeUnit.HOURS.toNanos(1)) to result; if (cache.size > 100) cache.remove(cache.keys.first()) }
        return result
    }
}
internal fun normalizeTitle(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex(" +"), " ")
internal data class AniListMetadata(val names: List<String> = emptyList(), val cover: String? = null)
internal fun parseAniListAliases(text: String, query: String): List<String> = parseAniListMetadata(text, query).names
internal fun parseAniListMetadata(text: String, query: String): AniListMetadata {
    val items = Json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray ?: return AniListMetadata()
    val candidates = items.mapNotNull { element ->
        val obj = element.jsonObject
        val title = obj["title"]?.jsonObject ?: return@mapNotNull null
        val names = listOfNotNull(title["romaji"]?.jsonPrimitive?.contentOrNull, title["english"]?.jsonPrimitive?.contentOrNull) +
            obj["synonyms"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val cover = obj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull
        AniListMetadata(names.filter { it.length in 2..120 && it.any { ch -> ch.isLetter() } }, cover)
    }.filter { item -> item.names.any { normalizeTitle(it) == normalizeTitle(query) } }
    val match = candidates.singleOrNull() ?: return AniListMetadata()
    return match.copy(names = match.names.distinctBy(::normalizeTitle).take(8))
}
