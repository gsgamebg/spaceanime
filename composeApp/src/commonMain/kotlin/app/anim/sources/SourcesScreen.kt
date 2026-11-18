package app.anim.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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

@Composable fun SourcesScreen(browser: SourceBrowserState, repository: SourceRepository, library: LibraryStore, onPlay: (PlaybackSource, EpisodeQueue?) -> Unit, comments: CommentsStore? = null, author: String = "Explorador") {
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
    fun open(anime: AnimeResult) { cancel(); browser.anime = anime; browser.episodes = emptyList(); browser.episode = null; browser.streams = emptyList(); browser.pendingEpisode = null }
    fun queueFor(anime: AnimeResult, episode: Episode): EpisodeQueue {
        val episodes = if (browser.episodes.any { it.id == episode.id }) browser.episodes
        else browser.episodes + episode
        return EpisodeQueue(anime, episodes, episode.id)
    }
    fun back() { cancel(); browser.anime = null; browser.episode = null; browser.streams = emptyList(); browser.error = null; browser.pendingEpisode = null }
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
                browser.episode = episode; browser.streams = repository.streams(anime, episode)
                browser.pendingEpisode = null
            }
        }
    }
    if (browser.anime != null) PlayerBackHandler(::back)

    if (browser.anime == null) {
        Text("Explore seu próximo universo", style = MaterialTheme.typography.headlineLarge)
        Text("Encontre animes legendados e dublados.", color = SpaceColors.onSurfaceVariant)
        AdBannerSlot(AdPlacement.Catalog)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(browser.query, { browser.query = it.take(120); cancel() }, Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Qual anime você procura?") }, label = { Text("Buscar animes") },
            leadingIcon = { SpaceIcon("search", null, tint = SpaceColors.secondary) },
            trailingIcon = { if (browser.query.isNotEmpty()) IconButton(onClick = { browser.query = "" }) { SpaceIcon("x", "Limpar busca", tint = SpaceColors.onSurface) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { retry++ }), shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Todos", "Legendado", "Dublado").forEach { filter -> GenreChip(filter, browser.audioFilter == filter) { browser.audioFilter = filter } }
            GenreChip(if (alphabetical) "Nome A–Z ×" else "Ordem do servidor", alphabetical) { alphabetical = !alphabetical }
            GenreChip(if (onlyFavorites) "Minha lista ×" else "Só minha lista", onlyFavorites) { onlyFavorites = !onlyFavorites }
        }
        browser.searchNotice?.let { Text(it, color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        if (browser.query.isBlank()) {
            Spacer(Modifier.height(20.dp))
            TrendingExplorer(repository, ::open)
            Spacer(Modifier.height(20.dp))
            GenreExplorer(repository, browseGenre, { browseGenre = it }, ::open)
        } else if (browser.query.trim().length < 2) {
            SpaceEmptyState("Encontre uma nova história", "Digite pelo menos dois caracteres. A busca é feita automaticamente enquanto você escreve.")
        }
    } else {
        val anime = browser.anime!!
        var synopsis by remember(anime) { mutableStateOf<String?>(null) }
        LaunchedEffect(anime) {
            synopsis = try { repository.synopsis(anime) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }
        TextButton(onClick = ::back) { SpaceIcon("chevron-left", null); Text("Voltar à busca") }
        SpacePanel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                AnimePosterCard(anime.title, anime.image, Modifier.width(100.dp), onClick = {})
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(anime.title, style = MaterialTheme.typography.titleLarge)
                    Text(if (anime.dubbed) "Dublado em português" else "Legendado em português", color = SpaceColors.secondary)
                    Text(serverName(anime.sourceId), style = MaterialTheme.typography.bodySmall)
                }
            }
            synopsis?.let { Text("Sinopse", style = MaterialTheme.typography.titleMedium); Text(it, color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { library.toggleFavorite(anime) }) { SpaceIcon("heart", null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (saved.isFavorite(anime)) "Remover da minha lista" else "Adicionar à minha lista") }
        }
    }
    if (browser.loading) {
        Spacer(Modifier.height(16.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = ::cancel) { Text("Cancelar consulta") }
        if (browser.anime == null && browser.results.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { SpaceSkeletonBlock(Modifier.weight(1f).height(220.dp)) } }
    }
    browser.error?.let { message ->
        SpacePanel(Modifier.fillMaxWidth()) {
            Text(message, color = SpaceColors.error)
            TextButton(onClick = { val anime = browser.anime; if (anime == null) retry++ else run { browser.episodes = repository.episodes(anime); browser.episode?.let { browser.streams = repository.streams(anime, it) } } }) { Text("Tentar novamente") }
        }
    }
    if (browser.anime == null) {
        val results = remember(browser.results, browser.audioFilter, onlyFavorites, alphabetical, saved.favorites) {
            browser.results.filter { (browser.audioFilter == "Todos" || it.dubbed == (browser.audioFilter == "Dublado")) && (!onlyFavorites || saved.isFavorite(it)) }.let { if (alphabetical) it.sortedBy { a -> a.title.lowercase() } else it }
        }
        if (browser.searched && results.isEmpty() && !browser.loading && browser.searchFailedSources.isEmpty()) SpaceEmptyState("Nenhum resultado", "Tente outro nome, desative o filtro Minha lista ou selecione outro filtro de áudio.")
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(20.dp)); SectionHeader("${results.size} animes encontrados"); Spacer(Modifier.height(14.dp))
            PosterGrid(results, ::open, { saved.isFavorite(it) }, library::toggleFavorite)
        }
        if (browser.searched && (browser.searchHasMore || browser.searchFailedSources.isNotEmpty())) {
            TextButton(enabled = !browser.loading, onClick = ::loadMoreResults) {
                Text(if (browser.searchFailedSources.isNotEmpty()) "Tentar novamente / carregar próxima página" else "Carregar próxima página")
            }
        }
    } else {
        val anime = browser.anime!!
        Spacer(Modifier.height(20.dp))
        browser.episode?.let { episode ->
            SpacePanel(Modifier.fillMaxWidth(), tonal = SpaceColors.primaryContainer) {
                Text(episode.title, style = MaterialTheme.typography.titleLarge)
                val savedPosition = saved.progressFor(EpisodeContext(anime, episode))?.resumePosition() ?: 0L
                Text(if (savedPosition > 0) "Você já assistiu parte deste episódio. Escolha continuar ou começar do início." else "Selecione uma opção de reprodução.")
                browser.streams.forEachIndexed { index, option ->
                    val quality = Regex("[0-9]{3,4}p").find(option.label)?.value ?: "Automático"
                    Button(onClick = { onPlay(option.source, queueFor(anime, episode)) }, modifier = Modifier.fillMaxWidth()) { SpaceIcon("play", null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("${if (savedPosition > 0) "Continuar" else "Assistir"} · Opção ${index + 1} · $quality") }
                    if (savedPosition > 0) OutlinedButton(onClick = { onPlay(option.source.copy(startFromBeginning = true), queueFor(anime, episode)) }, modifier = Modifier.fillMaxWidth()) { Text("Começar do início · Opção ${index + 1}") }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); SectionHeader("Episódios", "${browser.episodes.size} disponíveis")
        val gaps = remember(browser.episodes) { missingEpisodeNumbers(browser.episodes) }
        if (gaps.isNotEmpty() && !browser.loading) {
            SpacePanel(Modifier.fillMaxWidth()) {
                Text("A lista deste servidor pula ${gaps.size} número(s): ${gaps.take(8).joinToString(", ")}${if (gaps.size > 8) "…" else ""}.", color = SpaceColors.onSurfaceVariant)
                TextButton(onClick = { val title = anime.title; back(); browser.query = title }) { Text("Procurar este anime no outro servidor") }
            }
        }
        OutlinedTextField(episodeQuery, { episodeQuery = it.take(120); episodePage = 0 }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar episódio por número") })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenreChip("Não assistidos", unwatchedOnly) { unwatchedOnly = !unwatchedOnly; episodePage = 0 }
            GenreChip(if (newestFirst) "Mais recentes" else "Mais antigos", newestFirst) { newestFirst = !newestFirst; episodePage = 0 }
        }
        val progressByEpisode = remember(saved.progress, anime) { saved.progress.filter { it.context.anime.sameAnime(anime) }.associateBy { it.context.episode.id } }
        val filtered = remember(browser.episodes, episodeQuery, unwatchedOnly, newestFirst, progressByEpisode) {
            browser.episodes.filter { it.title.contains(episodeQuery.trim(), true) && (!unwatchedOnly || progressByEpisode[it.id]?.completed != true) }.let { if (newestFirst) it.reversed() else it }
        }
        val page = episodePage.coerceAtMost(((filtered.size - 1) / 30).coerceAtLeast(0))
        if (filtered.isEmpty() && !browser.loading) Text("Nenhum episódio disponível para este filtro.", color = SpaceColors.onSurfaceVariant)
        filtered.drop(page * 30).take(30).forEach { episode ->
            val progress = progressByEpisode[episode.id]
            Surface(onClick = { browser.episode = episode; browser.streams = emptyList(); run { browser.streams = repository.streams(anime, episode) } }, enabled = !browser.loading,
                shape = RoundedCornerShape(16.dp), color = if (browser.episode?.id == episode.id) SpaceColors.primaryContainer else SpaceCardColor,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpaceIcon("play", null, Modifier.size(20.dp), SpaceColors.secondary)
                    Column(Modifier.weight(1f)) { Text(episode.title); Text(if (progress?.completed == true) "Assistido" else if ((progress?.positionMs ?: 0) > 0) "Em andamento" else "Pronto para assistir", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant) }
                    Checkbox(checked = progress?.completed == true, onCheckedChange = { library.markWatched(EpisodeContext(anime, episode), it) }, modifier = Modifier)
                }
            }
        }
        if (filtered.size > 30) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = page > 0, onClick = { episodePage = 0 }) { Text("Primeira") }
            TextButton(enabled = page > 0, onClick = { episodePage = page - 1 }) { Text("Anterior") }
            Text("${page + 1} / ${(filtered.size + 29) / 30}", Modifier.padding(12.dp))
            TextButton(enabled = (page + 1) * 30 < filtered.size, onClick = { episodePage = page + 1 }) { Text("Próxima") }
            TextButton(enabled = (page + 1) * 30 < filtered.size, onClick = { episodePage = (filtered.size - 1) / 30 }) { Text("Última") }
        }
        if (comments != null) { Spacer(Modifier.height(28.dp)); CommentsSection(anime, author, comments) }
    }
}



