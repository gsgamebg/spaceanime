package app.anim.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class PortugueseSourcesTest {
    @Test fun liveBloggerMediaRespondsWithVideo() = runBlocking {
        assumeTrue(System.getenv("ANIM_BLOGGER_SMOKE") == "1")
        val repository = PortugueseSources(SourceHttp())
        val client = okhttp3.OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val anime = repository.search("anitube", "Overlord IV", false).first { it.title == "Overlord IV" }
        val options = repository.streams(anime, repository.episodes(anime).first()).filter { it.label.startsWith("Blogger") }
        assertTrue(options.isNotEmpty())
        for (option in options) {
            val request = okhttp3.Request.Builder().url(option.source.url).header("Range", "bytes=0-1023")
            option.source.headers.forEach { (key, value) -> request.header(key, value) }
            client.newCall(request.build()).execute().use { response ->
                println("Blogger probe ${option.label}: HTTP ${response.code}, type=${response.header("Content-Type")}, host=${response.request.url.host}")
                assertTrue(response.isSuccessful, "Blogger HTTP ${response.code}")
                assertTrue(response.header("Content-Type").orEmpty().startsWith("video/"))
                assertTrue(response.body!!.byteStream().read() >= 0)
            }
        }
    }
    private val noNetwork = object : SourceTransport {
        override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage = error("Unexpected network call")
    }
    private val parser = PortugueseSources(noNetwork)

    @Test fun digitalDetectsDubFromLinkTitle() {
        val html = """<div class="itemA"><a href="https://animesdigital.org/anime/a/naruto-shippuden-dub" title="Assistir Naruto Shippuden Dublado"><span class="title_anime">Naruto Shippuden</span></a></div>"""
        assertTrue(parser.parseSearch("animesdigital", html, true).single().dubbed)
        assertTrue(parser.parseSearch("animesdigital", html, false).isEmpty())
    }
    @Test fun synopsisExtractsOnlyActualDescription() {
        val anitube = "<div id='sinopse2'>Nome da série<br/>Uma história em português sobre uma grande aventura.</div>"
        val digital = "<div class='sinopse'><h2>Nome</h2>Uma história em português sobre uma grande aventura.</div>"
        assertEquals("Uma história em português sobre uma grande aventura.", parser.parseSynopsis("anitube", anitube, "https://www.anitube.zip/"))
        assertEquals("Uma história em português sobre uma grande aventura.", parser.parseSynopsis("animesdigital", digital, "https://animesdigital.org/"))
        assertNull(parser.parseSynopsis("anitube", "<div>Sem sinopse</div>", "https://www.anitube.zip/"))
    }
    @Test fun catalogKeepsAllEntriesAndSeparatesNativeSupport() {
        assertEquals(38, extensionCatalog.size)
        assertEquals(38, extensionCatalog.map { it.id }.distinct().size)
        assertEquals(setOf("anitube", "animesdigital"), extensionCatalog.filter { it.support == ExtensionSupport.NATIVE }.map { it.id }.toSet())
        assertEquals(5, extensionCatalog.count { it.support == ExtensionSupport.EXTERNAL_MANIFEST })
    }

    @Test fun anitubeDecodesEntitiesSeparatesDubAndRejectsOffsiteCards() {
        val html = """
            <div class='aniItem'><a href='https://www.anitube.zip/video/12/' title='Teste &amp; Lua – Todos os Episódios'><div class='aniCC'>Legendado</div></a></div>
            <div class='aniItem'><a href='https://www.anitube.zip/video/13/' title='Teste &amp; Lua (Dublado) – Todos os Episódios'><div class='aniCC'>Dublado</div></a></div>
            <div class='aniItem'><a href='https://wrong.example/video/14/' title='Publicidade'><div class='aniCC'>Legendado</div></a></div>
        """
        assertEquals(listOf("Teste & Lua"), parser.parseSearch("anitube", html, false).map { it.title })
        assertEquals("video/13", parser.parseSearch("anitube", html, true).single().id)
    }

    @Test fun digitalSearchUsesVisibleTitleAndNotAdvertisingTitle() {
        val html = """<div class='itemA'><a href='/anime/a/teste-dublado' title='Assistir agora'><span class='title_anime'>Teste Dublado</span></a></div>"""
        assertEquals("Teste", parser.parseSearch("animesdigital", html, true).single().title)
        assertTrue(parser.parseSearch("animesdigital", html, false).isEmpty())
    }

    @Test fun searchPagesUseRemotePaginationAndIgnoreUntrustedContinuationLinks() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
                calls += url
                val page = url.substringAfter("paged=", "1").toInt()
                val pagination = if (page == 1) {
                    """<div class="content-pagination"><a href="https://animesdigital.org/pesquisa/?s=Teste&amp;paged=2">2</a><a href="https://evil.example/pesquisa/?s=Teste&amp;paged=2">Próxima</a></div>"""
                } else ""
                return SourcePage(
                    """$pagination<div class="itemA"><a href="/anime/a/teste-$page"><span class="title_anime">Teste $page</span></a></div>""",
                    url
                )
            }
        }
        val repository = PortugueseSources(transport)

        val first = repository.searchAllPage("animesdigital", "Teste", 1)
        val second = repository.searchAllPage("animesdigital", "Teste", 2)

        assertEquals("Teste 1", first.items.single().title)
        assertTrue(first.hasMore)
        assertEquals("Teste 2", second.items.single().title)
        assertFalse(second.hasMore)
        assertEquals(
            listOf(
                "https://animesdigital.org/pesquisa/?s=Teste",
                "https://animesdigital.org/pesquisa/?s=Teste&paged=2"
            ),
            calls
        )
    }

    @Test fun searchPageDoesNotClaimMoreWhenOnlyUntrustedContinuationExists() = runBlocking {
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage =
                SourcePage(
                    """<div class="content-pagination"><a href="https://evil.example/pesquisa/?s=Teste&amp;paged=2">2</a></div>""",
                    url
                )
        }

        val result = PortugueseSources(transport).searchAllPage("animesdigital", "Teste", 1)

        assertTrue(result.items.isEmpty())
        assertFalse(result.hasMore)
    }

    @Test fun episodesAreScopedDeduplicatedAndNumericallySorted() {
        val html = """
            <a href='/video/999/'>Outro Episódio 999</a>
            <div class='pagAniListaContainerX'>
                <a href='/video/12/'>Teste – Episódio 12</a>
                <a href='/video/2/'>Teste – Episódio 2</a>
                <a href='/video/25/'>Teste – Episódio 2.5</a>
                <a href='/video/2/'>Teste – Episódio 2</a>
            </div>
        """
        assertEquals(listOf(2.0, 2.5, 12.0), parser.parseEpisodes("anitube", html, "https://www.anitube.zip/video/1").map { it.number })
    }

    @Test fun preservesSpecialEpisodesWithHyphenatedNumbers() {
        val html = """<div class='pagAniListaContainer'><a href='/video/1100/'>One Piece Episódio 1100</a><a href='/video/11005/'>One Piece Episódio 1100-5 – SP13</a><a href='/video/11005/'>One Piece Episódio 1100-5 – SP13</a></div>"""
        assertEquals(listOf(1100.0, 1100.5), parser.parseEpisodes("anitube", html, "https://www.anitube.zip/video/1").map { it.number })
    }
    @Test fun anitubeAcceptsLegacyEpisodeSuffixAndRejectsUntrustedAddresses() {
        val html = """<div class='pagAniListaContainer'><a href='https://www.anitube.zip/939915b/'>One Piece – Episódio 1</a><a href='https://www.anitube.zip/video/939914b12/'>One Piece – Episódio 2</a><a href='https://wrong.example/939913b/'>One Piece – Episódio 3</a><a href='https://www.anitube.zip/939912bad/'>One Piece – Episódio 4</a></div>"""
        val episodes = parser.parseEpisodes("anitube", html, "https://www.anitube.zip/video/931713/")
        assertEquals(listOf(1.0, 2.0), episodes.map { it.number })
        assertEquals("939915b", episodes.first().id)
    }
    @Test fun liveLongAnimeEpisodeLists() = runBlocking {
        assumeTrue(System.getenv("ANIM_LONG_LIST_SMOKE") == "1")
        val repo = PortugueseSources(SourceHttp())
        val titles = listOf(
            AnimeResult("anitube", "video/924102", "Naruto Shippuden", "https://www.anitube.zip/video/924102/", false),
            AnimeResult("anitube", "video/931713", "One Piece", "https://www.anitube.zip/video/931713/", false),
            AnimeResult("animesdigital", "anime/a/one-piece", "One Piece", "https://animesdigital.org/anime/a/one-piece/", false),
        )
        for (anime in titles) {
            val episodes = repo.episodes(anime)
            println("${anime.sourceId} ${anime.title}: ${episodes.size}, ${episodes.first().number} - ${episodes.last().number}")
            assertTrue(episodes.first().number <= 1.0, "Missing first episode: ${anime.sourceId} ${anime.title}")
            assertTrue(episodes.last().number >= if (anime.title == "One Piece") 1000.0 else 500.0, "Missing final episodes")
        }
    }
    @Test fun digitalPaginatesUsingBoundedRequests() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
                calls += url
                val number = if (url.endsWith("page/2/")) 2 else 1
                return SourcePage("""<div class='content-pagination'><a href='https://animesdigital.org/anime/a/teste/page/2/'>2</a></div><a class='b_flex' href='/video/a/ep-$number/'><div class='title_anime'>Teste Episódio $number</div></a>""", url)
            }
        }
        val result = PortugueseSources(transport).episodes(AnimeResult("animesdigital", "anime/a/teste", "Teste", "https://animesdigital.org/anime/a/teste", false))
        assertEquals(listOf(1.0, 2.0), result.map { it.number })
        assertEquals(2, calls.size)
    }

    @Test fun hlsResolutionPreservesSignedUrlAndReferrerWithoutExecutingScript() = runBlocking {
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage = SourcePage("""<div id='blog2'><iframe src='https://api.anivideo.net/videohls.php?d=https%3A%2F%2Fvideo.example%2Fstream.m3u8%3Ftoken%3Da%252Fb%26expires%3D10'></iframe></div>""", url)
        }
        val anime = AnimeResult("anitube", "video/1", "Teste", "https://www.anitube.zip/video/1", false)
        val result = PortugueseSources(transport).streams(anime, Episode("video/2", 1.0, "Episódio 1", "https://www.anitube.zip/video/2")).single()
        assertEquals("https://video.example/stream.m3u8?token=a%2Fb&expires=10", result.source.url)
        assertEquals("https://api.anivideo.net/", result.source.headers["Referer"])
    }

    @Test fun bloggerEscapesAreDecodedAndQualityIsRecognized() {
        val html = """{"streams":[{"play_url":"https:\/\/video.example\/file?x=1\u0026y=2","format_id":22}]}"""
        assertEquals(listOf("https://video.example/file?x=1&y=2" to "720p"), parser.parseBloggerStreams(html))
    }

    @Test fun bloggerRefreshesEmbeddedUrlsAndPassesPlaybackHeaders() = runBlocking {
        val blogger = "https://www.blogger.com/video.g?token=test-token"
        var refreshed = false
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
                val text = when {
                    url.contains("anitube.zip") -> """<div id="blog1"><iframe src="$blogger"></iframe></div>"""
                    url == blogger -> """{"FdrFJe":"session","cfb2h":"build","streams":[{"play_url":"https://video.example/expired","format_id":22}]}"""
                    else -> {
                        assertTrue(url.startsWith("https://www.blogger.com/_/BloggerVideoPlayerUi/data/batchexecute?"))
                        assertTrue(formBody!!.contains("test-token"))
                        refreshed = true
                        """[["wrb.fr","WcwnYd","[null,null,[[\"https://video.example/fresh\",[22]]]]"]]"""
                    }
                }
                return SourcePage(text, url)
            }
        }
        val anime = AnimeResult("anitube", "video/1", "Teste", "https://www.anitube.zip/video/1", false)
        val stream = PortugueseSources(transport).streams(anime, Episode("video/2", 1.0, "Episódio 1", "https://www.anitube.zip/video/2")).single()
        assertTrue(refreshed)
        assertEquals("https://video.example/fresh", stream.source.url)
        assertEquals(blogger, stream.source.headers["Referer"])
        assertEquals("Mozilla/5.0 Anim/0.3", stream.source.headers["User-Agent"])
    }

    @Test fun cancelledTransportIsNotConvertedIntoSourceFailure() = runBlocking {
        val transport = object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage = throw kotlinx.coroutines.CancellationException("cancel")
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> { PortugueseSources(transport).search("anitube", "Teste", false) }
        Unit
    }

    @Test fun livePortugueseSourcesSearchListAndResolve() = runBlocking {
        assumeTrue("Set ANIM_SOURCE_SMOKE=1 to query live sites", System.getenv("ANIM_SOURCE_SMOKE") == "1")
        val repository = PortugueseSources(SourceHttp())
        for (id in listOf("anitube", "animesdigital")) {
            val anime = repository.search(id, "Sousou no Frieren", false).firstOrNull() ?: error("No live results for $id")
            val episodes = repository.episodes(anime)
            assertTrue(episodes.isNotEmpty())
            val streams = repository.streams(anime, episodes.first())
            assertTrue(streams.isNotEmpty())
            assertTrue(streams.all { it.source.url.startsWith("https://") })
            println("PASS $id: ${anime.title}, ${episodes.size} episodes, ${streams.size} stream options")
        }
    }
}

