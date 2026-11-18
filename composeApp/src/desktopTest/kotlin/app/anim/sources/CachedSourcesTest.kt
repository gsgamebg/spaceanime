package app.anim.sources

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CachedSourcesTest {
    @Test fun metadataExpiresAndStreamsAlwaysResolveAgain() = runBlocking {
        var time = 0L
        var searches = 0
        var episodes = 0
        var streams = 0
        val anime = AnimeResult("anitube", "1", "Teste", "https://www.anitube.zip/1", false)
        val episode = Episode("2", 1.0, "Episódio 1", "https://www.anitube.zip/2")
        val delegate = object : SourceRepository {
            override suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult> { searches++; return listOf(anime) }
            override suspend fun episodes(anime: AnimeResult): List<Episode> { episodes++; return listOf(episode) }
            override suspend fun streams(anime: AnimeResult, episode: Episode): List<StreamOption> { streams++; return emptyList() }
        }
        val cache = CachedSources(delegate) { time }
        repeat(3) { cache.search("anitube", "Teste", false); cache.episodes(anime); cache.streams(anime, episode) }
        assertEquals(1, searches); assertEquals(1, episodes); assertEquals(3, streams)
        cache.search("anitube", "Teste", true)
        assertEquals(2, searches)
        time = 120_001
        cache.search("anitube", "Teste", false); cache.episodes(anime)
        assertEquals(3, searches); assertEquals(2, episodes)
        repeat(30) { cache.search("anitube", "Title $it", false) }
        cache.search("anitube", "Teste", false)
        assertEquals(34, searches)
    }
}
