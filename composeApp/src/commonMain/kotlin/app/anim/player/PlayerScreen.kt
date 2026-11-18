package app.anim.player

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.anim.notifications.rememberPlaybackNotifier
import app.anim.notifications.shouldNotifyNearEnd
import app.anim.design.SpaceIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.design.SpaceColors
import app.anim.sources.Episode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

val demoMp4 = PlaybackSource("Sintel · trailer MP4", "https://media.w3.org/2010/05/sintel/trailer.mp4")
val demoHls = PlaybackSource("Big Buck Bunny · demonstração HLS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")

@Composable
expect fun PlayerBackHandler(onBack: () -> Unit)

// ── Escolha de URL manual (sala de testes interna) ─────────────────────────────
@Composable
fun PlaybackChooser(onPlay: (PlaybackSource) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Text("Sala de Reprodução Externa", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Abra um link direto de vídeo MP4 ou HLS externo.", color = SpaceColors.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = input, onValueChange = { input = it; invalid = false },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("URL HTTPS do vídeo") },
        placeholder = { Text("https://…/video.m3u8 ou .mp4") },
        isError = invalid,
        shape = RoundedCornerShape(14.dp),
        supportingText = { Text(if (invalid) "Informe um endereço HTTPS válido." else "Links diretos MP4 ou HLS.") }
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            val url = normalizedStreamUrl(input)
            if (url == null) invalid = true else onPlay(PlaybackSource("Vídeo por URL", url))
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
    ) { Text("Abrir vídeo", fontWeight = FontWeight.SemiBold) }
}

// ── Player Screen Principal e Completo ────────────────────────────────────────
@Composable
fun PlayerScreen(
    source: PlaybackSource,
    onBack: () -> Unit,
    onFullScreenChange: (Boolean) -> Unit,
    onProgress: (PlaybackState) -> Unit = {},
    queue: EpisodeQueue? = null,
    navigationLoading: Boolean = false,
    navigationError: String? = null,
    onEpisodeChange: (Episode) -> Unit = {},
) {
    val notifier = rememberPlaybackNotifier()
    var notified by remember(source) { mutableStateOf(false) }
    var attempt by remember(source) { mutableIntStateOf(0) }
    var retryPosition by remember(source) { mutableLongStateOf(source.startPositionMs) }
    var lastValidPosition by remember(source) { mutableLongStateOf(source.startPositionMs) }
    var fullScreen by remember { mutableStateOf(playerIsTouchDevice) }
    val latestFullScreenChange by rememberUpdatedState(onFullScreenChange)
    DisposableEffect(Unit) { if (playerIsTouchDevice) latestFullScreenChange(true); onDispose { latestFullScreenChange(false) } }

    PlayerBackHandler { if (fullScreen) { fullScreen = false; onFullScreenChange(false) } else onBack() }

    key(source, attempt) {
        val attemptSource = remember { source.copy(startPositionMs = retryPosition) }
        val controller = rememberPlayerController(attemptSource)
        val state by controller.state.collectAsState()
        var resumeReady by remember { mutableStateOf(attemptSource.startPositionMs <= 0) }
        var resumeFailed by remember { mutableStateOf(false) }
        val latestState by rememberUpdatedState(state.copy(positionReady = resumeReady))
        val latestProgress by rememberUpdatedState(onProgress)
        var controlsVisible by remember { mutableStateOf(true) }

        LaunchedEffect(state.positionMs, state.durationMs, state.playing) {
            if (!notified && state.playing && source.context != null && shouldNotifyNearEnd(state.positionMs, state.durationMs)) {
                notified = true; notifier.nearEnd(source.context.anime.title)
            }
        }
        LaunchedEffect(state.positionMs, state.error, resumeReady) {
            if (resumeReady && state.error == null && state.durationMs > 0 && state.positionMs > 0) {
                lastValidPosition = if (state.ended) 0 else state.positionMs
            }
        }

        LaunchedEffect(controller) {
            if (!resumeReady) {
                val resumed = withTimeoutOrNull(20_000) {
                    val available = controller.state.first { it.seekable && it.durationMs > 0 && !it.buffering || it.error != null }
                    if (available.error != null) return@withTimeoutOrNull false
                    val target = boundedPosition(attemptSource.startPositionMs, (available.durationMs - 1000).coerceAtLeast(0))
                    controller.seekTo(target)
                    val result = controller.state.first { kotlin.math.abs(it.positionMs - target) < 2000 || it.error != null }
                    result.error == null
                } == true
                resumeReady = resumed
                resumeFailed = !resumed
            }
        }

        LaunchedEffect(controller) { while (true) { delay(5000); latestProgress(latestState) } }
        LaunchedEffect(state.playing, state.ended) { latestProgress(latestState) }
        DisposableEffect(controller) { onDispose { latestProgress(latestState) } }

        var seekPreview by remember { mutableStateOf<Float?>(null) }
        var interaction by remember { mutableIntStateOf(0) }
        var openMenu by remember { mutableStateOf<String?>(null) }
        var feedback by remember { mutableStateOf<String?>(null) }
        var savedVolume by remember { mutableFloatStateOf(.7f) }
        var savedSubtitle by remember { mutableStateOf<String?>(null) }
        var showLoading by remember { mutableStateOf(false) }
        var slowLoading by remember { mutableStateOf(false) }
        fun reveal() { controlsVisible = true; interaction++ }
        fun toggle() { if (state.error == null) { if (state.playing) controller.pause() else controller.play(); feedback = if (state.playing) "Pausado" else "Reproduzindo"; reveal() } }
        fun seek(delta: Long) { if (state.seekable && state.error == null) { controller.seekTo(state.positionMs + delta); feedback = if (delta > 0) "+10 segundos" else "−10 segundos"; reveal() } }
        fun fullscreen() { fullScreen = !fullScreen; onFullScreenChange(fullScreen); reveal() }
        fun mute() { if (state.volume > 0f) { savedVolume = state.volume; controller.setVolume(0f) } else controller.setVolume(savedVolume.coerceAtLeast(.05f)); reveal() }
        fun subtitles() {
            if (state.selectedSubtitle != null) { savedSubtitle = state.selectedSubtitle; controller.selectSubtitle(null) }
            else (state.subtitleTracks.firstOrNull { it.id == savedSubtitle } ?: state.subtitleTracks.firstOrNull())?.let { controller.selectSubtitle(it.id) }
            reveal()
        }
        fun skip(segment: Segment?) { if (state.seekable && segment?.contains(state.positionMs, state.durationMs) == true) { controller.seekTo(segment.endMs); reveal() } }
        fun keyAction(key: PlayerKey) {
            when (key) {
                PlayerKey.Toggle -> toggle()
                PlayerKey.Back -> seek(-10_000)
                PlayerKey.Forward -> seek(10_000)
                PlayerKey.VolumeUp -> { controller.setVolume((state.volume + .05f).coerceAtMost(1f)); reveal() }
                PlayerKey.VolumeDown -> { controller.setVolume((state.volume - .05f).coerceAtLeast(0f)); reveal() }
                PlayerKey.Mute -> mute()
                PlayerKey.Fullscreen -> fullscreen()
                PlayerKey.Subtitles -> subtitles()
                PlayerKey.SkipIntro -> skip(source.markers.intro)
                PlayerKey.Escape -> { if (openMenu != null) openMenu = null else if (fullScreen) fullscreen(); reveal() }
            }
        }
        LaunchedEffect(state.playing, state.error, controlsVisible, openMenu, seekPreview, interaction, resumeFailed) {
            if (controlsMayHide(state.playing, state.buffering, state.error != null, controlsVisible, openMenu != null, seekPreview != null, resumeFailed)) {
                delay(3000); controlsVisible = false
            }
        }
        LaunchedEffect(feedback) { if (feedback != null) { delay(900); feedback = null } }
        LaunchedEffect(state.buffering, state.error) {
            showLoading = false; slowLoading = false
            if (state.buffering && state.error == null) { delay(450); showLoading = true; delay(8000); slowLoading = true }
        }
        val interactions = VideoInteractions(
            onTap = { x ->
                directSeekForTap(playerIsTouchDevice, x)?.let(::seek) ?: run {
                    when (videoTapAction(playerIsTouchDevice, controlsVisible, state.playing, x)) {
                        VideoTapAction.Reveal -> reveal()
                        VideoTapAction.Toggle -> toggle()
                        VideoTapAction.Hide -> controlsVisible = false
                    }
                }
            },
            onDoubleTap = { x -> seek(if (x < .5f) -10_000 else 10_000) },
            onActivity = ::reveal,
            onKey = ::keyAction,
        )
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).then(if (fullScreen) Modifier else Modifier.safeDrawingPadding()).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) false
            else {
                val key = when (event.key) {
                    Key.Spacebar, Key.K -> PlayerKey.Toggle; Key.DirectionLeft -> PlayerKey.Back; Key.DirectionRight -> PlayerKey.Forward
                    Key.DirectionUp -> PlayerKey.VolumeUp; Key.DirectionDown -> PlayerKey.VolumeDown; Key.M -> PlayerKey.Mute
                    Key.F -> PlayerKey.Fullscreen; Key.L -> PlayerKey.Subtitles; Key.C -> PlayerKey.SkipIntro; Key.Escape -> PlayerKey.Escape
                    else -> null
                }
                if (key == null) false else { keyAction(key); true }
            }
        }) {
            val panelHeight = (maxHeight * .48f).coerceAtMost(285.dp)
            Column(Modifier.fillMaxSize()) {
                if (controlsVisible || !state.playing || state.error != null) Surface(color = Color(0xF00D0919), contentColor = SpaceColors.onSurface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { SpaceIcon("chevron-left", null); Text("Voltar") }
                        Text(source.title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        IconButton(onClick = ::fullscreen) { SpaceIcon(if (fullScreen) "minimize" else "maximize", if (fullScreen) "Sair da tela cheia" else "Tela cheia", tint = Color.White) }
                    }
                }
                if (state.error == null) VideoSurface(controller, Modifier.weight(1f).fillMaxWidth(), interactions)
                else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Não foi possível carregar o episódio.", color = SpaceColors.error, style = MaterialTheme.typography.titleMedium)
                    Text("Verifique sua conexão ou tente novamente.", color = SpaceColors.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { retryPosition = lastValidPosition; attempt++ }) { Text("Tentar novamente") }
                        OutlinedButton(onClick = onBack) { Text("Voltar aos episódios") }
                    }
                }
                if (showLoading && state.error == null) PlayerLoading(if (slowLoading) "O vídeo está demorando para carregar..." else if (state.positionMs == 0L) "Preparando seu episódio..." else "Carregando vídeo...", Modifier.fillMaxWidth().height(145.dp).background(Color(0xFF080611)))
                feedback?.let { Text(it, Modifier.fillMaxWidth().padding(4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = SpaceColors.secondary) }
                if (state.seekable && (source.markers.intro?.contains(state.positionMs, state.durationMs) == true || source.markers.outro?.contains(state.positionMs, state.durationMs) == true)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
                        if (source.markers.intro?.contains(state.positionMs, state.durationMs) == true) OutlinedButton(onClick = { skip(source.markers.intro) }) { Text("Pular abertura  ››") }
                        if (source.markers.outro?.contains(state.positionMs, state.durationMs) == true) OutlinedButton(onClick = { skip(source.markers.outro) }) { Text("Pular encerramento  ››") }
                    }
                }
                if (controlsVisible || !state.playing || resumeFailed || state.error != null) Surface(color = Color(0xF00D0919)) {
                    Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        navigationError?.let { Text(it, color = SpaceColors.error) }
                        queue?.let { episodeQueue ->
                            val previous = episodeQueue.previous()
                            val next = episodeQueue.next()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (previous != null) {
                                    OutlinedButton(
                                        onClick = { onEpisodeChange(previous) },
                                        enabled = !navigationLoading,
                                    ) { Text("← Episódio anterior") }
                                } else Spacer(Modifier.width(1.dp))
                                if (next != null) {
                                    OutlinedButton(
                                        onClick = { onEpisodeChange(next) },
                                        enabled = !navigationLoading,
                                    ) { Text(if (navigationLoading) "Carregando…" else "Próximo episódio →") }
                                }
                            }
                            if (state.ended && next != null) {
                                Button(
                                    onClick = { onEpisodeChange(next) },
                                    enabled = !navigationLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Assistir próximo episódio") }
                            }
                        }
                        if (resumeFailed) { Text("Não foi possível retomar a posição salva.", color = SpaceColors.error); TextButton(onClick = { resumeReady = true; resumeFailed = false }) { Text("Continuar desta posição") } }
                        Slider(value = seekPreview ?: (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
                            onValueChange = { seekPreview = it; reveal() }, onValueChangeFinished = { seekPreview?.let { controller.seekTo((it * state.durationMs).toLong()) }; seekPreview = null; reveal() },
                            enabled = state.seekable && state.durationMs > 0 && state.error == null,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Posição do vídeo" })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(playbackTime(state.positionMs), style = MaterialTheme.typography.bodySmall)
                            Text(if (state.durationMs > 0) playbackTime(state.durationMs) else "--:--", style = MaterialTheme.typography.bodySmall)
                        }
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { seek(-10_000) }, enabled = state.seekable && state.error == null) { SpaceIcon("rewind", "Voltar 10 segundos", tint = Color.White) }
                            IconButton(onClick = ::toggle, enabled = state.error == null) { SpaceIcon(if (state.playing) "pause" else "play", if (state.playing) "Pausar" else "Reproduzir", tint = Color.White) }
                            IconButton(onClick = { seek(10_000) }, enabled = state.seekable && state.error == null) { SpaceIcon("forward", "Avançar 10 segundos", tint = Color.White) }
                            IconButton(onClick = ::mute) { SpaceIcon(if (state.volume == 0f) "volume-off" else "volume", if (state.volume == 0f) "Ativar som" else "Silenciar", tint = Color.White) }
                            Slider(state.volume, { controller.setVolume(it); reveal() }, modifier = Modifier.width(120.dp).semantics { contentDescription = "Volume" }, enabled = state.error == null)
                            SpeedMenu(state.speed, controller::setSpeed, openMenu == "speed") { openMenu = if (it) "speed" else null; reveal() }
                            TrackMenu("Áudio", state.audioTracks, state.selectedAudio, false, { it?.let(controller::selectAudio); reveal() }, openMenu == "audio") { openMenu = if (it) "audio" else null; reveal() }
                            TrackMenu("Legendas", state.subtitleTracks, state.selectedSubtitle, true, { controller.selectSubtitle(it); reveal() }, openMenu == "subs") { openMenu = if (it) "subs" else null; reveal() }
                        }
                    }
                }
            }
        }
    }
}
// ── Menu de Velocidade de Reprodução ──────────────────────────────────────────
@Composable
private fun SpeedMenu(currentSpeed: Float, onSelectSpeed: (Float) -> Unit, expanded: Boolean, setExpanded: (Boolean) -> Unit) {

    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Box {
        Surface(
            onClick = { setExpanded(true) },
            shape = RoundedCornerShape(10.dp),
            color = Color(0x28FFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338B42F6))
        ) {
            Text(
                "${currentSpeed}x",
                Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text((if (speed == currentSpeed) "✓ " else "") + "${speed}x") },
                    onClick = { onSelectSpeed(speed); setExpanded(false) }
                )
            }
        }
    }
}

@Composable
private fun TrackMenu(label: String, tracks: List<PlayerTrack>, selected: String?, allowOff: Boolean, onSelect: (String?) -> Unit, expanded: Boolean, setExpanded: (Boolean) -> Unit) {

    Box {
        Surface(
            onClick = { setExpanded(true) },
            shape = RoundedCornerShape(10.dp),
            color = Color(0x28FFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338B42F6)),
            enabled = tracks.isNotEmpty(),
        ) {
            Text(
                "$label: ${tracks.firstOrNull { it.id == selected }?.label ?: if (allowOff) "off" else "auto"}",
                Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        DropdownMenu(expanded, onDismissRequest = { setExpanded(false) }) {
            if (allowOff) DropdownMenuItem(text = { Text("Desligar legendas") }, onClick = { onSelect(null); setExpanded(false) })
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text((if (track.id == selected) "✓ " else "") + track.label) },
                    onClick = { onSelect(track.id); setExpanded(false) }
                )
            }
        }
    }
}








