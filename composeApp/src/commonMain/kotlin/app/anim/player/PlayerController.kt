package app.anim.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import app.anim.library.EpisodeContext

data class SubtitleSource(val url: String, val label: String = "Legenda externa", val language: String = "pt", val mimeType: String = "text/vtt")
data class PlayerTrack(val id: String, val label: String)
data class PlaybackSource(val title: String, val url: String, val headers: Map<String, String> = emptyMap(), val subtitles: List<SubtitleSource> = emptyList(), val context: EpisodeContext? = null, val startPositionMs: Long = 0, val markers: EpisodeMarkers = EpisodeMarkers(), val startFromBeginning: Boolean = false) {
    init {
        require(headers.all { (name, value) -> name.lowercase() in setOf("referer", "user-agent") && value.none { it == '\r' || it == '\n' || it == '\u0000' } }) { "Cabeçalho de reprodução não suportado." }
    }
}

fun startPosition(source: PlaybackSource, savedPositionMs: Long): Long =
    if (source.startFromBeginning) 0L else savedPositionMs.coerceAtLeast(0L)

data class PlaybackState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val ended: Boolean = false,
    val volume: Float = 0.7f,
    val speed: Float = 1.0f,
    val error: String? = null,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val selectedAudio: String? = null,
    val selectedSubtitle: String? = null,
    val positionReady: Boolean = true,
)

interface PlayerController {
    val state: StateFlow<PlaybackState>
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setSpeed(speed: Float) {}
    fun selectAudio(id: String) {}
    fun selectSubtitle(id: String?) {}
    fun release()
}

// One controller per screen/session. Removing the screen releases its native resources.
@Composable
expect fun rememberPlayerController(source: PlaybackSource): PlayerController

@Composable
expect fun VideoSurface(controller: PlayerController, modifier: Modifier, interactions: VideoInteractions)

fun normalizedStreamUrl(input: String): String? {
    val url = input.trim()
    if (!url.startsWith("https://", ignoreCase = true)) return null
    if (url.any { it.isWhitespace() || it.code < 32 }) return null
    val authority = url.substring(8).substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority || authority.startsWith(':')) return null
    return "https://" + url.substring(8)
}

fun boundedPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceIn(0, durationMs.coerceAtLeast(0))

fun playbackTime(milliseconds: Long): String {
    val total = milliseconds.coerceAtLeast(0) / 1000
    val seconds = (total % 60).toString().padStart(2, '0')
    val minutes = (total / 60 % 60).toString().padStart(2, '0')
    return if (total >= 3600) "${total / 3600}:$minutes:$seconds" else "$minutes:$seconds"
}

