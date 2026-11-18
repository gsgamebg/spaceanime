package app.anim

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.ads.AdBannerSlot
import app.anim.ads.AdPlacement
import app.anim.design.*
import app.anim.discovery.discoveryTime
import app.anim.discovery.rememberDiscovery
import app.anim.library.LibrarySnapshot
import app.anim.library.WatchProgress
import app.anim.player.playbackTime
import app.anim.profile.LocalProfile
import app.anim.sources.AnimeResult
import app.anim.sources.RecentRelease
import app.anim.sources.RecentEpisode
import app.anim.sources.groupRecentEpisodes
import app.anim.sources.SourceRepository
import app.anim.sources.serverName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ─── Home visual tokens ──────────────────────────────────────────────────────
// The home screen keeps the chrome quiet: one neutral surface ramp, hairline
// borders and a single violet accent. Artwork carries the color.
private val HomeSurface = Color(0xFF0F172A)
private val HomeSurfaceHigh = Color(0xFF1E1B4B)
private val HomeHairline = Color(0x408B5CF6)
private val HomeTextPrimary = Color(0xFFF8FAFC)
private val HomeTextSecondary = Color(0xFFCBD5E1)
private val HomeTextMuted = Color(0xFF94A3B8)
private val HomeAccent = Color(0xFF8B5CF6)
private val HomeCta = Color(0xFF8B5CF6)
private val HomeCtaText = Color.White
private val HomeScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC020617)))
private val HomeCardShape = RoundedCornerShape(20.dp)
private val HomeInnerShape = RoundedCornerShape(14.dp)

@Composable
internal fun SpaceHomeScreen(
    profile: LocalProfile,
    saved: LibrarySnapshot,
    repository: SourceRepository,
    compact: Boolean,
    focus: HomeNavFocus,
    categorySlug: String,
    onCategory: (String) -> Unit,
    onTrending: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onOpenAnime: (AnimeResult) -> Unit,
    onContinue: (WatchProgress) -> Unit,
    onOpenRecent: (AnimeResult, app.anim.sources.Episode) -> Unit,
) {
    val discovery = rememberDiscovery(repository)
    val disco by discovery.state.collectAsState()
    val scope = rememberCoroutineScope()
    var openingRecent by remember { mutableStateOf<String?>(null) }
    var recentError by remember { mutableStateOf<String?>(null) }
    var selectedRelease by remember { mutableStateOf<RecentRelease?>(null) }
    fun openRecent(item: RecentEpisode) {
        if (openingRecent != null) return
        openingRecent = item.episode.id
        recentError = null
        scope.launch {
            try {
                onOpenRecent(withTimeout(35_000) { repository.animeForRecent(item) }, item.episode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recentError = "Não foi possível abrir o lançamento. Tente novamente."
            } finally {
                openingRecent = null
            }
        }
    }
    LaunchedEffect(discovery) {
        while (true) { discovery.refresh(); delay(60_000) }
    }

    val featured = remember(disco.pages) { disco.pages.flatMap { it.featured }.distinctBy { "${it.sourceId}:${it.id}:${it.dubbed}" }.take(12) }
    val recent = remember(disco.pages) {
        val lists = disco.pages.map { it.recent }
        val interleaved = (0 until (lists.maxOfOrNull { it.size } ?: 0))
            .flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        groupRecentEpisodes(interleaved)
    }
    var recentSearchQuery by remember { mutableStateOf("") }
    var recentDubbedFilter by remember { mutableStateOf<Boolean?>(null) }
    val filteredRecent = remember(recent, recentSearchQuery, recentDubbedFilter) {
        var list = recent
        if (recentDubbedFilter != null) {
            list = list.filter { it.dubbed == recentDubbedFilter }
        }
        if (recentSearchQuery.isNotBlank()) {
            val q = recentSearchQuery.trim().lowercase()
            list = list.filter { it.title.lowercase().contains(q) || it.episodeTitle.lowercase().contains(q) }
        }
        list
    }
    var recentPage by remember { mutableIntStateOf(0) }
    val safeRecentPage = recentPage.coerceAtMost(((filteredRecent.size - 1) / 18).coerceAtLeast(0))
    val dubbed = remember(disco.pages) { disco.pages.flatMap { it.featured }.filter { it.dubbed }.distinctBy { "${it.sourceId}:${it.id}" }.take(12) }
    val continuing = remember(saved.progress) { saved.progress.filter { !it.completed && it.positionMs > 0 }.take(12) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        HomeStatusRow(
            updatedAt = disco.pages.minOfOrNull { it.updatedAt },
            loading = disco.loading,
            onRefresh = { scope.launch { discovery.refresh(force = true) } },
        )

        recentError?.let { HomeNotice(it, isError = true) }

        if (focus == HomeNavFocus.Home) HomeHero(
            profile = profile,
            featured = featured,
            repository = repository,
            onOpenAnime = onOpenAnime,
        )
        if (focus == HomeNavFocus.Home) AdBannerSlot(AdPlacement.Home)

        val summaryText = when (focus) {
            HomeNavFocus.Home -> "Seu espaço para descobrir histórias e continuar suas maratonas."
            HomeNavFocus.Trending -> "Descubra o que está chamando atenção na comunidade."
            HomeNavFocus.Releases -> "Lançamentos recentes e episódios atualizados direto do catálogo."
            HomeNavFocus.Categories -> "Escolha um gênero e encontre sua próxima história."
        }
        Text(
            summaryText,
            color = HomeTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(max = 720.dp),
        )

        if (disco.loading && disco.pages.isEmpty()) {
            HomeSkeleton(280.dp)
            HomeSkeleton(180.dp)
        }

        if (focus == HomeNavFocus.Home) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSectionHeader(
                    title = "Explore universos",
                    subtitle = "Escolha uma categoria",
                    action = {
                        TextButton(onClick = onTrending) {
                            Text("Ver em alta", color = HomeTextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceGenres.forEach { (label, value) -> HomeGenreChip(label) { onCategory(value) } }
                }
            }
        }
        if (focus == HomeNavFocus.Categories) GenreExplorer(repository, categorySlug, onCategory, onOpenAnime)
        if (focus == HomeNavFocus.Trending) TrendingExplorer(repository, onOpenAnime)

        if (continuing.isNotEmpty() && focus == HomeNavFocus.Home) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeSectionHeader(
                    title = "Continuar assistindo",
                    subtitle = "Sua última sessão está esperando",
                    action = {
                        TextButton(onClick = onLibrary) {
                            Text("Abrir biblioteca", color = HomeTextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )
                val railState = rememberLazyListState()
                RailControls(railState, continuing.size)
                LazyRow(
                    state = railState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(continuing) { item ->
                        ContinueCard(
                            item = item,
                            width = if (compact) 300.dp else 360.dp,
                            onContinue = { onContinue(item) },
                        )
                    }
                }
            }
        }

        if (featured.isNotEmpty() && focus == HomeNavFocus.Home) {
            PosterRail(
                title = "Em destaque",
                subtitle = "Seleção disponível nos servidores Space",
                items = if (focus == HomeNavFocus.Trending) featured else featured.take(10),
                compact = compact,
                onClick = onOpenAnime,
                action = {
                    TextButton(onClick = onTrending) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Explorar em alta", color = SpaceColors.secondary, style = MaterialTheme.typography.labelLarge)
                            SpaceIcon("arrow-right", null, tint = SpaceColors.secondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )
        }

        if (recent.isNotEmpty() && (focus == HomeNavFocus.Home || focus == HomeNavFocus.Releases)) {
            if (focus == HomeNavFocus.Releases) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = recentSearchQuery,
                            onValueChange = { recentSearchQuery = it; recentPage = 0 },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("Filtrar lançamentos por nome…", maxLines = 1) },
                            leadingIcon = { SpaceIcon("search", null, tint = SpaceColors.secondary, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (recentSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { recentSearchQuery = ""; recentPage = 0 }) {
                                        SpaceIcon("x", "Limpar", tint = SpaceColors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpaceColors.primary,
                                unfocusedBorderColor = SpaceBorderColor,
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = SpaceColors.secondary,
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GenreChip("Todos (${recent.size})", recentDubbedFilter == null) { recentDubbedFilter = null; recentPage = 0 }
                        GenreChip("Dublados", recentDubbedFilter == true) { recentDubbedFilter = true; recentPage = 0 }
                        GenreChip("Legendados", recentDubbedFilter == false) { recentDubbedFilter = false; recentPage = 0 }
                    }
                }
            }

            val itemsToShow = if (focus == HomeNavFocus.Releases) filteredRecent.drop(safeRecentPage * 18).take(18) else filteredRecent.take(12)
            RecentRail(
                title = if (focus == HomeNavFocus.Releases) "Todos os Lançamentos Recentes" else "Episódios recentes",
                subtitle = if (openingRecent != null) "Abrindo episódio selecionado…" else if (focus == HomeNavFocus.Releases) "${filteredRecent.size} episódios encontrados sem repetições" else "Novidades atualizadas nos servidores em português",
                items = itemsToShow,
                compact = compact,
                action = {
                    if (focus == HomeNavFocus.Home) {
                        TextButton(onClick = onSearch) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Explorar catálogo", color = SpaceColors.secondary, style = MaterialTheme.typography.labelLarge)
                                SpaceIcon("arrow-right", null, tint = SpaceColors.secondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                },
                onOpen = { release ->
                    if (release.candidates.size > 1) selectedRelease = release
                    else release.candidates.firstOrNull()?.let(::openRecent)
                },
            )
            if (focus == HomeNavFocus.Releases && filteredRecent.size > 18) {
                val totalPages = (filteredRecent.size + 17) / 18
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        enabled = safeRecentPage > 0,
                        onClick = { recentPage = safeRecentPage - 1 },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SpaceBorderColor)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpaceIcon("chevron-left", null, tint = if (safeRecentPage > 0) Color.White else HomeTextMuted, modifier = Modifier.size(14.dp))
                            Text("Anterior")
                        }
                    }
                    Text(
                        "Página ${safeRecentPage + 1} de $totalPages",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        enabled = (safeRecentPage + 1) * 18 < filteredRecent.size,
                        onClick = { recentPage = safeRecentPage + 1 },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SpaceBorderColor)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Próxima")
                            SpaceIcon("chevron-right", null, tint = if ((safeRecentPage + 1) * 18 < filteredRecent.size) Color.White else HomeTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        if (dubbed.isNotEmpty() && focus == HomeNavFocus.Home) {
            PosterRail(
                title = "Dublados em destaque",
                subtitle = "Para quem prefere áudio em português",
                items = dubbed,
                compact = compact,
                onClick = onOpenAnime,
                action = {
                    TextButton(onClick = onSearch) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ver tudo", color = SpaceColors.secondary, style = MaterialTheme.typography.labelLarge)
                            SpaceIcon("arrow-right", null, tint = SpaceColors.secondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )
        }

        disco.message?.let { message -> HomeNotice(message) }
        saved.error?.let { message -> HomeNotice(message, isError = true) }
    }
    selectedRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { selectedRelease = null },
            title = { Text("Escolha um servidor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    release.candidates.forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                selectedRelease = null
                                openRecent(candidate)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = openingRecent == null
                        ) {
                            Text("${serverName(candidate.sourceId)} · ${if (candidate.dubbed) "Dublado" else "Legendado"}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRelease = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HomeStatusRow(updatedAt: Long?, loading: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulseDot(Color(0xFF10B981), size = 7.dp)
            Text(
                "Servidores Space 1 & 2 Online",
                color = Color(0xFF10B981),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("•", color = HomeTextMuted)
        Text(
            updatedAt?.let { "Catálogo sincronizado às ${discoveryTime(it)}" } ?: "Buscando novidades…",
            color = HomeTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(enabled = !loading, onClick = onRefresh) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaceIcon("refresh-cw", null, tint = if (loading) HomeTextMuted else SpaceColors.secondary, modifier = Modifier.size(13.dp))
                Text(
                    if (loading) "Sincronizando…" else "Atualizar lançamentos",
                    color = if (loading) HomeTextMuted else SpaceColors.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpaceColors.primary)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = HomeTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HomeTextMuted)
                }
            }
        }
        action?.invoke()
    }
}

@Composable
private fun HomeMeta(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, HomeHairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            color = HomeTextSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeGenreChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = SpaceColors.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SpaceColors.primary.copy(alpha = 0.28f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = HomeTextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HomeNotice(message: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) Color(0xFF2B1120) else Color(0xFF1B1032),
        border = BorderStroke(1.dp, if (isError) SpaceColors.error.copy(alpha = 0.25f) else HomeHairline),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) SpaceColors.error else HomeTextSecondary,
        )
    }
}

@Composable
private fun HomeSkeleton(height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(HomeCardShape)
            .background(spaceShimmerBrush())
    )
}

@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = HomeCardShape,
        color = HomeSurface,
        border = BorderStroke(1.dp, HomeHairline),
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ContinueCard(item: WatchProgress, width: Dp, onContinue: () -> Unit) {
    HomeCard(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(104.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomeSurfaceHigh)
            ) {
                Artwork(
                    item.context.anime.image,
                    item.context.anime.title,
                    Modifier.fillMaxSize(),
                )
                Box(Modifier.matchParentSize().background(HomeScrim))
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.context.anime.title,
                    color = HomeTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.context.episode.title,
                    color = HomeTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(playbackTime(item.positionMs), color = HomeTextSecondary, style = MaterialTheme.typography.bodySmall)
                    HomeMeta(
                        if (item.context.anime.dubbed) "Dublado" else "Legendado",
                        if (item.context.anime.dubbed) SpaceColors.tertiary else SpaceColors.secondary,
                    )
                }
            }
            Surface(
                onClick = onContinue,
                shape = CircleShape,
                color = HomeCta,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Continuar ${item.context.anime.title}" },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SpaceIcon("play", null, Modifier.size(18.dp), HomeCtaText, glow = false)
                }
            }
        }
        LinearProgressIndicator(
            progress = { (item.positionMs.toFloat() / item.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
            color = HomeAccent,
            trackColor = Color(0x33FFFFFF),
        )
    }
}

@Composable
private fun HomeHero(
    profile: LocalProfile,
    featured: List<AnimeResult>,
    repository: SourceRepository,
    onOpenAnime: (AnimeResult) -> Unit,
) {
    var index by remember(featured) { mutableIntStateOf(0) }
    val current = featured.getOrNull(index.coerceIn(0, (featured.size - 1).coerceAtLeast(0)))
    var synopsis by remember(current) { mutableStateOf<String?>(null) }
    var heroCover by remember(current) { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        val anime = current ?: return@LaunchedEffect
        try { heroCover = repository.highResolutionCover(anime)?.let { safeArtworkUrl(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { heroCover = null }
    }
    var synopsisLoading by remember(current) { mutableStateOf(current != null) }
    LaunchedEffect(current) {
        val anime = current ?: return@LaunchedEffect
        try {
            delay(400)
            synopsis = repository.synopsis(anime)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { synopsis = null }
        finally { synopsisLoading = false }
    }

    if (current != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = HomeSurface,
            contentColor = HomeTextPrimary,
            border = BorderStroke(1.dp, HomeHairline),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val narrow = maxWidth < 700.dp
                val heroHeight = if (narrow) 400.dp else 420.dp
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    Artwork(
                        model = heroCover ?: current.image,
                        fallbackModel = current.image,
                        contentDescription = current.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF2020617), Color(0xD90F172A), Color(0x400F172A))
                            )
                        )
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                listOf(Color(0x33020617), Color(0x99020617), Color(0xF2020617))
                            )
                        )
                    )
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(if (narrow) 20.dp else 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeMeta("Olá, ${profile.name}", SpaceColors.secondary)
                                HomeMeta(
                                    if (current.dubbed) "Dublado" else "Legendado",
                                    if (current.dubbed) SpaceColors.tertiary else SpaceColors.secondary,
                                )
                            }
                            Crossfade(targetState = current.title, animationSpec = tween(if (LocalSpaceMotionEnabled.current) 240 else 0), label = "hero-title") { title ->
                                Text(
                                    title,
                                    style = if (narrow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                                    color = HomeTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.5).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                synopsis ?: if (synopsisLoading) "Carregando sinopse…" else "Sinopse indisponível para este título.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HomeTextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 620.dp),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onOpenAnime(current) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = HomeCta, contentColor = HomeCtaText),
                                ) {
                                    SpaceIcon("play", null, Modifier.size(16.dp), HomeCtaText, glow = false)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Assistir agora", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { onOpenAnime(current) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HomeHairline),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeTextPrimary),
                                ) {
                                    Text("Mais informações")
                                }
                            }
                        }
                        if (!narrow) {
                            Artwork(
                                model = heroCover ?: current.image,
                                fallbackModel = current.image,
                                contentDescription = current.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(168.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, HomeHairline, RoundedCornerShape(16.dp)),
                            )
                        }
                    }
                    if (featured.size > 1) {
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (narrow) 16.dp else 20.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xB3020617))
                                .border(1.dp, HomeHairline, RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                featured.take(5).forEachIndexed { i, _ ->
                                    val isCurrent = i == index
                                    Box(
                                        Modifier
                                            .width(if (isCurrent) 14.dp else 6.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isCurrent) SpaceColors.primary else SpaceColors.onSurfaceVariant.copy(alpha = 0.4f))
                                    )
                                }
                            }
                            Text("${index + 1}/${featured.size}", color = HomeTextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            SmallCircleAction("chevron-left", "Anterior", enabled = index > 0) { index-- }
                            SmallCircleAction("chevron-right", "Próximo", enabled = index < featured.lastIndex) { index++ }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRail(
    title: String,
    subtitle: String,
    items: List<AnimeResult>,
    compact: Boolean,
    action: (@Composable () -> Unit)? = null,
    onClick: (AnimeResult) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(title, subtitle, action)
        val railState = rememberLazyListState()
        RailControls(railState, items.size)
        LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(items) { anime ->
                HomePosterCard(
                    anime = anime,
                    modifier = Modifier.width(if (compact) 124.dp else 146.dp),
                    onClick = { onClick(anime) },
                )
            }
        }
    }
}

@Composable
private fun HomePosterCard(anime: AnimeResult, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val motion = LocalSpaceMotionEnabled.current
    val borderColor by animateColorAsState(
        if (focused || hovered) Color(0x33FFFFFF) else HomeHairline,
        animationSpec = tween(if (motion) 160 else 0),
        label = "poster-border",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HomeSurfaceHigh)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Artwork(anime.image, anime.title, Modifier.fillMaxSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66020617)))))
                HomeMeta(
                    if (anime.dubbed) "Dublado" else "Legendado",
                    if (anime.dubbed) SpaceColors.tertiary else SpaceColors.secondary,
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Text(
                anime.title,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                style = MaterialTheme.typography.titleSmall,
                color = HomeTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                serverName(anime.sourceId),
                modifier = Modifier.fillMaxWidth().height(18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentRail(
    title: String,
    subtitle: String,
    items: List<RecentRelease>,
    compact: Boolean,
    action: (@Composable () -> Unit)? = null,
    onOpen: (RecentRelease) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(title, subtitle, action)
        val railState = rememberLazyListState()
        RailControls(railState, items.size)
        LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(items) { item ->
                Surface(
                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (compact) 124.dp else 146.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(HomeSurfaceHigh)
                                .border(1.dp, HomeHairline, RoundedCornerShape(16.dp))
                        ) {
                            if (item.image.isNotBlank()) {
                                Artwork(
                                    model = item.image,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99020617)))))
                            // Badge de episódio no topo esquerdo
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(HomeCta.copy(alpha = 0.90f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    item.episodeTitle,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Dubbed badge bottom end
                            HomeMeta(
                                if (item.dubbed) "Dublado" else "Legendado",
                                if (item.dubbed) SpaceColors.tertiary else SpaceColors.secondary,
                                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            )
                        }
                        Text(
                            item.title,
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            color = HomeTextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (item.candidates.size > 1) "${item.candidates.size} servidores disponíveis" else serverName(item.candidates.first().sourceId),
                            modifier = Modifier.fillMaxWidth().height(18.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailControls(state: LazyListState, totalItems: Int) {
    val scope = rememberCoroutineScope()
    val motion = LocalSpaceMotionEnabled.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallCircleAction("chevron-left", "Anterior", state.canScrollBackward) {
            scope.launch { val target = (state.firstVisibleItemIndex - 3).coerceAtLeast(0); if (motion) state.animateScrollToItem(target) else state.scrollToItem(target) }
        }
        SmallCircleAction("chevron-right", "Próximo", state.canScrollForward) {
            scope.launch { val target = (state.firstVisibleItemIndex + 3).coerceAtMost((totalItems - 1).coerceAtLeast(0)); if (motion) state.animateScrollToItem(target) else state.scrollToItem(target) }
        }
    }
}

@Composable
private fun SmallCircleAction(icon: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val motion = LocalSpaceMotionEnabled.current
    val backgroundColor by animateColorAsState(
        if (hovered && enabled) Color(0x14FFFFFF) else Color.Transparent,
        animationSpec = tween(if (motion) 160 else 0),
        label = "rail-bg",
    )
    val borderColor by animateColorAsState(
        if (hovered && enabled) Color(0x33FFFFFF) else HomeHairline,
        animationSpec = tween(if (motion) 160 else 0),
        label = "rail-border",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SpaceIcon(
                icon,
                null,
                Modifier.size(18.dp),
                if (enabled) HomeTextSecondary else HomeTextMuted.copy(alpha = 0.5f),
                glow = false,
            )
        }
    }
}
