package app.anim.notifications

import androidx.compose.runtime.Composable

interface PlaybackNotifier { fun nearEnd(title: String) }
@Composable expect fun rememberPlaybackNotifier(): PlaybackNotifier
fun shouldNotifyNearEnd(position: Long, duration: Long): Boolean =
    duration >= 180_000 && position > 0 && position < duration && duration - position <= minOf(120_000, duration / 10)
