package app.anim.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/** Opt-in live timing probe (ANIM_TIMING=1): where do Explore and playback spend their time? */
class LiveTimingProbe {
    @Test fun probe() = runBlocking {
        assumeTrue(System.getenv("ANIM_TIMING") == "1")
        val repo = CachedSources(PortugueseSources(SourceHttp()))
        var trending: List<AnimeResult> = emptyList()
        println("TIMING trending ms=" + measureTimeMillis { trending = repo.trending() } + " count=${trending.size}")
        for (id in listOf("anitube", "animesdigital")) {
            var page: DiscoveryPage? = null
            println("TIMING discover $id ms=" + measureTimeMillis { page = repo.discover(id) } + " recent=${page?.recent?.size} featured=${page?.featured?.size}")
            val anime = repo.search(id, "Sousou no Frieren", false).first()
            var eps: List<Episode> = emptyList()
            println("TIMING episodes $id ms=" + measureTimeMillis { eps = repo.episodes(anime) } + " n=${eps.size}")
            var streams: List<StreamOption> = emptyList()
            println("TIMING streams $id ms=" + measureTimeMillis { streams = repo.streams(anime, eps.first()) } + " options=${streams.map { it.label }}")
        }
    }
}
