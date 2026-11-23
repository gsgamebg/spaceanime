package app.anim.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import app.anim.ads.AdBannerSlot
import app.anim.ads.AdPlacement
import app.anim.design.*
import app.anim.player.EpisodeQueue
import app.anim.player.PlaybackSource
import app.anim.player.PlayerBackHandler
import app.anim.library.*
import app.anim.comments.*
import kotlinx.coroutines.*

class SourceBrowserState {
    var sourceId by mutableStateOf("anitube")
    var query by mutableStateOf("")
    var dubbed by mutableStateOf(false)
    var audioFilter by mutableStateOf("Todos")
    var searchNotice by mutableStateOf<String?>(null)
    var results by mutableStateOf(emptyList<AnimeResult>())
    var searched by mutableStateOf(false)
    var searchPage by mutableIntStateOf(0)
    var searchRetryPage by mutableIntStateOf(1)
    var searchHasMore by mutableStateOf(false)
    var searchFailedSources by mutableStateOf(emptySet<String>())
    var anime by mutableStateOf<AnimeResult?>(null)
    var episodes by mutableStateOf(emptyList<Episode>())
    var episode by mutableStateOf<Episode?>(null)
    var pendingEpisode by mutableStateOf<Episode?>(null)
    var streams by mutableStateOf(emptyList<StreamOption>())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
}

@Composable fun SourcesScreen(
    browser: SourceBrowserState,
    repository: SourceRepository,
    library: LibraryStore,
    onPlay: (PlaybackSource, EpisodeQueue?) -> Unit,
    comments: CommentsStore? = null,
    author: String = "Explorador"
) {
    val saved by library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var alphabetical by remember { mutableStateOf(false) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var browseGenre by remember { mutableStateOf("acao") }
    var episodePage by remember(browser.anime) { mutableIntStateOf(0) }
    var episodeQuery by remember(browser.anime) { mutableStateOf("") }
    var unwatchedOnly by remember(browser.anime) { mutableStateOf(false) }
    var newestFirst by remember(browser.anime) { mutableStateOf(false) }

    fun cancel() { generation++; request?.cancel(); browser.loading = false }
    fun run(action: suspend () -> Unit) {
        cancel(); val id = generation; browser.loading = true; browser.error = null
        request = scope.launch {
            try { withTimeout(120_000) { action() } }
            catch (_: TimeoutCancellationException) { if (id == generation) browser.error = "O servidor demorou para responder. Tente novamente." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: SourceException) { if (id == generation) browser.error = failure.message ?: "Não foi possível consultar este servidor. Tente novamente ou escolha outro." }
            catch (_: Exception) { if (id == generation) browser.error = "Não foi possível consultar este servidor. Tente novamente ou escolha outro." }
            finally { if (id == generation) browser.loading = false }
        }
    }

    fun loadMoreResults() {
        val retrying = browser.searchFailedSources.isNotEmpty()
        val page = if (retrying) browser.searchRetryPage else browser.searchPage + 1
        run {
            val result = unifiedSearchPage(repository, browser.query.trim(), page)
            browser.searchRetryPage = page
            browser.searchFailedSources = result.sourceFailures
            browser.searchHasMore = result.hasMore
            browser.results = (browser.results + result.items).distinctBy { "${it.sourceId}|${it.id}|${it.dubbed}" }
            if (result.sourceFailures.isEmpty()) browser.searchPage = page
            browser.searchNotice = result.sourceFailures.takeIf { it.isNotEmpty() }?.let { failures ->
                "Falha em ${failures.joinToString { serverName(it) }}; os resultados disponíveis foram mantidos."
            }
            if (result.items.isEmpty() && result.sourceFailures.size == 2) {
                error("Não foi possível carregar outra página dos servidores.")
            }
        }
    }

    fun open(anime: AnimeResult) {
        cancel()
        browser.anime = anime
        browser.episodes = emptyList()
        browser.episode = null
        browser.streams = emptyList()
        browser.pendingEpisode = null
    }

    fun queueFor(anime: AnimeResult, episode: Episode): EpisodeQueue {
        val episodes = if (browser.episodes.any { it.id == episode.id }) browser.episodes
        else browser.episodes + episode
        return EpisodeQueue(anime, episodes, episode.id)
    }

    /** Clicking an episode plays it at once with the best option; the others stay listed as a fallback. */
    fun playEpisode(anime: AnimeResult, episode: Episode) {
        browser.episode = episode
        browser.streams = emptyList()
        run {
            val streams = repository.streams(anime, episode)
            browser.streams = streams
            preferredStream(streams)?.let { onPlay(it.source, queueFor(anime, episode)) }
        }
    }

    fun back() {
        cancel()
        browser.anime = null
        browser.episode = null
        browser.streams = emptyList()
        browser.error = null
        browser.pendingEpisode = null
    }

    DisposableEffect(Unit) { onDispose { request?.cancel(); browser.loading = false } }

    LaunchedEffect(browser.query, browser.anime, retry) {
        if (browser.anime != null) return@LaunchedEffect
        cancel()
        val query = browser.query.trim()
        if (query.length < 2) {
            browser.results = emptyList(); browser.searched = false; browser.error = null
            browser.searchPage = 0; browser.searchRetryPage = 1; browser.searchHasMore = false; browser.searchFailedSources = emptySet()
            return@LaunchedEffect
        }
        delay(450)
        run {
            browser.results = emptyList(); browser.searched = false; browser.searchNotice = null
            browser.searchPage = 0; browser.searchRetryPage = 1; browser.searchHasMore = false; browser.searchFailedSources = emptySet()
            val result = unifiedSearchPage(repository, query, 1)
            browser.searchRetryPage = 1
            browser.searchFailedSources = result.sourceFailures
            browser.searchHasMore = result.hasMore
            browser.results = result.items
            if (result.sourceFailures.isEmpty()) browser.searchPage = 1
            if (result.failedSources == 2) error("Servidores indisponíveis")
            browser.searched = true
            browser.searchNotice = buildList {
                if (result.terms.size > 1) add("Também pesquisamos: " + result.terms.drop(1).joinToString(", "))
                if (result.sourceFailures.isNotEmpty()) add("Falha em ${result.sourceFailures.joinToString { serverName(it) }}; exibindo os resultados disponíveis.")
            }.joinToString(" · ").ifBlank { null }
        }
    }

    LaunchedEffect(browser.anime) {
        val anime = browser.anime ?: return@LaunchedEffect
        if (browser.episodes.isEmpty()) run {
            browser.episodes = repository.episodes(anime)
            browser.pendingEpisode?.let { recent ->
                val episode = resolveRecentEpisode(browser.episodes, recent)
                if (browser.episodes.none { it.id == episode.id }) browser.episodes = (browser.episodes + episode).sortedBy { it.number }
                browser.episode = episode
                browser.pendingEpisode = null
                val streams = repository.streams(anime, episode)
                browser.streams = streams
                preferredStream(streams)?.let { onPlay(it.source, queueFor(anime, episode)) }
            }
        }
    }

    if (browser.anime != null) PlayerBackHandler(::back)

    // Layout principal unificado para garantir que nada se sobreponha
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (browser.anime == null) {
            // =========================================================================
            // MODO EXPLORAR / CATÁLOGO / BUSCA
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4)))),
                        contentAlignment = Alignment.Center
                    ) {
                        SpaceIcon("search", null, Modifier.size(20.dp), tint = Color.White)
                    }
                    Column {
                        Text(
                            "Explorar Universo Space",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Text(
                            "Encontre animes legendados e dublados sem anúncios intrusivos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
            AdBannerSlot(AdPlacement.Catalog)

            // Campo de Busca
            OutlinedTextField(
                value = browser.query,
                onValueChange = { browser.query = it.take(120); cancel() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Qual anime você procura? (ex: Solo Leveling, One Piece)", color = Color(0xFF64748B)) },
                label = { Text("Buscar animes") },
                leadingIcon = { SpaceIcon("search", null, tint = Color(0xFF06B6D4)) },
                trailingIcon = {
                    if (browser.query.isNotEmpty()) {
                        IconButton(onClick = { browser.query = "" }) {
                            SpaceIcon("x", "Limpar busca", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { retry++ }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0x338B5CF6),
                    focusedTextColor = Color(0xFFF8FAFC),
                    unfocusedTextColor = Color(0xFFF8FAFC),
                )
            )

            // Filtros de Áudio e Ordenação
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Todos", "Legendado", "Dublado").forEach { filter ->
                    GenreChip(filter, browser.audioFilter == filter) { browser.audioFilter = filter }
                }
                GenreChip(if (alphabetical) "Nome A–Z ×" else "Ordem do servidor", alphabetical) { alphabetical = !alphabetical }
                GenreChip(if (onlyFavorites) "Minha lista ×" else "Só minha lista", onlyFavorites) { onlyFavorites = !onlyFavorites }
            }

            // Indicador de Carregamento
            if (browser.loading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF8B5CF6), trackColor = Color(0xFF1E1B4B))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Buscando nos servidores Space...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                        TextButton(onClick = ::cancel) { Text("Cancelar consulta", color = Color(0xFF8B5CF6)) }
                    }
                }
            }

            // Mensagem de Erro
            browser.error?.let { message ->
                SpacePanel(Modifier.fillMaxWidth(), tonal = Color(0xFF1E1B4B)) {
                    Text(message, color = SpaceColors.error)
                    TextButton(onClick = {
                        val anime = browser.anime
                        if (anime == null) retry++ else run {
                            browser.episodes = repository.episodes(anime)
                            browser.episode?.let { browser.streams = repository.streams(anime, it) }
                        }
                    }) {
                        Text("Tentar novamente", color = Color(0xFF06B6D4))
                    }
                }
            }

            browser.searchNotice?.let {
                Text(it, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            }

            // Seção de Conteúdo da Busca
            if (browser.query.isBlank()) {
                // Conteúdo Inicial: Em Alta e Categorias
                TrendingExplorer(repository, ::open)
                Spacer(Modifier.height(16.dp))
                GenreExplorer(repository, browseGenre, { browseGenre = it }, ::open)
            } else if (browser.query.trim().length < 2) {
                SpaceEmptyState("Encontre uma nova história", "Digite pelo menos dois caracteres. A busca é feita automaticamente enquanto você escreve.")
            } else {
                // Resultados da Busca
                val results = remember(browser.results, browser.audioFilter, onlyFavorites, alphabetical, saved.favorites) {
                    browser.results.filter { (browser.audioFilter == "Todos" || it.dubbed == (browser.audioFilter == "Dublado")) && (!onlyFavorites || saved.isFavorite(it)) }.let { if (alphabetical) it.sortedBy { a -> a.title.lowercase() } else it }
                }
                if (browser.searched && results.isEmpty() && !browser.loading && browser.searchFailedSources.isEmpty()) {
                    SpaceEmptyState("Nenhum resultado", "Tente outro nome, desative o filtro Minha lista ou selecione outro filtro de áudio.")
                }
                if (results.isNotEmpty()) {
                    SectionHeader("${results.size} animes encontrados")
                    PosterGrid(results, ::open, { saved.isFavorite(it) }, library::toggleFavorite)
                }
                if (browser.searched && (browser.searchHasMore || browser.searchFailedSources.isNotEmpty())) {
                    Surface(
                        onClick = ::loadMoreResults,
                        enabled = !browser.loading,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0x338B5CF6)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (browser.searchFailedSources.isNotEmpty()) "Tentar novamente / carregar próxima página" else "Carregar próxima página",
                                color = Color(0xFF06B6D4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // MODO DETALHES DO ANIME (SINOPSE, EPISÓDIOS E COMENTÁRIOS)
            // =========================================================================
            val anime = browser.anime!!
            var synopsis by remember(anime) { mutableStateOf<String?>(null) }
            LaunchedEffect(anime) {
                synopsis = try { repository.synopsis(anime) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            }

            // Barra Superior de Navegação
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = ::back,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0x338B5CF6))
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpaceIcon("chevron-left", null, Modifier.size(18.dp), tint = Color(0xFF06B6D4))
                        Text("Voltar ao catálogo", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
                SpaceBadge(serverName(anime.sourceId), Color(0xFF1E1B4B))
            }

            // Card Principal do Anime
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0x338B5CF6)),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, Color(0x448B5CF6), RoundedCornerShape(16.dp))
                        ) {
                            AnimePosterCard(anime.title, anime.image, Modifier.fillMaxWidth(), onClick = {})
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                anime.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SpaceBadge(
                                    if (anime.dubbed) "Dublado em Português" else "Legendado em Português",
                                    if (anime.dubbed) Color(0xFF8B5CF6) else Color(0xFF06B6D4)
                                )
                                SpaceBadge(serverName(anime.sourceId), Color(0xFF1E1B4B))
                            }
                            Button(
                                onClick = { library.toggleFavorite(anime) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (saved.isFavorite(anime)) Color(0xFF1E1B4B) else Color(0xFF8B5CF6)
                                ),
                                border = BorderStroke(1.dp, if (saved.isFavorite(anime)) Color(0xFF8B5CF6) else Color.Transparent)
                            ) {
                                SpaceIcon(
                                    "heart", null, Modifier.size(18.dp),
                                    tint = if (saved.isFavorite(anime)) Color(0xFFF43F5E) else Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (saved.isFavorite(anime)) "Remover da minha lista" else "Adicionar à minha lista",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Bloco de Sinopse
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Sinopse",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                        Text(
                            synopsis ?: "Carregando sinopse do anime...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Indicador de Carregamento de Episódios
            if (browser.loading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF8B5CF6), trackColor = Color(0xFF1E1B4B))
                    Text("Carregando lista de episódios...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                }
            }

            // Erro de Carregamento
            browser.error?.let { message ->
                SpacePanel(Modifier.fillMaxWidth(), tonal = Color(0xFF1E1B4B)) {
                    Text(message, color = SpaceColors.error)
                    TextButton(onClick = {
                        run {
                            browser.episodes = repository.episodes(anime)
                            browser.episode?.let { browser.streams = repository.streams(anime, it) }
                        }
                    }) { Text("Tentar novamente", color = Color(0xFF06B6D4)) }
                }
            }

            // Painel de Reprodução do Episódio Selecionado
            browser.episode?.let { episode ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1B4B),
                    border = BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6)),
                                contentAlignment = Alignment.Center
                            ) {
                                SpaceIcon("play", null, Modifier.size(18.dp), tint = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(episode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val savedPosition = saved.progressFor(EpisodeContext(anime, episode))?.resumePosition() ?: 0L
                                Text(
                                    when {
                                        browser.loading -> "Abrindo o player…"
                                        browser.streams.size > 1 -> "Se o vídeo falhar, troque de servidor:"
                                        else -> "Toque para assistir novamente."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        val savedPosition = saved.progressFor(EpisodeContext(anime, episode))?.resumePosition() ?: 0L
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            browser.streams.forEachIndexed { index, option ->
                                val quality = Regex("[0-9]{3,4}p").find(option.label)?.value ?: "Automático"
                                FilledTonalButton(
                                    onClick = { onPlay(option.source, queueFor(anime, episode)) },
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text("Servidor ${index + 1} · $quality") }
                            }
                            if (savedPosition > 0) preferredStream(browser.streams)?.let { best ->
                                OutlinedButton(
                                    onClick = { onPlay(best.source.copy(startFromBeginning = true), queueFor(anime, episode)) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0x668B5CF6))
                                ) { Text("Começar do início", color = Color(0xFFF8FAFC)) }
                            }
                        }
                    }
                }
            }

            // Seção de Episódios
            SectionHeader("Episódios", "${browser.episodes.size} disponíveis")
            val gaps = remember(browser.episodes) { missingEpisodeNumbers(browser.episodes) }
            if (gaps.isNotEmpty() && !browser.loading) {
                SpacePanel(Modifier.fillMaxWidth(), tonal = Color(0xFF0F172A)) {
                    Text("A lista deste servidor pula ${gaps.size} número(s): ${gaps.take(8).joinToString(", ")}${if (gaps.size > 8) "…" else ""}.", color = Color(0xFF94A3B8))
                    TextButton(onClick = { val title = anime.title; back(); browser.query = title }) {
                        Text("Procurar este anime no outro servidor", color = Color(0xFF06B6D4))
                    }
                }
            }

            // Filtros de Episódios
            OutlinedTextField(
                value = episodeQuery,
                onValueChange = { episodeQuery = it.take(120); episodePage = 0 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Filtrar episódio por número ou nome") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0x338B5CF6),
                    focusedTextColor = Color(0xFFF8FAFC),
                    unfocusedTextColor = Color(0xFFF8FAFC),
                )
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenreChip("Não assistidos", unwatchedOnly) { unwatchedOnly = !unwatchedOnly; episodePage = 0 }
                GenreChip(if (newestFirst) "Mais recentes" else "Mais antigos", newestFirst) { newestFirst = !newestFirst; episodePage = 0 }
            }

            // Lista de Episódios
            val progressByEpisode = remember(saved.progress, anime) { saved.progress.filter { it.context.anime.sameAnime(anime) }.associateBy { it.context.episode.id } }
            val filtered = remember(browser.episodes, episodeQuery, unwatchedOnly, newestFirst, progressByEpisode) {
                browser.episodes.filter { it.title.contains(episodeQuery.trim(), true) && (!unwatchedOnly || progressByEpisode[it.id]?.completed != true) }.let { if (newestFirst) it.reversed() else it }
            }
            val page = episodePage.coerceAtMost(((filtered.size - 1) / 30).coerceAtLeast(0))
            if (filtered.isEmpty() && !browser.loading) {
                Text("Nenhum episódio disponível para este filtro.", color = Color(0xFF94A3B8))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.drop(page * 30).take(30).forEach { episode ->
                    val progress = progressByEpisode[episode.id]
                    val isSelected = browser.episode?.id == episode.id
                    Surface(
                        onClick = { playEpisode(anime, episode) },
                        enabled = !browser.loading,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF8B5CF6) else Color(0x228B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1E1B4B)),
                                contentAlignment = Alignment.Center
                            ) {
                                SpaceIcon("play", null, Modifier.size(18.dp), tint = if (isSelected) Color.White else Color(0xFF06B6D4))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(episode.title, color = Color(0xFFF8FAFC), fontWeight = FontWeight.Medium)
                                Text(
                                    if (progress?.completed == true) "Assistido" else if ((progress?.positionMs ?: 0) > 0) "Em andamento" else "Pronto para assistir",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (progress?.completed == true) Color(0xFF10B981) else Color(0xFF94A3B8)
                                )
                            }
                            Checkbox(
                                checked = progress?.completed == true,
                                onCheckedChange = { library.markWatched(EpisodeContext(anime, episode), it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF8B5CF6),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                }
            }

            // Paginação de Episódios
            if (filtered.size > 30) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(enabled = page > 0, onClick = { episodePage = 0 }) { Text("Primeira", color = Color(0xFF06B6D4)) }
                    TextButton(enabled = page > 0, onClick = { episodePage = page - 1 }) { Text("Anterior", color = Color(0xFF06B6D4)) }
                    Text("${page + 1} / ${(filtered.size + 29) / 30}", Modifier.padding(12.dp), color = Color(0xFF94A3B8))
                    TextButton(enabled = (page + 1) * 30 < filtered.size, onClick = { episodePage = page + 1 }) { Text("Próxima", color = Color(0xFF06B6D4)) }
                    TextButton(enabled = (page + 1) * 30 < filtered.size, onClick = { episodePage = (filtered.size - 1) / 30 }) { Text("Última", color = Color(0xFF06B6D4)) }
                }
            }

            // Seção de Comentários (POSICIONADA NO FINAL COM SEPARAÇÃO EXPLÍCITA)
            if (comments != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier.fillMaxWidth(), color = Color(0x338B5CF6))
                Spacer(Modifier.height(8.dp))
                CommentsSection(anime, author, comments)
            }
        }
    }
}
