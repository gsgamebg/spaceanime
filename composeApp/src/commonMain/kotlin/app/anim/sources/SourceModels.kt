package app.anim.sources

import app.anim.player.PlaybackSource

enum class ExtensionSupport { NATIVE, REQUIRES_RUNTIME, EXTERNAL_MANIFEST }
data class ExtensionInfo(val id: String, val name: String, val language: String, val version: String, val author: String, val website: String, val manifest: String, val support: ExtensionSupport)
data class AnimeResult(val sourceId: String, val id: String, val title: String, val url: String, val dubbed: Boolean, val image: String = "")
data class Episode(val id: String, val number: Double, val title: String, val url: String)
data class StreamOption(val label: String, val source: PlaybackSource)
class SourceException(message: String) : Exception(message)
fun serverName(id: String): String = when (id) { "anitube" -> "Servidor Space 1"; "animesdigital" -> "Servidor Space 2"; else -> "Servidor Space" }
data class RecentEpisode(val sourceId: String, val title: String, val episode: Episode, val image: String, val dubbed: Boolean)
data class DiscoveryPage(val sourceId: String, val recent: List<RecentEpisode>, val featured: List<AnimeResult>, val dubbed: List<AnimeResult> = emptyList(), val updatedAt: Long = 0)
data class SourceItemsPage<T>(val items: List<T>, val hasMore: Boolean)

interface SourceRepository {
    suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult>
    suspend fun alternateTitles(query: String): List<String> = emptyList()
    suspend fun searchAll(sourceId: String, query: String): List<AnimeResult> = search(sourceId, query, false) + search(sourceId, query, true)
    suspend fun searchAllPage(sourceId: String, query: String, page: Int): SourceItemsPage<AnimeResult> =
        SourceItemsPage(if (page == 1) searchAll(sourceId, query) else emptyList(), false)
    suspend fun genre(slug: String, page: Int): List<AnimeResult> = emptyList()
    suspend fun genrePageResults(slug: String, page: Int): SourceItemsPage<AnimeResult> =
        SourceItemsPage(genre(slug, page), false)
    suspend fun trending(): List<AnimeResult> = emptyList()
    suspend fun highResolutionCover(anime: AnimeResult): String? = null
    suspend fun synopsis(anime: AnimeResult): String? = null
    suspend fun episodes(anime: AnimeResult): List<Episode>
    suspend fun streams(anime: AnimeResult, episode: Episode): List<StreamOption>
    suspend fun discover(sourceId: String): DiscoveryPage = DiscoveryPage(sourceId, emptyList(), emptyList())
    suspend fun animeForRecent(item: RecentEpisode): AnimeResult = throw SourceException("Não foi possível abrir este título.")
}

expect fun createSourceRepository(): SourceRepository

// A release feed may use a newer URL than the episode index.
fun resolveRecentEpisode(episodes: List<Episode>, recent: Episode): Episode =
    episodes.firstOrNull { it.id == recent.id } ?: episodes.firstOrNull { it.number == recent.number } ?: recent



/** Missing whole numbers in a sequential listing; never assumes unavailable media exists. */
fun missingEpisodeNumbers(episodes: List<Episode>): List<Int> {
    val numbers = episodes.asSequence().map { it.number }.filter { it >= 1.0 && it <= 5000.0 && it % 1.0 == 0.0 }.map { it.toInt() }.toSet()
    val last = numbers.maxOrNull() ?: return emptyList()
    if (last < 2) return emptyList()
    return (1..last).filter { it !in numbers }
}
