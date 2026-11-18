package app.anim.discovery

import app.anim.sources.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class DiscoveryTest {
    private val anime = AnimeResult("anitube", "video/1", "Teste", "https://www.anitube.zip/video/1", false)
    private val episode = Episode("video/2", 1.0, "Episódio 1", "https://www.anitube.zip/video/2")
    private fun temp() = Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "discovery-").resolve("cache.json").toFile()
    @Test fun refreshIsDailyPersistentAndFailureKeepsPreviousResults() = runBlocking {
        var clock = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3600000
        var calls = 0
        var failure = false
        val repo = object : SourceRepository {
            override suspend fun search(sourceId: String, query: String, dubbed: Boolean) = emptyList<AnimeResult>()
            override suspend fun episodes(anime: AnimeResult) = emptyList<Episode>()
            override suspend fun streams(anime: AnimeResult, episode: Episode) = emptyList<StreamOption>()
            override suspend fun discover(sourceId: String): DiscoveryPage {
                calls++
                if (failure) error("offline")
                return DiscoveryPage(sourceId, listOf(RecentEpisode(sourceId, "Teste", episode, "", false)), listOf(anime.copy(sourceId = sourceId)))
            }
        }
        val file = temp()
        val store = FileDiscovery(file, repo) { clock }
        store.refresh(); assertEquals(2, calls)
        store.refresh(); assertEquals(2, calls)
        val reopened = FileDiscovery(file, repo) { clock }
        assertEquals(2, reopened.state.value.pages.size)
        reopened.refresh(); assertEquals(2, calls)
        clock += 86_400_000; failure = true
        reopened.refresh(); assertEquals(4, calls)
        assertEquals(2, reopened.state.value.pages.size)
        assertNotNull(reopened.state.value.message)
        reopened.refresh(); assertEquals(4, calls)
        failure = false; reopened.refresh(force = true); assertEquals(6, calls)
        assertNull(reopened.state.value.message)
    }
    @Test fun calendarBoundaryAndClockRollbackInvalidateCache() {
        val midnight = LocalDate.of(2026, 10, 31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(discoveryDue(midnight - 1000, midnight + 1000))
        assertTrue(discoveryDue(midnight, midnight - 1000))
        assertFalse(discoveryDue(midnight + 1000, midnight + 5000))
    }
    @Test fun recentEpisodeUsesCanonicalIndexAndFallsBackForNewRelease() {
        val alias = episode.copy(id = "video/alias")
        assertEquals(episode, resolveRecentEpisode(listOf(episode), alias))
        assertEquals(alias, resolveRecentEpisode(emptyList(), alias))
    }
    @Test fun liveDiscoveryAndParentLinksWork() = runBlocking {
        assumeTrue(System.getenv("ANIM_SOURCE_SMOKE") == "1")
        val repository = PortugueseSources(SourceHttp())
        for (source in listOf("anitube", "animesdigital")) {
            val page = repository.discover(source)
            assertTrue(page.recent.isNotEmpty())
            assertTrue(page.featured.isNotEmpty())
            val parent = repository.animeForRecent(page.recent.first())
            assertEquals(source, parent.sourceId)
            val episodes = repository.episodes(parent)
            assertTrue(episodes.any { it.number == page.recent.first().episode.number }, "Recent episode must belong to its parent anime")
            println("Discovery PASS ${serverName(source)}: ${page.recent.size} episodes, ${page.featured.size} titles")
        }
    }
}


