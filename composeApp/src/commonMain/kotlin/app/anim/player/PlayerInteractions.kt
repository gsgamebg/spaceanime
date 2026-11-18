package app.anim.player

/** Intervals are supplied by trusted episode metadata; missing markers never imply a guessed opening. */
data class Segment(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
    fun contains(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && endMs <= durationMs && positionMs >= startMs && positionMs < endMs
}
data class EpisodeMarkers(val intro: Segment? = null, val outro: Segment? = null)
enum class PlayerKey { Toggle, Back, Forward, VolumeUp, VolumeDown, Mute, Fullscreen, Subtitles, SkipIntro, Escape }
data class VideoInteractions(
    val onTap: (Float) -> Unit = {},
    val onDoubleTap: (Float) -> Unit = {},
    val onActivity: () -> Unit = {},
    val onKey: (PlayerKey) -> Unit = {},
)

expect val playerIsTouchDevice: Boolean

enum class VideoTapAction { Reveal, Toggle, Hide }

/** Desktop uses deliberate side clicks for seeking; touch keeps the familiar single-tap behavior. */
fun directSeekForTap(touch: Boolean, horizontal: Float): Long? = when {
    touch -> null
    horizontal < .25f -> -10_000L
    horizontal > .75f -> 10_000L
    else -> null
}

fun shouldAutoAdvance(autoPlayNext: Boolean, ended: Boolean, hasNext: Boolean): Boolean =
    autoPlayNext && ended && hasNext

fun videoTapAction(touch: Boolean, visible: Boolean, playing: Boolean, horizontal: Float): VideoTapAction = when {
    touch && !visible -> VideoTapAction.Reveal
    horizontal in .25f.. .75f -> VideoTapAction.Toggle
    touch && playing -> VideoTapAction.Hide
    else -> VideoTapAction.Reveal
}
fun controlsMayHide(playing: Boolean, buffering: Boolean, hasError: Boolean, visible: Boolean, menuOpen: Boolean, seeking: Boolean, resumeFailed: Boolean): Boolean =
    playing && !hasError && visible && !menuOpen && !seeking && !resumeFailed

