package app.anim.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anim.sources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

val SpaceGenres = listOf("Ação" to "acao", "Aventura" to "aventura", "Comédia" to "comedia", "Fantasia" to "fantasia", "Romance" to "romance", "Drama" to "drama")

// Largura padrão de card de poster — igual em Em Alta e Categorias
private val PosterCardWidth = 160.dp

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
        SectionHeader("Explorar por Categoria", "Gêneros disponíveis no catálogo do Servidor Space 2")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceGenres.forEach { (label, value) -> GenreChip(label, slug == value) { onSelect(value) } }
        }
        if (loading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF8B5CF6), trackColor = Color(0xFF1E1B4B))
                Text("Buscando animes da categoria...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
        else if (error) {
            SpacePanel(Modifier.fillMaxWidth(), tonal = Color(0xFF1E1B4B)) {
                Text("Não foi possível carregar esta categoria.", color = SpaceColors.error)
                TextButton(onClick = { retry++ }) { Text("Tentar novamente", color = Color(0xFF06B6D4)) }
            }
        }
        else if (data.isEmpty() && !loading) {
            Text("Nenhum anime encontrado nesta página.", color = Color(0xFF94A3B8))
        }

        if (data.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(data, key = { "${it.sourceId}:${it.id}:${it.dubbed}" }) { anime ->
                    AnimePosterCard(
                        title = anime.title,
                        imageUrl = anime.image,
                        modifier = Modifier.width(PosterCardWidth),
                        onClick = { onOpen(anime) },
                        badge = {
                            SpaceBadge(
                                if (anime.dubbed) "DUB" else "LEG",
                                if (anime.dubbed) Color(0xFF8B5CF6) else Color(0xFF06B6D4),
                                Modifier.align(Alignment.TopStart).padding(8.dp)
                            )
                        }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(enabled = page > 1 && !loading, onClick = { requestedPage = page - 1 }) {
                Text("← Anterior", color = if (page > 1 && !loading) Color(0xFF06B6D4) else Color(0xFF64748B))
            }
            Text(
                if (page == 0) "Página 1" else "Página $page",
                Modifier.padding(horizontal = 8.dp),
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(enabled = hasMore && !loading && page < 40, onClick = { requestedPage = page + 1 }) {
                Text("Próxima →", color = if (hasMore && !loading && page < 40) Color(0xFF06B6D4) else Color(0xFF64748B))
            }
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
        SectionHeader("Em Alta Agora 🔥", "Tendências da comunidade encontradas nos servidores Space")
        if (loading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF8B5CF6), trackColor = Color(0xFF1E1B4B))
                Text("Conectando aos servidores Space...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
        else if (error) {
            SpacePanel(Modifier.fillMaxWidth(), tonal = Color(0xFF1E1B4B)) {
                Text("Não foi possível carregar os títulos em alta.", color = SpaceColors.error)
                TextButton(onClick = { retry++ }) { Text("Tentar novamente", color = Color(0xFF06B6D4)) }
            }
        }
        else if (titles.isEmpty() && !loading) {
            Text("Nenhuma tendência foi encontrada nos servidores neste momento.", color = Color(0xFF94A3B8))
        }

        if (titles.isNotEmpty()) {
            // LazyRow com largura fixa igual à aba de categorias — evita cards gigantes no desktop
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(titles, key = { "${it.sourceId}:${it.id}:${it.dubbed}" }) { anime ->
                    AnimePosterCard(
                        title = anime.title,
                        imageUrl = anime.image,
                        modifier = Modifier.width(PosterCardWidth),
                        onClick = { onOpen(anime) }
                    )
                }
            }
        }
    }
}
