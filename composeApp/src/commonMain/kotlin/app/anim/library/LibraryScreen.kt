package app.anim.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.anim.design.*
import app.anim.player.EpisodeQueue
import app.anim.player.PlaybackSource
import app.anim.player.playbackTime
import app.anim.sources.AnimeResult
import app.anim.sources.SourceException
import app.anim.sources.SourceRepository
import app.anim.sources.serverName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class LibraryFilter(val label: String) {
    All("Tudo"),
    Continuing("Continuar"),
    Favorites("Favoritos"),
}

@Composable
fun LibraryScreen(
    library: LibraryStore,
    mangaFollows: MangaFollowStore,
    repository: SourceRepository,
    onOpen: (AnimeResult) -> Unit,
    onOpenManga: (String) -> Unit,
    onPlay: (PlaybackSource, EpisodeQueue?, String?) -> Unit,
) {
    val snapshot by library.state.collectAsState()
    val mangaSnapshot by mangaFollows.state.collectAsState()
    val sections = remember(snapshot, mangaSnapshot.followed) {
        LibrarySections.project(snapshot, mangaSnapshot.followed)
    }
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(LibraryFilter.All) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Minha Lista", style = MaterialTheme.typography.headlineLarge, color = SpaceColors.onSurface)
    Text("Animes favoritos, mangás seguidos e seu progresso neste dispositivo.", color = SpaceColors.onSurfaceVariant)
    snapshot.error?.let { Text(it, color = SpaceColors.error) }
    mangaSnapshot.error?.let { Text(it, color = SpaceColors.error) }
    error?.let { Text(it, color = SpaceColors.error) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpaceColors.primary)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryFilter.entries.forEach { filter ->
            GenreChip(filter.label, selectedFilter == filter) { selectedFilter = filter }
        }
    }

    val visibleEntries = when (selectedFilter) {
        LibraryFilter.All -> sections.all
        LibraryFilter.Favorites -> sections.favorites
        LibraryFilter.Continuing -> emptyList()
    }
    val continuingItems = when (selectedFilter) {
        LibraryFilter.Continuing -> sections.continuing
        else -> emptyList()
    }

    fun resume(progress: WatchProgress) {
        loading = true
        error = null
        scope.launch {
            try {
                val anime = progress.context.anime
                var queueWarning: String? = null
                val episodes = try {
                    withTimeout(120_000) { repository.episodes(anime) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    queueWarning = "A lista de episódios não carregou; navegação anterior/próximo indisponível."
                    emptyList()
                }
                val queueEpisodes = if (episodes.any { it.id == progress.context.episode.id }) episodes
                else episodes + progress.context.episode
                val streams = withTimeout(120_000) {
                    repository.streams(anime, progress.context.episode)
                }
                val stream = streams.firstOrNull()?.source
                    ?: throw IllegalStateException("Nenhuma opção de reprodução foi encontrada.")
                onPlay(
                    stream,
                    EpisodeQueue(anime, queueEpisodes, progress.context.episode.id),
                    queueWarning,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = if (failure is SourceException) failure.message
                else failure.message ?: "Não foi possível reabrir o episódio."
            } finally {
                loading = false
            }
        }
    }

    if (visibleEntries.isEmpty() && continuingItems.isEmpty() && snapshot.error == null && mangaSnapshot.error == null) {
        when (selectedFilter) {
            LibraryFilter.All -> EmptyStateCard("▤", "Sua lista está vazia", "Adicione animes aos favoritos ou siga mangás para encontrá-los aqui.")
            LibraryFilter.Continuing -> EmptyStateCard("▷", "Nada para continuar", "Episódios não concluídos com posição salva aparecerão aqui.")
            LibraryFilter.Favorites -> EmptyStateCard("♡", "Nenhum favorito ainda", "Adicione animes aos favoritos ou siga um mangá.")
        }
    }

    visibleEntries.forEach { entry ->
        when (entry) {
            is LibraryEntry.Anime -> AnimeLibraryCard(
                entry = entry,
                onOpen = { onOpen(entry.anime) },
                onToggleFavorite = { library.toggleFavorite(entry.anime) },
                onContinue = entry.progress?.let { progress -> { resume(progress) } },
                enabled = !loading,
            )
            is LibraryEntry.Manga -> MangaLibraryCard(
                entry = entry,
                onOpen = { onOpenManga(entry.manga.title) },
                onUnfollow = { mangaFollows.toggleFollow(entry.manga) },
            )
        }
    }

    continuingItems.forEach { progress ->
        val favorite = snapshot.isFavorite(progress.context.anime)
        AnimeLibraryCard(
            entry = LibraryEntry.Anime(progress.context.anime, progress, favorite),
            onOpen = { onOpen(progress.context.anime) },
            onToggleFavorite = { library.toggleFavorite(progress.context.anime) },
            onContinue = { resume(progress) },
            enabled = !loading,
        )
    }
}

@Composable
private fun AnimeLibraryCard(
    entry: LibraryEntry.Anime,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onContinue: (() -> Unit)?,
    enabled: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SpaceColors.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(entry.anime.image, entry.anime.title, Modifier.size(width = 72.dp, height = 100.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.anime.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "${serverName(entry.anime.sourceId)} · ${if (entry.anime.dubbed) "Dublado" else "Legendado"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpaceColors.onSurfaceVariant,
                )
                entry.progress?.let { progress ->
                    Text(progress.context.episode.title, color = SpaceColors.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = {
                            (progress.positionMs.toFloat() / progress.durationMs.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = SpaceColors.primary,
                    )
                    Text(
                        "${playbackTime(progress.positionMs)} / ${playbackTime(progress.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpaceColors.secondary,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onContinue != null) {
                        Button(onClick = onContinue, enabled = enabled) { Text("Continuar") }
                    }
                    OutlinedButton(onClick = onOpen, enabled = enabled) { Text("Episódios") }
                    TextButton(onClick = onToggleFavorite, enabled = enabled) {
                        Text(if (entry.favorite) "Remover favorito" else "Favoritar")
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaLibraryCard(entry: LibraryEntry.Manga, onOpen: () -> Unit, onUnfollow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SpaceColors.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(entry.manga.coverUrl, entry.manga.title, Modifier.size(width = 72.dp, height = 100.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.manga.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Mangá · ${entry.manga.sourceId}", color = SpaceColors.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpen) { Text("Abrir mangá") }
                    TextButton(onClick = onUnfollow) { Text("Deixar de seguir") }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: String, title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = SpaceColors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
