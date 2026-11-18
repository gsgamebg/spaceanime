package app.anim.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anim.sources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

val SpaceGenres = listOf("Ação" to "acao", "Aventura" to "aventura", "Comédia" to "comedia", "Fantasia" to "fantasia", "Romance" to "romance", "Drama" to "drama")

@Composable fun GenreExplorer(repository: SourceRepository, slug: String, onSelect: (String) -> Unit, onOpen: (AnimeResult) -> Unit) {
    var page by remember(slug) { mutableIntStateOf(0) }
    var requestedPage by remember(slug) { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var hasMore by remember(slug) { mutableStateOf(false) }
    var data by remember(slug) { mutableStateOf(emptyList<AnimeResult>()) }
    LaunchedEffect(repository, slug, requestedPage, retry) {
        loading = true; error = false
        try {
            val result = withTimeout(30_000) { repository.genrePageResults(slug, requestedPage) }
            data = result.items
            hasMore = result.hasMore
            page = requestedPage
        }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Explore por categoria", style = MaterialTheme.typography.headlineMedium)
        Text("Gêneros disponíveis no catálogo do Servidor Space 2.", color = SpaceColors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceGenres.forEach { (label, value) -> GenreChip(label, slug == value) { onSelect(value) } }
        }
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Buscando animes…") }
        else if (error) { Text("Não foi possível carregar a categoria.", color = SpaceColors.error); TextButton(onClick = { retry++ }) { Text("Tentar novamente") } }
        else if (data.isEmpty()) Text("Nenhum anime nesta página.", color = SpaceColors.onSurfaceVariant)
        if (data.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(data, key = { "${it.sourceId}:${it.id}:${it.dubbed}" }) { anime ->
                    AnimePosterCard(anime.title, anime.image, Modifier.width(155.dp), onClick = { onOpen(anime) }, badge = { SpaceBadge(if (anime.dubbed) "DUB" else "LEG", SpaceColors.secondary, Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(8.dp)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = page > 1 && !loading, onClick = { requestedPage = page - 1 }) { Text("Anterior") }
            Text(if (page == 0) "Página 1" else "Página $page", Modifier.padding(12.dp), color = SpaceColors.onSurfaceVariant)
            TextButton(enabled = hasMore && !loading && page < 40, onClick = { requestedPage = page + 1 }) { Text("Próxima") }
        }
    }
}

@Composable fun TrendingExplorer(repository: SourceRepository, onOpen: (AnimeResult) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var titles by remember { mutableStateOf(emptyList<AnimeResult>()) }
    LaunchedEffect(repository, retry) {
        loading = true; error = false
        try { titles = withTimeout(35_000) { repository.trending() } }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Em alta agora", style = MaterialTheme.typography.headlineMedium)
        Text("Tendências da AniList encontradas nos servidores Space. Alguns títulos podem não estar disponíveis.", color = SpaceColors.onSurfaceVariant)
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Conectando os universos…") }
        else if (error) { Text("Não foi possível carregar as tendências.", color = SpaceColors.error); TextButton(onClick = { retry++ }) { Text("Tentar novamente") } }
        else if (titles.isEmpty()) Text("Nenhuma tendência foi encontrada nos servidores agora.", color = SpaceColors.onSurfaceVariant)
        if (titles.isNotEmpty()) {
            if (titles.size < 10) Text("${titles.size} títulos encontrados nos servidores neste momento.", color = SpaceColors.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = posterColumnCount(maxWidth.value)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    titles.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { anime -> AnimePosterCard(anime.title, anime.image, Modifier.weight(1f), onClick = { onOpen(anime) }) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
