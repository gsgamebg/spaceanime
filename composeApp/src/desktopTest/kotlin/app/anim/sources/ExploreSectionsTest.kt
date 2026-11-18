package app.anim.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class ExploreSectionsTest {
    @Test fun trendingOnlyAcceptsExactTitleAndPreservesRank() = runBlocking {
        val titles = parseTrending("""{"data":{"Page":{"media":[{"title":{"romaji":"Série A","english":"Series A"}},{"title":{"romaji":"Série B","english":null}}]}}}""")
        val repo = object : SourceRepository {
            override suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult> = if (dubbed) emptyList() else when (query) {
                "Série A" -> listOf(AnimeResult(sourceId, "wrong", "Série A 2", "https://example.com", false), AnimeResult(sourceId, "a", "Série A", "https://example.com", false))
                "Série B" -> listOf(AnimeResult(sourceId, "b", "Série B", "https://example.com", false))
                else -> emptyList()
            }
            override suspend fun episodes(anime: AnimeResult) = emptyList<Episode>()
            override suspend fun streams(anime: AnimeResult, episode: Episode) = emptyList<StreamOption>()
        }
        assertEquals(listOf("Série A", "Série B"), matchTrending(titles, repo).map { it.title })
    }
    @Test fun genreUsesAllowlistedSlugAndRealCatalog() = runBlocking {
        var requested = ""
        val repo = PortugueseSources(object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
                requested = url
                return SourcePage("""<div class="itemA"><a href="/anime/a/aventura" title="Assistir Aventura"><span class="title_anime">Aventura</span></a></div>""", url)
            }
        })
        assertEquals("Aventura", repo.genre("aventura", 2).single().title)
        assertEquals("https://animesdigital.org/genero/aventura/page/2/", requested)
        assertFailsWith<IllegalArgumentException> { repo.genre("../../outro", 1) }
        assertFailsWith<IllegalArgumentException> { repo.genre("aventura", 41) }
        Unit
    }

    @Test fun genrePageReportsOnlyAnAllowlistedRemoteNextPage() = runBlocking {
        val calls = mutableListOf<String>()
        val repo = PortugueseSources(object : SourceTransport {
            override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
                calls += url
                val html = if (url.endsWith("/genero/aventura/")) {
                    """<div class="content-pagination"><a href="https://animesdigital.org/genero/aventura/page/2/">2</a><a href="https://animesdigital.org:444/genero/aventura/page/2/">2 alt</a><a href="https://evil.example/genero/aventura/page/2/">2 external</a></div><div class="itemA"><a href="/anime/a/a"><span class="title_anime">A</span></a></div>"""
                } else {
                    """<div class="itemA"><a href="/anime/a/b"><span class="title_anime">B</span></a></div>"""
                }
                return SourcePage(html, url)
            }
        })

        val first = repo.genrePageResults("aventura", 1)
        val second = repo.genrePageResults("aventura", 2)

        assertEquals(listOf("A"), first.items.map { it.title })
        assertTrue(first.hasMore)
        assertEquals(listOf("B"), second.items.map { it.title })
        assertFalse(second.hasMore)
        assertEquals(
            listOf(
                "https://animesdigital.org/genero/aventura/",
                "https://animesdigital.org/genero/aventura/page/2/"
            ),
            calls
        )
    }
    @Test fun liveCategoriesAndTrending() = runBlocking {
        assumeTrue(System.getenv("ANIM_EXPLORE_SMOKE") == "1")
        val repo = CachedSources(PortugueseSources(SourceHttp()))
        val categories = repo.genre("acao", 1)
        assertTrue(categories.isNotEmpty())
        val trends = AniListTrending().fetch()
        assertTrue(trends.isNotEmpty())
        val matched = matchTrending(trends, PortugueseSources(SourceHttp()))
        println("Categoria: ${categories.size}; tendências: ${trends.size}; disponíveis: ${matched.size}")
        assertTrue(matched.isNotEmpty())
    }
}

