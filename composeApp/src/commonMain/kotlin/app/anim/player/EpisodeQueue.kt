package app.anim.player

import app.anim.sources.AnimeResult
import app.anim.sources.Episode

data class EpisodeQueue(
    val anime: AnimeResult,
    val episodes: List<Episode>,
    val currentEpisodeId: String,
) {
    private val currentIndex: Int
        get() = episodes.indexOfFirst { it.id == currentEpisodeId }

    fun previous(): Episode? = currentIndex.takeIf { it > 0 }?.let { episodes[it - 1] }

    fun next(): Episode? = currentIndex.takeIf { it >= 0 }?.let { episodes.getOrNull(it + 1) }

    fun select(episode: Episode): EpisodeQueue? =
        episode.takeIf { candidate -> episodes.any { it.id == candidate.id } }
            ?.let { copy(currentEpisodeId = it.id) }
}
