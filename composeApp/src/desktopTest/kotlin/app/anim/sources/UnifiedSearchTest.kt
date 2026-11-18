package app.anim.sources

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class UnifiedSearchTest {
    @Test fun expandsKnownAliasesWithoutChangingUnrelatedQueries() {
        assertTrue(searchTerms("Attack on Titan").any { it.equals("shingeki no kyojin", true) })
        assertTrue(searchTerms("Shingeki no Kyojin").any { it.equals("attack on titan", true) })
        assertEquals(listOf("Naruto Shippuden"), searchTerms("Naruto Shippuden"))
    }
    @Test fun returnsBothAudioVariantsAndKeepsPartialResults() = runBlocking {
        val repo = object : SourceRepository {
            override suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult> {
                if (sourceId == "animesdigital") error("offline")
                return listOf(AnimeResult(sourceId, if (dubbed) "dub" else "leg", query, "https://example.org", dubbed))
            }
            override suspend fun episodes(anime: AnimeResult) = emptyList<Episode>()
            override suspend fun streams(anime: AnimeResult, episode: Episode) = emptyList<StreamOption>()
        }
        val result = unifiedSearch(repo, "Attack on Titan")
        assertEquals(1, result.failedSources)
        assertEquals(2, result.items.size)
        assertEquals(setOf(true, false), result.items.map { it.dubbed }.toSet())
    }

    @Test fun requestsTheNextRemotePageAndPreservesHealthyResultsOnPartialFailure() = runBlocking {
        val requestedPages = mutableListOf<Pair<String, Int>>()
        val repo = object : SourceRepository {
            override suspend fun search(sourceId: String, query: String, dubbed: Boolean) = emptyList<AnimeResult>()
            override suspend fun searchAllPage(sourceId: String, query: String, page: Int): SourceItemsPage<AnimeResult> {
                requestedPages += sourceId to page
                if (sourceId == "animesdigital") error("offline")
                return SourceItemsPage(
                    listOf(AnimeResult(sourceId, "episode-$page", "Série $page", "https://example.org/$page", false)),
                    hasMore = page == 1
                )
            }
            override suspend fun episodes(anime: AnimeResult) = emptyList<Episode>()
            override suspend fun streams(anime: AnimeResult, episode: Episode) = emptyList<StreamOption>()
        }

        val first = unifiedSearchPage(repo, "Naruto", 1)
        val second = unifiedSearchPage(repo, "Naruto", 2)

        assertEquals(listOf("Série 1"), first.items.map { it.title })
        assertTrue(first.hasMore)
        assertEquals(listOf("Série 2"), second.items.map { it.title })
        assertEquals(1, second.failedSources)
        assertEquals(setOf("animesdigital"), second.sourceFailures)
        assertFalse(second.hasMore)
        assertTrue(requestedPages.contains("anitube" to 2))
        assertTrue(requestedPages.contains("animesdigital" to 2))
    }
}
