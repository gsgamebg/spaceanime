package app.anim.sources

import app.anim.player.PlaybackSource
import app.anim.library.EpisodeContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder

actual fun createSourceRepository(): SourceRepository = CachedSources(PortugueseSources(SourceHttp()))

internal class PortugueseSources(private val http: SourceTransport) : SourceRepository {
    override suspend fun discover(sourceId: String): DiscoveryPage {
        val page = http.fetch(base(sourceId) + "/", base(sourceId) + "/")
        val recent = parseRecent(sourceId, page.text)
        if (recent.isEmpty()) throw SourceException("Não foi possível atualizar os lançamentos deste servidor.")
        val featured = (parseSearch(sourceId, page.text, false) + parseSearch(sourceId, page.text, true)).distinctBy { it.id }.take(36)
        val dubbed = try {
            val dubPage = http.fetch(base(sourceId) + (if (sourceId == "anitube") "/?s=dublado" else "/pesquisa/?s=dublado"), base(sourceId) + "/")
            parseSearch(sourceId, dubPage.text, true).take(40)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        return DiscoveryPage(sourceId, recent, (featured + dubbed).distinctBy { it.id }, dubbed)
    }

    internal fun parseRecent(sourceId: String, html: String): List<RecentEpisode> {
        val origin = base(sourceId)
        val doc = Jsoup.parse(html, origin)
        return doc.select(if (sourceId == "anitube") ".epiItem" else ".itemE").mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (!url.isHttps || url.host != origin.toHttpUrlOrNull()!!.host) return@mapNotNull null
            val path = url.encodedPath.trim('/')
            if (!(if (sourceId == "anitube") Regex("(?:video/)?\\d+(?:[bz]\\d*)?") else Regex("video/a/[\\w-]+")).matches(path)) return@mapNotNull null
            val raw = if (sourceId == "anitube") link.attr("title") else card.select(".title_anime").text() + " – " + card.select(".number").text()
            val match = Regex("Epis[oó]dio\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(raw) ?: return@mapNotNull null
            val number = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val dub = raw.contains("Dublado", true) || card.select(".epiCC").text().contains("Dublado", true)
            val title = raw.substring(0, match.range.first).trim().trimEnd('–', '-').trim().replace(Regex("\\s*\\(?Dublado\\)?", RegexOption.IGNORE_CASE), "").trim()
            if (title.isBlank()) return@mapNotNull null
            RecentEpisode(sourceId, title, Episode(path, number, "Episódio ${match.groupValues[1].trimStart('0').ifBlank { "0" }}", url.toString()), poster(card), dub)
        }.distinctBy { it.episode.id }.take(60)
    }

    override suspend fun animeForRecent(item: RecentEpisode): AnimeResult {
        val origin = base(item.sourceId)
        val page = http.fetch(checkedPage(item.episode.url, origin), "$origin/")
        val document = Jsoup.parse(page.text, page.url)
        val links = if (item.sourceId == "anitube") document.select("a.listaPagAni[href]") else document.select("a[href*=/anime/a/]")
        val parent = links.mapNotNull { it.absUrl("href").toHttpUrlOrNull() }.firstOrNull {
            it.isHttps && it.host == origin.toHttpUrlOrNull()!!.host &&
                (if (item.sourceId == "anitube") Regex("(?:video/)?\\d+(?:[bz]\\d*)?").matches(it.encodedPath.trim('/')) else Regex("anime/a/[\\w-]+").matches(it.encodedPath.trim('/')))
        } ?: throw SourceException("Não foi possível encontrar a lista de episódios deste anime.")
        return AnimeResult(item.sourceId, parent.encodedPath.trim('/'), item.title, parent.toString().trimEnd('/'), item.dubbed, item.image)
    }

    private fun poster(card: org.jsoup.nodes.Element): String {
        val image = card.selectFirst("img") ?: return ""
        val raw = listOf("data-src", "data-lazy-src", "src").map { image.absUrl(it) }.firstOrNull { it.toHttpUrlOrNull()?.isHttps == true }.orEmpty()
        return raw.replace(Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$"), "$1")
    }
    private fun base(id: String) = when (id) {
        "anitube" -> "https://www.anitube.zip"
        "animesdigital" -> "https://animesdigital.org"
        else -> throw SourceException("Esta extensão ainda não tem adaptador compatível com o Anim.")
    }

    override suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult> {
        require(query.trim().length in 2..120) { "Digite de 2 a 120 caracteres para buscar." }
        val base = base(sourceId)
        val path = if (sourceId == "anitube") "/?s=" else "/pesquisa/?s="
        val page = http.fetch(base + path + encode(query.trim()), "$base/")
        return parseSearch(sourceId, page.text, dubbed)
    }

    override suspend fun genre(slug: String, page: Int): List<AnimeResult> {
        return genrePageResults(slug, page).items
    }

    override suspend fun genrePageResults(slug: String, page: Int): SourceItemsPage<AnimeResult> {
        require(slug in setOf("acao", "aventura", "comedia", "fantasia", "romance", "drama") && page in 1..40)
        val origin = base("animesdigital")
        val path = "$origin/genero/$slug/" + if (page > 1) "page/$page/" else ""
        val result = http.fetch(path, "$origin/")
        val items = (parseSearch("animesdigital", result.text, false) + parseSearch("animesdigital", result.text, true))
            .distinctBy { "${it.id}|${it.dubbed}" }
        return SourceItemsPage(items, hasNextPage("animesdigital", result.text, page, genreSlug = slug))
    }

    override suspend fun searchAll(sourceId: String, query: String): List<AnimeResult> {
        return searchAllPage(sourceId, query, 1).items
    }

    override suspend fun searchAllPage(sourceId: String, query: String, page: Int): SourceItemsPage<AnimeResult> {
        require(query.trim().length in 2..120)
        require(page in 1..40)
        val origin = base(sourceId)
        val path = if (sourceId == "anitube") "/?s=" else "/pesquisa/?s="
        val pageUrl = origin + path + encode(query.trim()) + if (page == 1) "" else "&paged=$page"
        val response = http.fetch(pageUrl, "$origin/")
        val items = (parseSearch(sourceId, response.text, false) + parseSearch(sourceId, response.text, true))
            .distinctBy { "${it.id}|${it.dubbed}" }
        return SourceItemsPage(items, hasNextPage(sourceId, response.text, page, searchQuery = query.trim()))
    }

    private fun hasNextPage(
        sourceId: String,
        html: String,
        page: Int,
        searchQuery: String? = null,
        genreSlug: String? = null
    ): Boolean {
        val origin = base(sourceId).toHttpUrlOrNull() ?: return false
        val nextPage = page + 1
        return Jsoup.parse(html, origin.toString()).select("a[href]").any { link ->
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@any false
            if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null || url.host != origin.host) return@any false
            val linkedPage = url.queryParameter("paged")?.toIntOrNull()
                ?: Regex("(?:^|/)page/(\\d+)(?:/|$)").find(url.encodedPath)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@any false
            if (linkedPage != nextPage) return@any false
            when {
                searchQuery != null -> url.queryParameter("s") == searchQuery
                genreSlug != null -> url.encodedPath.contains("/genero/$genreSlug/")
                else -> false
            }
        }
    }

    internal fun parseSearch(sourceId: String, html: String, dubbed: Boolean): List<AnimeResult> {
        val base = base(sourceId)
        val document = Jsoup.parse(html, base)
        val cards = document.select(if (sourceId == "anitube") ".aniItem" else ".itemA")
        return cards.mapNotNull { card ->
            val link = card.selectFirst("a[href]") ?: return@mapNotNull null
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.host != base.toHttpUrlOrNull()!!.host) return@mapNotNull null
            val path = url.encodedPath.trim('/')
            val valid = if (sourceId == "anitube") Regex("(?:video/)?\\d+(?:[bz]\\d*)?").matches(path) else Regex("anime/a/[\\w-]+").matches(path)
            if (!valid) return@mapNotNull null
            val shown = if (sourceId == "anitube") link.attr("title") else card.selectFirst(".title_anime")?.text().orEmpty()
            if (shown.isBlank()) return@mapNotNull null
            val isDub = Regex("dublado", RegexOption.IGNORE_CASE).containsMatchIn(shown + " " + link.attr("title") + " " + link.attr("aria-label") + " " + card.select("img").attr("alt") + " " + card.select(".aniCC").text() + " " + path)
            if (isDub != dubbed) return@mapNotNull null
            val title = shown.replace(Regex("\\s+[–-]\\s+Todos (?:os )?Epis[oó]dios\\s*$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\((?:Dublado|Legendado)\\)|\\s+Dublado\\b", RegexOption.IGNORE_CASE), "").trim()
            AnimeResult(sourceId, path, title, "$base/$path", dubbed, poster(card))
        }.distinctBy { it.id }
    }

    internal fun parseSynopsis(sourceId: String, html: String, url: String): String? {
        val node = Jsoup.parse(html, url).selectFirst(if (sourceId == "anitube") "#sinopse2" else ".sinopse") ?: return null
        if (sourceId == "animesdigital") node.select("h1,h2,h3").remove()
        val lines = node.html().replace(Regex("(?i)<br\\s*/?>"), "|||" ).split("|||")
            .map { Jsoup.parse(it).text().trim() }.filter { it.isNotBlank() }
        val description = if (sourceId == "anitube" && lines.size > 1) lines.drop(1).joinToString(" ") else lines.joinToString(" ")
        return description.takeIf { it.length >= 20 }?.take(2000)
    }
    override suspend fun synopsis(anime: AnimeResult): String? {
        val origin = base(anime.sourceId)
        val page = http.fetch(checkedPage(anime.url, origin), "$origin/")
        return parseSynopsis(anime.sourceId, page.text, page.url)
    }

    override suspend fun episodes(anime: AnimeResult): List<Episode> {
        val base = base(anime.sourceId)
        val first = http.fetch(checkedPage(anime.url, base), "$base/")
        val pages = mutableListOf(first)
        if (anime.sourceId == "animesdigital") {
            val document = Jsoup.parse(first.text, first.url)
            val pageLinks = document.select(".content-pagination a[href]").mapNotNull { link ->
                val address = link.absUrl("href")
                val match = Regex("^(https://[^?#]+/)page/(\\d+)/?$").matchEntire(address) ?: return@mapNotNull null
                if (address.toHttpUrlOrNull()?.host != base.toHttpUrlOrNull()?.host) return@mapNotNull null
                match.groupValues[1] to match.groupValues[2].toInt()
            }
            val last = pageLinks.maxByOrNull { it.second }
            if (last != null) {
                if (last.second > 40) throw SourceException("Esta série excede o limite atual de 40 páginas de episódios.")
                for (batch in (2..last.second).chunked(4)) {
                    pages += coroutineScope { batch.map { n -> async { http.fetch(last.first + "page/$n/", first.url) } }.awaitAll() }
                }
            }
        }
        val episodes = pages.flatMap { parseEpisodes(anime.sourceId, it.text, it.url) }.distinctBy { it.id }.sortedBy { it.number }
        if (episodes.isEmpty()) throw SourceException("A fonte não informou episódios para este título.")
        return episodes
    }

    internal fun parseEpisodes(sourceId: String, html: String, pageUrl: String): List<Episode> {
        val document = Jsoup.parse(html, pageUrl)
        val links = if (sourceId == "anitube") document.select("[class*=pagAniListaContainer] a[href]") else document.select("a.b_flex[href]")
        val base = base(sourceId)
        return links.mapNotNull { link ->
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.host != base.toHttpUrlOrNull()!!.host) return@mapNotNull null
            val path = url.encodedPath.trim('/')
            val valid = if (sourceId == "anitube") Regex("(?:video/)?\\d+(?:[bz]\\d*)?").matches(path) else Regex("video/a/[\\w-]+").matches(path)
            if (!valid) return@mapNotNull null
            val title = if (sourceId == "anitube") link.text() else link.selectFirst(".title_anime")?.text().orEmpty()
            val number = Regex("Epis[oó]dio\\s+(\\d+(?:[.\\-]\\d+)?)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.replace('-', '.')?.toDoubleOrNull() ?: return@mapNotNull null
            Episode(path, number, "Episódio ${if (number % 1 == 0.0) number.toInt() else number}", "$base/$path/")
        }.distinctBy { it.id }.sortedBy { it.number }
    }

    override suspend fun streams(anime: AnimeResult, episode: Episode): List<StreamOption> {
        val base = base(anime.sourceId)
        val watch = http.fetch(checkedPage(episode.url, base), "$base/")
        val document = Jsoup.parse(watch.text, watch.url)
        val frames = if (anime.sourceId == "anitube") document.select("#blog2 iframe[src], #blog1 iframe[src]").map { it.absUrl("src") }
            else document.select("iframe[src]").map { it.absUrl("src") }
        val results = mutableListOf<StreamOption>()
        val failures = mutableListOf<String>()
        for (frame in frames.distinct().take(6)) {
            val address = frame.toHttpUrlOrNull() ?: continue
            if (!address.isHttps) continue
            val hls = address.host == "api.anivideo.net" &&
                (address.encodedPath.endsWith("videohls.php") || Regex("/cdn/v\\d+\\.[\\w.-]+").matches(address.encodedPath))
            val blogger = address.host == "www.blogger.com" && address.encodedPath == "/video.g"
            val holder = address.host == base.toHttpUrlOrNull()!!.host && address.encodedPath.endsWith("/bg.mp4")
            if (!hls && !blogger && !holder) continue
            try {
                if (hls) {
                    val direct = address.queryParameter("d")?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
                        ?: extractFile(http.fetch(frame, watch.url).text)
                        ?: throw SourceException("O servidor HLS não informou um vídeo.")
                    val referrer = "${address.scheme}://${address.host}/"
                    results += StreamOption("Servidor principal · automático", PlaybackSource("${anime.title} · ${episode.title}", direct, headers = mapOf("Referer" to referrer)))
                } else {
                    val bloggerUrl = if (blogger) frame else {
                        val page = http.fetch(frame, watch.url)
                        Jsoup.parse(page.text, page.url).select("iframe[src]").map { it.absUrl("src") }
                            .firstOrNull { it.toHttpUrlOrNull()?.let { u -> u.isHttps && u.host == "www.blogger.com" && u.encodedPath == "/video.g" } == true }
                            ?: throw SourceException("O servidor alternativo não informou um vídeo Blogger.")
                    }
                    for ((url, quality) in bloggerStreams(bloggerUrl)) {
                        results += StreamOption("Blogger · $quality", PlaybackSource("${anime.title} · ${episode.title}", url, headers = mapOf("Referer" to bloggerUrl, "User-Agent" to "Mozilla/5.0 Anim/0.3")))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failures += failure.message ?: "Servidor indisponível." }
        }
        if (results.isEmpty()) throw SourceException(failures.firstOrNull() ?: "Nenhum servidor compatível encontrado neste episódio.")
        return results.distinctBy { it.source.url }.map { it.copy(source = it.source.copy(context = EpisodeContext(anime, episode))) }
    }

    internal fun extractFile(html: String): String? {
        val match = Regex("\\bfile\\s*:\\s*['\"]([^'\"]+)['\"]").find(html) ?: return null
        val decoded = unescape(match.groupValues[1])
        return decoded.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
    }

    private suspend fun bloggerStreams(url: String): List<Pair<String, String>> {
        val page = http.fetch(url, "https://www.blogger.com/")
        val found = mutableListOf<Pair<String, String>>()
        if (found.isEmpty()) {
            val token = url.toHttpUrlOrNull()?.queryParameter("token") ?: throw SourceException("Vídeo Blogger sem identificador.")
            fun field(name: String) = Regex("\"$name\":\"([^\"]+)\"").find(page.text)?.groupValues?.get(1)
                ?: throw SourceException("O player Blogger mudou de formato.")
            val session = field("FdrFJe")
            val build = field("cfb2h")
            val arguments = buildJsonArray { add(token); add(""); add(0) }.toString()
            val request = buildJsonArray { add(buildJsonArray { add(buildJsonArray { add("WcwnYd"); add(arguments); add(JsonNull); add("generic") }) }) }.toString()
            val response = http.fetch("https://www.blogger.com/_/BloggerVideoPlayerUi/data/batchexecute?rpcids=WcwnYd&source-path=%2Fvideo.g&f.sid=${encode(session)}&bl=${encode(build)}&hl=en-US&rt=c", "https://www.blogger.com/", "f.req=${encode(request)}&")
            for (line in response.text.lineSequence().filter { it.startsWith("[[\"wrb.fr\"") }) {
                val streams = runCatching { Json.parseToJsonElement(Json.parseToJsonElement(line).jsonArray[0].jsonArray[2].jsonPrimitive.content).jsonArray[2].jsonArray }.getOrNull() ?: continue
                for (stream in streams) {
                    val entry = runCatching { stream.jsonArray }.getOrNull() ?: continue
                    val file = entry.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                    val code = runCatching { entry[1].jsonArray[0].jsonPrimitive.int }.getOrNull() ?: continue
                    val quality = quality(code) ?: continue
                    if (file.toHttpUrlOrNull()?.isHttps == true) found += file to quality
                }
            }
        }
        if (found.isEmpty()) throw SourceException("O vídeo Blogger não está disponível.")
        return found.distinctBy { it.second }.sortedByDescending { it.second.removeSuffix("p").toInt() }
    }

    internal fun parseBloggerStreams(html: String): List<Pair<String, String>> =
        Regex("\"play_url\":\"((?:\\\\.|[^\"\\\\])*)\"[^}]*?\"format_id\":(\\d+)").findAll(html).mapNotNull {
            val quality = quality(it.groupValues[2].toInt()) ?: return@mapNotNull null
            val url = unescape(it.groupValues[1])
            if (url.toHttpUrlOrNull()?.isHttps == true) url to quality else null
        }.toList()

    private fun quality(code: Int) = mapOf(18 to "360p", 22 to "720p", 37 to "1080p", 59 to "480p")[code]
    private fun unescape(text: String) = Parser.unescapeEntities(text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }.replace("\\/", "/"), false)
    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    private fun checkedPage(url: String, base: String): String {
        val parsed = url.toHttpUrlOrNull() ?: throw SourceException("Endereço inválido na fonte.")
        if (!parsed.isHttps || parsed.host != base.toHttpUrlOrNull()!!.host) throw SourceException("O endereço não pertence à fonte selecionada.")
        return parsed.toString()
    }
}


