package app.anim.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private class AndroidPlayer(val player: ExoPlayer) : PlayerController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state = mutable.asStateFlow()
    private var released = false
    init {
        player.volume = state.value.volume
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { refresh() }
            override fun onPlayerError(error: PlaybackException) {
                mutable.update { it.copy(error = "Não foi possível reproduzir este vídeo (${error.errorCodeName}).", playing = false, buffering = false) }
            }
        })
    }
    fun refresh() {
        if (released) return
        mutable.update {
            it.copy(
                playing = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.coerceAtLeast(0),
                seekable = player.isCurrentMediaItemSeekable,
                ended = player.playbackState == Player.STATE_ENDED,
                volume = player.volume,
                audioTracks = tracks(C.TRACK_TYPE_AUDIO),
                subtitleTracks = tracks(C.TRACK_TYPE_TEXT),
                selectedAudio = selectedTrack(C.TRACK_TYPE_AUDIO),
                selectedSubtitle = selectedTrack(C.TRACK_TYPE_TEXT),
            )
        }
    }
    override fun play() {
        if (released) return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }
    override fun pause() { if (!released) player.pause() }
    override fun seekTo(positionMs: Long) {
        if (!released && state.value.seekable) player.seekTo(boundedPosition(positionMs, state.value.durationMs))
    }
    override fun setVolume(volume: Float) { if (!released) player.volume = volume.coerceIn(0f, 1f) }
    override fun setSpeed(speed: Float) {
        if (!released) {
            player.setPlaybackSpeed(speed)
            mutable.update { it.copy(speed = speed) }
        }
    }
    private fun tracks(type: Int): List<PlayerTrack> = player.currentTracks.groups.flatMapIndexed { groupIndex, group ->
        if (group.type != type) emptyList() else (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            val format = group.getTrackFormat(index)
            PlayerTrack("$groupIndex:$index", format.label ?: format.language ?: "Faixa ${index + 1}")
        }
    }
    private fun selectedTrack(type: Int): String? = player.currentTracks.groups.mapIndexedNotNull { groupIndex, group ->
        if (group.type != type) null else (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { "$groupIndex:$it" }
    }.firstOrNull()
    private fun select(type: Int, id: String?) {
        if (released) return
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, id == null)
        if (id != null) {
            val parts = id.split(':').mapNotNull { it.toIntOrNull() }
            if (parts.size != 2) return
            val group = player.currentTracks.groups.getOrNull(parts[0]) ?: return
            if (group.type != type || parts[1] !in 0 until group.length || !group.isTrackSupported(parts[1])) return
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parts[1]))
        }
        player.trackSelectionParameters = builder.build()
    }
    override fun selectAudio(id: String) { select(C.TRACK_TYPE_AUDIO, id) }
    override fun selectSubtitle(id: String?) { select(C.TRACK_TYPE_TEXT, id) }
    override fun release() { if (!released) { released = true; player.release() } }
}

@Composable
actual fun rememberPlayerController(source: PlaybackSource): PlayerController {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val controller = remember(source) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        val data = DefaultDataSource.Factory(context, http)
        AndroidPlayer(ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(data)).build())
    }
    DisposableEffect(controller, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(controller) {
        val subtitles = source.subtitles.mapIndexed { index, subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url)).setId("external-$index")
                .setLabel(subtitle.label).setLanguage(subtitle.language).setMimeType(subtitle.mimeType)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0).build()
        }
        controller.player.setMediaItem(MediaItem.Builder().setUri(source.url).setSubtitleConfigurations(subtitles).build())
        controller.player.prepare()
        controller.player.playWhenReady = true
        // Event callbacks cover state changes; a modest clock tick is enough for progress without waking the UI 4x/sec.
        while (true) { controller.refresh(); delay(500) }
    }
    return controller
}

@Composable
actual fun VideoSurface(controller: PlayerController, modifier: Modifier, interactions: VideoInteractions) {
    val androidPlayer = controller as AndroidPlayer
    val current by rememberUpdatedState(interactions)
    AndroidView(
        modifier = modifier,
        factory = { context -> PlayerView(context).apply {
            player = androidPlayer.player; useController = false; keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
            val detector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: android.view.MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(event: android.view.MotionEvent): Boolean { current.onTap(event.x / width.coerceAtLeast(1)); return true }
                override fun onDoubleTap(event: android.view.MotionEvent): Boolean { current.onDoubleTap(event.x / width.coerceAtLeast(1)); return true }
            })
            setOnTouchListener { _, event -> current.onActivity(); detector.onTouchEvent(event) }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val key = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerKey.Toggle
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> PlayerKey.Back
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerKey.Forward
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> PlayerKey.VolumeUp
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKey.VolumeDown
                    android.view.KeyEvent.KEYCODE_VOLUME_MUTE -> PlayerKey.Mute
                    android.view.KeyEvent.KEYCODE_ESCAPE,
                    android.view.KeyEvent.KEYCODE_BACK -> PlayerKey.Escape
                    else -> null
                }
                if (key == null) false else { current.onKey(key); true }
            }
        } },
        onRelease = { view -> view.player = null; view.keepScreenOn = false },
    )
}

@Composable
actual fun PlayerBackHandler(onBack: () -> Unit) { BackHandler(onBack = onBack) }



actual val playerIsTouchDevice: Boolean = true
