package app.anim.library

import androidx.compose.runtime.Composable
import app.anim.player.PlaybackState
import app.anim.sources.AnimeResult
import app.anim.sources.Episode
import kotlinx.coroutines.flow.StateFlow

data class EpisodeContext(val anime: AnimeResult, val episode: Episode)
data class WatchProgress(val context: EpisodeContext, val positionMs: Long, val durationMs: Long, val completed: Boolean, val updatedAt: Long)
data class LibrarySnapshot(val favorites: List<AnimeResult> = emptyList(), val progress: List<WatchProgress> = emptyList(), val error: String? = null)

fun AnimeResult.sameAnime(other: AnimeResult): Boolean = sourceId == other.sourceId && id == other.id && dubbed == other.dubbed
fun EpisodeContext.sameEpisode(other: EpisodeContext): Boolean = anime.sameAnime(other.anime) && episode.id == other.episode.id
fun LibrarySnapshot.progressFor(context: EpisodeContext): WatchProgress? = progress.firstOrNull { it.context.sameEpisode(context) }
fun LibrarySnapshot.isFavorite(anime: AnimeResult): Boolean = favorites.any { it.sameAnime(anime) }
fun WatchProgress.resumePosition(): Long = if (completed || durationMs <= 0) 0 else positionMs.coerceIn(0, (durationMs - 1000).coerceAtLeast(0))

interface LibraryStore {
    val state: StateFlow<LibrarySnapshot>
    fun toggleFavorite(anime: AnimeResult)
    fun record(context: EpisodeContext, playback: PlaybackState)
    fun markWatched(context: EpisodeContext, watched: Boolean)
    suspend fun flush()
}

@Composable
expect fun rememberLibraryStore(profileId: String = "default"): LibraryStore
