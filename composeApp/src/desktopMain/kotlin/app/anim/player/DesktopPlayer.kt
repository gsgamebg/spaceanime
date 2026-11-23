package app.anim.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CompletableFuture

internal class DesktopPlayer(private val source: PlaybackSource) : PlayerController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state = mutable.asStateFlow()
    private val released = AtomicBoolean(false)
    internal val releaseComplete = CompletableFuture<Unit>()
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "anim-player").apply { isDaemon = true }
    }
    private val factory: MediaPlayerFactory
    val component: EmbeddedMediaPlayerComponent
    private val mediaOptions = desktopMediaOptions(source).toTypedArray()
    /** Replaying from the end must not jump back to the resume point. */
    private val replayOptions = desktopMediaOptions(source.copy(startPositionMs = 0)).toTypedArray()
    private var subtitlesAdded = false
    init {
        prepareDesktopRuntime()
        // --http-reconnect: resume dropped CDN connections instead of failing the stream.
        factory = MediaPlayerFactory("--no-video-title-show", "--no-metadata-network-access", "--quiet", "--http-reconnect")
        try {
            component = EmbeddedMediaPlayerComponent(factory, null, null, null, null)
            component.mediaPlayer().input().enableMouseInputHandling(false)
            component.mediaPlayer().input().enableKeyInputHandling(false)
        } catch (failure: Throwable) {
            factory.release()
            worker.shutdown()
            throw failure
        }
        component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) {
                publish { it.copy(error = "Não foi possível abrir o vídeo. Confira a URL e sua conexão.", playing = false, buffering = false) }
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                publish { it.copy(ended = true, playing = false, buffering = false) }
            }
            override fun playing(mediaPlayer: MediaPlayer) { publish { it.copy(buffering = false) } }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                publish { it.copy(buffering = newCache < 100f) }
            }
        })
    }
    private fun publish(change: (PlaybackState) -> PlaybackState) {
        if (!released.get()) mutable.update(change)
    }
    private fun command(action: () -> Unit) {
        if (!released.get()) worker.execute { if (!released.get()) action() }
    }
    fun start() {
        command {
            if (!component.mediaPlayer().media().play(source.url, *mediaOptions)) {
                publish { it.copy(error = "Não foi possível iniciar o vídeo.", buffering = false) }
            }
            component.mediaPlayer().audio().setVolume((state.value.volume * 100).toInt())
        }
        worker.scheduleWithFixedDelay({
            if (!released.get()) {
                val player = component.mediaPlayer()
                if (!subtitlesAdded && player.status().isPlaying) {
                    subtitlesAdded = true
                    source.subtitles.forEach { player.subpictures().setSubTitleUri(it.url) }
                }
                publish { it.copy(
                    playing = player.status().isPlaying,
                    positionMs = player.status().time().coerceAtLeast(0),
                    durationMs = player.status().length().coerceAtLeast(0),
                    seekable = player.status().isSeekable,
                    audioTracks = player.audio().trackDescriptions().filter { track -> track.id() >= 0 }.map { track -> PlayerTrack(track.id().toString(), track.description()) },
                    subtitleTracks = player.subpictures().trackDescriptions().filter { track -> track.id() >= 0 }.map { track -> PlayerTrack(track.id().toString(), track.description()) },
                    selectedAudio = player.audio().track().takeIf { id -> id >= 0 }?.toString(),
                    selectedSubtitle = player.subpictures().track().takeIf { id -> id >= 0 }?.toString(),
                ) }
            }
        }, 250, 500, TimeUnit.MILLISECONDS)
    }
    override fun play() = command {
        if (state.value.ended) {
            publish { it.copy(ended = false, buffering = true) }
            subtitlesAdded = false
            component.mediaPlayer().media().play(source.url, *replayOptions)
        } else component.mediaPlayer().controls().play()
    }
    override fun pause() = command { component.mediaPlayer().controls().setPause(true) }
    override fun seekTo(positionMs: Long) = command {
        if (state.value.seekable) component.mediaPlayer().controls().setTime(boundedPosition(positionMs, state.value.durationMs))
    }
    override fun setVolume(volume: Float) = command {
        val value = volume.coerceIn(0f, 1f)
        component.mediaPlayer().audio().setVolume((value * 100).toInt())
        publish { it.copy(volume = value) }
    }
    override fun setSpeed(speed: Float) = command {
        component.mediaPlayer().controls().setRate(speed)
        publish { it.copy(speed = speed) }
    }
    override fun selectAudio(id: String) = command {
        if (state.value.audioTracks.any { it.id == id }) id.toIntOrNull()?.let { component.mediaPlayer().audio().setTrack(it) }
    }
    override fun selectSubtitle(id: String?) = command {
        if (id == null) component.mediaPlayer().subpictures().setTrack(-1)
        else if (state.value.subtitleTracks.any { it.id == id }) id.toIntOrNull()?.let { component.mediaPlayer().subpictures().setTrack(it) }
    }
    override fun release() {
        if (released.compareAndSet(false, true)) {
            worker.execute {
                try {
                    component.mediaPlayer().controls().stop()
                    component.release()
                    factory.release()
                    releaseComplete.complete(Unit)
                } catch (failure: Throwable) { releaseComplete.completeExceptionally(failure) }
            }
            worker.shutdown()
        }
    }
}

private class UnavailablePlayer : PlayerController {
    override val state = MutableStateFlow(PlaybackState(buffering = false, error = "O componente de vídeo não está disponível. Execute scripts/install-player.ps1 e reabra o Anim."))
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun release() = Unit
}

@Composable
actual fun rememberPlayerController(source: PlaybackSource): PlayerController {
    val controller = remember(source) {
        try { DesktopPlayer(source) }
        catch (_: Exception) { UnavailablePlayer() }
        catch (_: LinkageError) { UnavailablePlayer() }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(controller) {
        if (controller is DesktopPlayer) {
            // VLC requires a realized native Canvas before binding its video output.
            while (!controller.component.isDisplayable) delay(25)
            controller.start()
        }
    }
    return controller
}

@Composable
actual fun VideoSurface(controller: PlayerController, modifier: Modifier, interactions: VideoInteractions) {
    if (controller is DesktopPlayer) {
        val current by rememberUpdatedState(interactions)
        DisposableEffect(controller) {
            val canvas = controller.component.videoSurfaceComponent()
            var lastPointer: java.awt.Point? = null
            val mouse = object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(event: java.awt.event.MouseEvent) {
                    canvas.requestFocusInWindow()
                    if (event.button == java.awt.event.MouseEvent.BUTTON1) {
                        val x = event.x.toFloat() / canvas.width.coerceAtLeast(1)
                        if (event.clickCount == 2) current.onDoubleTap(x) else if (event.clickCount == 1) current.onTap(x)
                    }
                }
                override fun mouseMoved(event: java.awt.event.MouseEvent) { val point = event.locationOnScreen; if (point != lastPointer) { lastPointer = point; current.onActivity() } }
                override fun mouseDragged(event: java.awt.event.MouseEvent) { current.onActivity() }
            }
            val keys = object : java.awt.event.KeyAdapter() {
                override fun keyPressed(event: java.awt.event.KeyEvent) {
                    val key = when (event.keyCode) {
                        java.awt.event.KeyEvent.VK_SPACE, java.awt.event.KeyEvent.VK_K -> PlayerKey.Toggle
                        java.awt.event.KeyEvent.VK_LEFT -> PlayerKey.Back
                        java.awt.event.KeyEvent.VK_RIGHT -> PlayerKey.Forward
                        java.awt.event.KeyEvent.VK_UP -> PlayerKey.VolumeUp
                        java.awt.event.KeyEvent.VK_DOWN -> PlayerKey.VolumeDown
                        java.awt.event.KeyEvent.VK_M -> PlayerKey.Mute
                        java.awt.event.KeyEvent.VK_F -> PlayerKey.Fullscreen
                        java.awt.event.KeyEvent.VK_L -> PlayerKey.Subtitles
                        java.awt.event.KeyEvent.VK_C -> PlayerKey.SkipIntro
                        java.awt.event.KeyEvent.VK_ESCAPE -> PlayerKey.Escape
                        else -> null
                    }
                    if (key != null && !event.isAltDown && !event.isControlDown && !event.isMetaDown) { current.onKey(key); event.consume() }
                }
            }
            canvas.isFocusable = true
            canvas.addMouseListener(mouse); canvas.addMouseMotionListener(mouse); canvas.addKeyListener(keys)
            onDispose { canvas.removeMouseListener(mouse); canvas.removeMouseMotionListener(mouse); canvas.removeKeyListener(keys) }
        }
        SwingPanel(background = Color.Black, modifier = modifier, factory = { controller.component })
    }
}

@Composable
actual fun PlayerBackHandler(onBack: () -> Unit) = Unit


actual val playerIsTouchDevice: Boolean = false


