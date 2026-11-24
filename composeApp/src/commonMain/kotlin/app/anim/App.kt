package app.anim

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.auth.AccountScreen
import app.anim.auth.rememberAccountState
import app.anim.calendar.CalendarScreen
import app.anim.comments.rememberComments
import app.anim.design.*
import app.anim.library.LibraryScreen
import app.anim.library.WatchProgress
import app.anim.library.rememberMangaFollowStore
import app.anim.library.progressFor
import app.anim.library.rememberLibraryStore
import app.anim.library.resumePosition
import app.anim.manga.MangaScreen
import app.anim.news.NewsScreen
import app.anim.player.PlaybackSource
import app.anim.player.PlayerScreen
import app.anim.player.EpisodeQueue
import app.anim.profile.LocalProfile
import app.anim.profile.ProfilePicture
import app.anim.profile.ProfileScreen
import app.anim.profile.ProfileTab
import app.anim.profile.rememberProfiles
import app.anim.sources.AnimeResult
import app.anim.sources.SourceBrowserState
import app.anim.sources.SourcesScreen
import app.anim.sources.createSourceRepository
import app.anim.update.AppRelease
import app.anim.update.MandatoryUpdateScreen
import app.anim.update.APP_VERSION_CODE
import app.anim.update.UPDATE_MANIFEST_URL
import app.anim.update.UpdateCard
import app.anim.update.createUpdateClient
import app.anim.update.rememberRequiredReleaseStore
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class SpaceScreen { Home, Explore, Manga, Calendar, News, Library, Profile }
internal enum class HomeNavFocus { Home, Trending, Releases, Categories }

private data class NavEntry(
    val title: String,
    val icon: String,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun App(onFullScreenChange: (Boolean) -> Unit = {}) {
    remember { configureArtwork(); true }
    val accountState = rememberAccountState()
    // Sends the launch telemetry event (opt-out in Perfil > Privacidade).
    app.anim.telemetry.rememberTelemetry()
    val profiles = rememberProfiles()
    val profileState by profiles.state.collectAsState()
    val repository = remember { createSourceRepository() }
    val updater = remember { createUpdateClient() }
    val requiredReleaseStore = rememberRequiredReleaseStore()
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var checking by remember { mutableStateOf(false) }
    var requiredReleaseStoreReady by remember { mutableStateOf(false) }
    var initialUpdateCheck by remember { mutableStateOf(true) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateStorageError by remember { mutableStateOf<String?>(null) }

    fun checkUpdates() {
        if (checking || !requiredReleaseStoreReady || (updateStorageError != null && release == null)) return
        checking = true
        scope.launch {
            try {
                val checked = try {
                    updater.check()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    updateMessage = "Não foi possível consultar atualizações."
                    return@launch
                }
                val nextRequirement = app.anim.update.updateRequirementAfterCheck(
                    APP_VERSION_CODE,
                    release,
                    checked,
                )
                release = nextRequirement
                try {
                    if (nextRequirement == null) requiredReleaseStore.clear()
                    else requiredReleaseStore.save(nextRequirement)
                    updateStorageError = null
                    updateMessage = if (nextRequirement == null) "Você está na versão mais recente." else null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    updateStorageError = if (nextRequirement != null) {
                        "A atualização obrigatória foi detectada, mas não foi possível salvar a exigência neste dispositivo. O bloqueio continuará ativo nesta sessão."
                    } else {
                        "Não foi possível atualizar o armazenamento local das atualizações."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                checking = false
                initialUpdateCheck = false
            }
        }
    }

    LaunchedEffect(requiredReleaseStore) {
        try {
            val stored = requiredReleaseStore.load()
            val cachedRequirement = app.anim.update.requiredReleaseForStartup(APP_VERSION_CODE, stored)
            release = cachedRequirement
            if (stored != null && cachedRequirement == null) requiredReleaseStore.clear()
            requiredReleaseStoreReady = true
            if (UPDATE_MANIFEST_URL.isBlank()) initialUpdateCheck = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            updateStorageError = "Não foi possível ler a exigência de atualização salva neste dispositivo. O app permanecerá bloqueado até o armazenamento ser verificado."
            requiredReleaseStoreReady = true
            initialUpdateCheck = false
        }
    }

    fun retryRequiredReleaseStore() {
        if (checking) return
        scope.launch {
            try {
                val stored = requiredReleaseStore.load()
                val cachedRequirement = app.anim.update.requiredReleaseForStartup(APP_VERSION_CODE, stored)
                if (stored != null && cachedRequirement == null) requiredReleaseStore.clear()
                release = cachedRequirement
                updateStorageError = null
                initialUpdateCheck = UPDATE_MANIFEST_URL.isNotBlank()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateStorageError = "Não foi possível ler ou limpar a exigência de atualização salva. Tente novamente."
            }
        }
    }

    LaunchedEffect(requiredReleaseStoreReady, updateStorageError) {
        if (!requiredReleaseStoreReady || updateStorageError != null || UPDATE_MANIFEST_URL.isBlank()) return@LaunchedEffect
        checkUpdates()
        while (true) {
            delay(6 * 60 * 60 * 1000L)
            checkUpdates()
        }
    }

    MaterialTheme(
        colorScheme = SpaceColors,
        typography = spaceTypography(),
        shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
    ) {
        CompositionLocalProvider(LocalSpaceMotionEnabled provides !profileState.active.reduceMotion, LocalContentColor provides SpaceColors.onSurface) {
        if (initialUpdateCheck) {
            Surface(Modifier.fillMaxSize(), color = SpaceColors.background, contentColor = SpaceColors.onSurface) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpaceLoader(size = 150.dp)
                        Text("Sincronizando com o SpaceAnime…", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (release != null) {
            MandatoryUpdateScreen(
                release = release!!,
                checking = checking,
                message = updateStorageError ?: updateMessage,
                onCheck = ::checkUpdates
            )
        } else if (updateStorageError != null) {
            Surface(Modifier.fillMaxSize(), color = SpaceColors.background, contentColor = SpaceColors.onSurface) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Não foi possível verificar as atualizações salvas.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(updateStorageError!!, color = SpaceColors.error)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = ::retryRequiredReleaseStore, enabled = !checking) {
                        Text(if (checking) "Verificando…" else "Tentar novamente")
                    }
                }
            }
        } else if (!accountState.isAuthenticated) {
            Surface(Modifier.fillMaxSize(), color = SpaceColors.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.Center) {
                    AccountScreen(accountState, profiles, onBack = {}, mandatory = true)
                }
            }
        } else key(profileState.activeId) {
            var screen by remember { mutableStateOf(SpaceScreen.Home) }
            var homeFocus by remember { mutableStateOf(HomeNavFocus.Home) }
            var categorySlug by remember { mutableStateOf("acao") }
            var profileTab by remember { mutableStateOf(ProfileTab.History) }
            var source by remember { mutableStateOf<PlaybackSource?>(null) }
            var playerQueue by remember { mutableStateOf<EpisodeQueue?>(null) }
            var playerNavigationError by remember { mutableStateOf<String?>(null) }
            var headerQuery by remember { mutableStateOf("") }
            var mangaSearch by remember { mutableStateOf("") }
            val browser = remember { SourceBrowserState() }
            val comments = rememberComments(profileState.activeId)
            val sessionScope = rememberCoroutineScope()
            var opening by remember { mutableStateOf(false) }
            var openingError by remember { mutableStateOf<String?>(null) }
            val library = rememberLibraryStore(profileState.activeId)
            val mangaFollows = rememberMangaFollowStore(profileState.activeId)
            val saved by library.state.collectAsState()

            fun play(request: PlaybackSource, queue: EpisodeQueue? = null, queueWarning: String? = null) {
                val savedPosition = request.context?.let { library.state.value.progressFor(it)?.resumePosition() } ?: 0
                val position = app.anim.player.startPosition(request, savedPosition)
                playerQueue = queue
                playerNavigationError = queueWarning
                source = request.copy(startPositionMs = position)
            }

            fun resume(progress: WatchProgress) {
                if (opening) return
                opening = true; openingError = null
                sessionScope.launch {
                    try {
                        var queueWarning: String? = null
                        // Resolve the server while the episode list loads: both are needed, neither waits for the other.
                        val streamJob = async {
                            withTimeout(120_000) {
                                repository.quickStream(progress.context.anime, progress.context.episode)?.source
                                    ?: throw IllegalStateException("Nenhuma opção de reprodução foi encontrada.")
                            }
                        }
                        val episodes = try {
                            withTimeout(120_000) { repository.episodes(progress.context.anime) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            queueWarning = "Não foi possível carregar a lista completa de episódios."
                            emptyList()
                        }
                        val queueEpisodes = if (episodes.any { it.id == progress.context.episode.id }) episodes
                        else episodes + progress.context.episode
                        val stream = streamJob.await()
                        play(stream, EpisodeQueue(progress.context.anime, queueEpisodes, progress.context.episode.id), queueWarning)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { openingError = "Não foi possível abrir o episódio. Tente novamente pela biblioteca." }
                    finally { opening = false }
                }
            }

            fun navigateEpisode(episode: app.anim.sources.Episode) {
                val queue = playerQueue ?: return
                if (opening) return
                opening = true
                playerNavigationError = null
                sessionScope.launch {
                    try {
                        val stream = withTimeout(120_000) {
                            repository.quickStream(queue.anime, episode)?.source
                                ?: throw IllegalStateException("Nenhuma opção de reprodução foi encontrada.")
                        }
                        play(stream, queue.select(episode))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        playerNavigationError = failure.message
                            ?: "Não foi possível abrir o episódio selecionado."
                    } finally {
                        opening = false
                    }
                }
            }

            fun openAnime(anime: AnimeResult) {
                browser.anime = anime
                browser.sourceId = anime.sourceId
                browser.episodes = emptyList()
                browser.episode = null
                browser.streams = emptyList()
                browser.error = null
                browser.pendingEpisode = null
                screen = SpaceScreen.Explore
            }

            if (source != null) {
                val active = source!!
                Surface(color = Color.Black, contentColor = SpaceColors.onSurface) {
                    PlayerScreen(
                        source = active,
                        queue = playerQueue,
                        navigationLoading = opening,
                        navigationError = playerNavigationError,
                        onEpisodeChange = ::navigateEpisode,
                        autoPlayNext = profileState.active.autoPlayNext,
                        onHome = { source = null; playerQueue = null; screen = SpaceScreen.Home; homeFocus = HomeNavFocus.Home },
                        onBack = { source = null; playerQueue = null },
                        onFullScreenChange = onFullScreenChange,
                        onProgress = { playback -> active.context?.let { library.record(it, playback) } },
                    )
                }
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF05030C), Color(0xFF030920), Color(0xFF05030C))
                            )
                        )
                        .safeDrawingPadding()
                ) {
                    val expandedSidebar = maxWidth >= 1280.dp
                    val compactSidebar = maxWidth >= 1024.dp
                    val showSidebar = compactSidebar
                    val contentPadding = if (maxWidth >= 1280.dp) 30.dp else 18.dp

                    val activeSidebarKey = when {
                        screen == SpaceScreen.Home && homeFocus == HomeNavFocus.Trending -> "Em Alta"
                        screen == SpaceScreen.Home && homeFocus == HomeNavFocus.Releases -> "Lançamentos"
                        screen == SpaceScreen.Home && homeFocus == HomeNavFocus.Categories -> "Categorias"
                        screen == SpaceScreen.Home -> "Início"
                        screen == SpaceScreen.Explore -> "Explorar"
                        screen == SpaceScreen.Manga -> "Mangás"
                        screen == SpaceScreen.News -> "Notícias"
                        screen == SpaceScreen.Calendar -> "Calendário"
                        screen == SpaceScreen.Library -> "Minha Lista"
                        screen == SpaceScreen.Profile && profileTab == ProfileTab.History -> "Histórico"
                        screen == SpaceScreen.Profile && profileTab == ProfileTab.Settings -> "Configurações"
                        screen == SpaceScreen.Profile && profileTab == ProfileTab.Plans -> "VIP"
                        screen == SpaceScreen.Profile && profileTab == ProfileTab.Account -> "Perfil"
                        screen == SpaceScreen.Profile -> "Perfil"
                        else -> "Player"
                    }

                    Row(Modifier.fillMaxSize()) {
                        if (showSidebar) {
                            SpaceSidebar(
                                expanded = expandedSidebar,
                                activeKey = activeSidebarKey,
                                profile = profileState.active,
                                avatarId = accountState.avatarAsset,
                                onHome = { screen = SpaceScreen.Home; homeFocus = HomeNavFocus.Home },
                                onExplore = { screen = SpaceScreen.Explore },
                                onManga = { mangaSearch = ""; screen = SpaceScreen.Manga },
                                onNews = { screen = SpaceScreen.News },
                                onTrending = { screen = SpaceScreen.Home; homeFocus = HomeNavFocus.Trending },
                                onReleases = { screen = SpaceScreen.Home; homeFocus = HomeNavFocus.Releases },
                                onCalendar = { screen = SpaceScreen.Calendar },
                                onCategories = { screen = SpaceScreen.Home; homeFocus = HomeNavFocus.Categories },
                                onLibrary = { screen = SpaceScreen.Library },
                                onHistory = { screen = SpaceScreen.Profile; profileTab = ProfileTab.History },
                                onSettings = { screen = SpaceScreen.Profile; profileTab = ProfileTab.Settings },
                                onVip = { screen = SpaceScreen.Profile; profileTab = ProfileTab.Plans },
                                onProfile = { screen = SpaceScreen.Profile; profileTab = ProfileTab.History },

                            )
                        }

                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (screen != SpaceScreen.Profile) SpaceHeader(
                                compact = !showSidebar,
                                showSearch = screen == SpaceScreen.Home || screen == SpaceScreen.Explore || screen == SpaceScreen.Calendar,
                                profile = profileState.active,
                                avatarId = accountState.avatarAsset,
                                query = headerQuery,
                                hasNotification = release != null,
                                onQueryChange = { headerQuery = it.take(120) },
                                onSearch = {
                                    if (screen == SpaceScreen.Manga) { mangaSearch = headerQuery.trim() } else {
                                        browser.anime = null
                                        browser.query = headerQuery.trim()
                                        screen = SpaceScreen.Explore
                                    }
                                },
                                onNotification = {
                                    screen = SpaceScreen.Profile
                                    profileTab = ProfileTab.Settings
                                },
                                onProfile = {
                                    screen = SpaceScreen.Profile
                                    profileTab = ProfileTab.History
                                },
                            )

                            key(screen, homeFocus) {
                            val contentScroll = rememberScrollState()
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(contentScroll)
                                    .padding(horizontal = contentPadding, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                if (release != null && screen != SpaceScreen.Profile) {
                                    SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF22123A)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x1AE879F9)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("✦", color = SpaceColors.tertiary, fontSize = 18.sp)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text("Nova versão ${release!!.version} disponível", color = Color.White, fontWeight = FontWeight.SemiBold)
                                                Text("Abra o perfil para revisar novidades e atualização.", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = {
                                                screen = SpaceScreen.Profile
                                                profileTab = ProfileTab.Settings
                                            }) {
                                                Text("Ver", color = SpaceColors.secondary)
                                            }
                                        }
                                    }
                                }

                                if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
                                openingError?.let { Text(it, color = SpaceColors.error) }
                                Crossfade(
                                    targetState = screen,
                                    animationSpec = tween(if (profileState.active.reduceMotion) 0 else 220),
                                    label = "screen-transition"
                                ) { currentScreen ->
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        when (currentScreen) {
                                            SpaceScreen.Home -> SpaceHomeScreen(
                                                profile = profileState.active,
                                                saved = saved,
                                                repository = repository,
                                                compact = !expandedSidebar,
                                                focus = homeFocus,
                                                categorySlug = categorySlug,
                                                onCategory = { categorySlug = it; homeFocus = HomeNavFocus.Categories },
                                                onTrending = { homeFocus = HomeNavFocus.Trending },
                                                onReleases = { homeFocus = HomeNavFocus.Releases },
                                                onSearch = { screen = SpaceScreen.Explore },
                                                onLibrary = { screen = SpaceScreen.Library },
                                                onOpenAnime = ::openAnime,
                                                onContinue = ::resume,
                                                onOpenRecent = { anime, episode -> openAnime(anime); browser.pendingEpisode = episode },
                                            )

                                            SpaceScreen.Explore -> SourcesScreen(
                                                browser = browser,
                                                repository = repository,
                                                library = library,
                                                onPlay = { request, queue -> play(request, queue) },
                                                comments = comments,
                                                author = profileState.active.name,
                                            )

                                            SpaceScreen.Manga -> MangaScreen(mangaSearch, contentScroll, profileState.activeId)
                                            SpaceScreen.News -> NewsScreen(contentScroll)

                                            SpaceScreen.Calendar -> CalendarScreen { title ->
                                                headerQuery = title
                                                browser.anime = null
                                                browser.query = title
                                                screen = SpaceScreen.Explore
                                            }

                                            SpaceScreen.Library -> LibraryScreen(
                                                library,
                                                mangaFollows,
                                                repository,
                                                onOpen = ::openAnime,
                                                onOpenManga = { title -> mangaSearch = title; screen = SpaceScreen.Manga },
                                                onPlay = { request, queue, warning -> play(request, queue, warning) },
                                            )
                                            SpaceScreen.Profile -> {
                                                ProfileScreen(
                                                    profiles = profiles,
                                                    library = library,
                                                    mangaFollows = mangaFollows,
                                                    initialTab = profileTab,
                                                    accountState = accountState,
                                                    release = release,
                                                    checking = checking,
                                                    updateMessage = updateMessage,
                                                    onCheckUpdates = ::checkUpdates,
                                                    onOpenAnime = ::openAnime,
                                                    onContinue = ::resume,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            }
                            if (!showSidebar) {
                                NavigationBar(
                                    containerColor = SpaceSidebarColor,
                                    tonalElevation = 0.dp,
                                ) {
                                    listOf(
                                        Triple("Início", "⌂", SpaceScreen.Home),
                                        Triple("Explorar", "⌕", SpaceScreen.Explore),
                                        Triple("Mangás", "▤", SpaceScreen.Manga),
                                        Triple("Agenda", "⬢", SpaceScreen.Calendar),
                                        Triple("Notícias", "◌", SpaceScreen.News),
                                        Triple("Lista", "▤", SpaceScreen.Library),
                                        Triple("Perfil", "◉", SpaceScreen.Profile),
                                    ).forEach { (label, icon, target) ->
                                        NavigationBarItem(
                                            selected = screen == target,
                                            onClick = {
                                                screen = target
                                                if (target == SpaceScreen.Profile) profileTab = ProfileTab.History
                                                if (target == SpaceScreen.Home) homeFocus = HomeNavFocus.Home
                                            },
                                            icon = { SpaceIcon(navIcon(label), label, tint = SpaceColors.onSurface) },
                                            label = { Text(label, maxLines = 1) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = Color.White,
                                                indicatorColor = SpaceColors.primary.copy(alpha = 0.2f),
                                                unselectedIconColor = SpaceColors.onSurfaceVariant,
                                                unselectedTextColor = SpaceColors.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

}

@Composable
private fun SpaceSidebar(
    expanded: Boolean,
    activeKey: String,
    profile: LocalProfile,
    avatarId: String,
    onHome: () -> Unit,
    onExplore: () -> Unit,
    onManga: () -> Unit,
    onNews: () -> Unit,
    onTrending: () -> Unit,
    onReleases: () -> Unit,
    onCalendar: () -> Unit,
    onCategories: () -> Unit,
    onLibrary: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onVip: () -> Unit,
    onProfile: () -> Unit,

) {
    val topEntries = listOf(
        NavEntry("Início", "⌂", active = activeKey == "Início", onClick = onHome),
        NavEntry("Explorar", "⌕", active = activeKey == "Explorar", onClick = onExplore),
        NavEntry("Mangás", "▤", active = activeKey == "Mangás", onClick = onManga),
        NavEntry("Notícias", "◌", active = activeKey == "Notícias", onClick = onNews),
        NavEntry("Em Alta", "✦", active = activeKey == "Em Alta", onClick = onTrending),
        NavEntry("Lançamentos", "⬢", active = activeKey == "Lançamentos", onClick = onReleases),
        NavEntry("Calendário", "⬢", active = activeKey == "Calendário", onClick = onCalendar),
        NavEntry("Categorias", "◈", active = activeKey == "Categorias", onClick = onCategories),
        NavEntry("Minha Lista", "▤", active = activeKey == "Minha Lista", onClick = onLibrary),
        NavEntry("Histórico", "↺", active = activeKey == "Histórico", onClick = onHistory),

    )
    val bottomEntries = listOf(
        NavEntry("VIP", "✦", active = activeKey == "VIP", onClick = onVip),
        NavEntry("Configurações", "⚙", active = activeKey == "Configurações", onClick = onSettings),
        NavEntry("Perfil", "◉", active = activeKey == "Perfil", onClick = onProfile),

    )

    Column(
        Modifier
            .fillMaxHeight()
            .width(if (expanded) 240.dp else 92.dp)
            .background(SpaceSidebarColor)
            .border(1.dp, SpaceHeaderBorder)
            .padding(horizontal = if (expanded) 16.dp else 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (expanded) SpaceBrand() else SpaceLogo(Modifier.size(42.dp).align(Alignment.CenterHorizontally))

        SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF120B20), contentPadding = PaddingValues(12.dp)) {
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SERVIDORES SPACE", style = MaterialTheme.typography.labelSmall, color = SpaceColors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(Color(0xFF10B981), size = 6.dp)
                        Text("ONLINE", fontSize = 9.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceBadge("Space 1", SpaceColors.primary)
                    SpaceBadge("Space 2", SpaceColors.secondary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PulseDot(Color(0xFF10B981), size = 7.dp)
                    Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (expanded) app.anim.premium.VipPill(onClick = onVip, modifier = Modifier.align(Alignment.CenterHorizontally))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SidebarSection("Catálogo", expanded, topEntries)
        }

        SidebarSection("Conta", expanded, bottomEntries)

    }
}

@Composable
private fun SidebarSection(title: String, expanded: Boolean, items: List<NavEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (expanded) {
            Text(title.uppercase(), color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        items.forEach { item ->
            Surface(
                onClick = item.onClick,
                enabled = item.enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (item.active) SpaceColors.primary.copy(alpha = 0.22f) else Color.Transparent,
                border = if (item.active) BorderStroke(1.dp, SpaceColors.primary.copy(alpha = 0.45f)) else null,
            ) {
                Row(
                    Modifier.padding(horizontal = if (expanded) 14.dp else 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SpaceIcon(navIcon(item.title), item.title, Modifier.size(22.dp), if (item.active) Color.White else SpaceColors.onSurfaceVariant)
                    if (expanded) {
                        Text(
                            item.title,
                            modifier = Modifier.weight(1f),
                            color = if (item.enabled) (if (item.active) Color.White else SpaceColors.onSurfaceVariant) else SpaceColors.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = if (item.active) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (item.active) Box(Modifier.size(7.dp).clip(CircleShape).background(SpaceColors.secondary))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceHeader(
    compact: Boolean,
    showSearch: Boolean,
    profile: LocalProfile,
    avatarId: String,
    query: String,
    hasNotification: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNotification: () -> Unit,
    onProfile: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xEE05030C),
        border = BorderStroke(1.dp, SpaceHeaderBorder),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compact) {
                SpaceBrand(compact = true)
            }
            if (showSearch) OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Buscar anime", maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                leadingIcon = { SpaceIcon("search", null, tint = SpaceColors.secondary) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onSearch) { SpaceIcon("search", "Buscar", tint = SpaceColors.secondary) }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpaceColors.primary,
                    unfocusedBorderColor = SpaceBorderColor,
                    focusedContainerColor = Color(0xFF120B20),
                    unfocusedContainerColor = Color(0xFF120B20),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SpaceColors.secondary,
                ),
            ) else Spacer(Modifier.weight(1f))

            Surface(
                onClick = onNotification,
                shape = CircleShape,
                color = Color(0xFF120B20),
                border = BorderStroke(1.dp, if (hasNotification) SpaceColors.tertiary.copy(alpha = 0.5f) else SpaceBorderColor),
                modifier = Modifier.size(42.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SpaceIcon("bell", "Atualizações", tint = if (hasNotification) SpaceColors.tertiary else SpaceColors.onSurfaceVariant)
                }
            }
            Surface(
                onClick = onProfile,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF120B20),
                border = BorderStroke(1.dp, SpaceBorderColor),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfilePicture(avatarId, profile.name, profile.color, 32.dp)
                    if (!compact) {
                        Column {
                            Text(profile.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("@${profile.username}", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCard() {
    SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF120B20)) {
        Text("Privacidade e segurança", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Perfis e biblioteca ficam neste dispositivo. Os perfis não têm senha e não substituem uma conta protegida. Capas e consultas usam HTTPS; nenhum pagamento ou coleta de analytics está ativo.",
            style = MaterialTheme.typography.bodyMedium,
            color = SpaceColors.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceBadge("HTTPS apenas", SpaceColors.secondary)
            SpaceBadge("Backup desativado", SpaceColors.primary)
            SpaceBadge("Dados locais", SpaceColors.tertiary)
        }
    }
}

private fun navIcon(title: String): String = when (title) {
    "Início" -> "house"; "Explorar" -> "search"; "Em Alta" -> "flame"; "Lançamentos", "Calendário", "Agenda" -> "calendar-days"
    "Minha Lista", "Lista" -> "bookmark"; "Categorias" -> "grid"; "Mangás" -> "book-open"; "Notícias" -> "newspaper"; "Histórico" -> "rotate-ccw"; "Configurações" -> "settings"; "VIP" -> "sparkles"; "Player" -> "play"; else -> "user-round"
}




