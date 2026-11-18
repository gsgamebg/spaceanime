package app.anim.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class TrendingTitle(val names: List<String>)
internal class AniListTrending {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    suspend fun fetch(): List<TrendingTitle> = suspendCancellableCoroutine { cont ->
        val body = buildJsonObject { put("query", "{ Page(page: 1, perPage: 24) { media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { id title { romaji english } } } }") }.toString().toRequestBody("application/json".toMediaType())
        val call = client.newCall(Request.Builder().url("https://graphql.anilist.co/").post(body).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { r ->
                        if (!r.isSuccessful) throw IOException("AniList indisponível")
                        val bytes = requireNotNull(r.body).byteStream().use { input ->
                            val out = java.io.ByteArrayOutputStream(); val b = ByteArray(4096)
                            while (true) { val n = input.read(b); if (n < 0) break; if (out.size() + n > 65536) throw IOException("Resposta grande"); out.write(b, 0, n) }; out.toByteArray()
                        }
                        parseTrending(bytes.toString(Charsets.UTF_8))
                    }
                    if (cont.isActive) cont.resume(result)
                } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
            }
        })
    }
}
internal fun parseTrending(json: String): List<TrendingTitle> = Json.parseToJsonElement(json).jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { item ->
    val t = item.jsonObject["title"]?.jsonObject ?: return@mapNotNull null
    val names = listOfNotNull(t["romaji"]?.jsonPrimitive?.contentOrNull, t["english"]?.jsonPrimitive?.contentOrNull).filter { it.length in 2..120 }.distinctBy(::normalizeTitle)
    if (names.isEmpty()) null else TrendingTitle(names)
}.take(24)
internal suspend fun matchTrending(titles: List<TrendingTitle>, repo: SourceRepository): List<AnimeResult> = coroutineScope {
    val limit = Semaphore(6)
    titles.map { item -> async {
      limit.withPermit {
        val accepted = item.names.map(::normalizeTitle).toSet()
        listOf("anitube", "animesdigital").map { source -> async {
            try { withTimeout(8_000) {
                item.names.firstNotNullOfOrNull { name ->
                    repo.searchAll(source, name).firstOrNull { normalizeTitle(it.title) in accepted && !it.dubbed }
                }
            } }
            catch (_: TimeoutCancellationException) { null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } }.awaitAll().firstOrNull { it != null }
      }
    } }.awaitAll().filterNotNull().distinctBy { normalizeTitle(it.title) }.take(10)
}
