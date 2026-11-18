package app.anim.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.anim.sources.AnimeResult
import coil3.compose.AsyncImage

expect fun safeArtworkUrl(raw: String): String?
expect fun configureArtwork()

@Composable fun Artwork(model: String, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallbackModel: String? = null) {
    var selected by remember(model, fallbackModel) { mutableStateOf(model) }
    val url = remember(selected) { safeArtworkUrl(selected) }
    Box(modifier.background(SpaceCardColor), contentAlignment = Alignment.Center) {
        SpaceIcon("play", null, Modifier.size(32.dp), SpaceColors.secondary.copy(alpha = .45f))
        if (url != null) AsyncImage(model = url, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.matchParentSize(),
            onError = { if (selected != fallbackModel && fallbackModel != null) selected = fallbackModel })
    }
}

fun posterColumnCount(width: Float): Int = when {
    width < 300 -> 1
    width < 600 -> 2
    width < 900 -> 3
    width < 1200 -> 4
    else -> 5
}

@Composable fun PosterGrid(items: List<AnimeResult>, onOpen: (AnimeResult) -> Unit, isFavorite: (AnimeResult) -> Boolean, onFavorite: (AnimeResult) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = posterColumnCount(maxWidth.value)
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { anime ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AnimePosterCard(anime.title, anime.image, Modifier.fillMaxWidth(), onClick = { onOpen(anime) }, badge = {
                                SpaceBadge(if (anime.dubbed) "DUB" else "LEG", SpaceColors.secondary, Modifier.align(Alignment.TopStart).padding(8.dp))
                            })
                            Text(app.anim.sources.serverName(anime.sourceId), style = MaterialTheme.typography.labelSmall, color = SpaceColors.onSurfaceVariant)
                            TextButton(onClick = { onFavorite(anime) }, modifier = Modifier.fillMaxWidth()) {
                                SpaceIcon("heart", null, Modifier.size(16.dp), if (isFavorite(anime)) SpaceColors.tertiary else SpaceColors.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp)); Text(if (isFavorite(anime)) "Na minha lista" else "Minha lista", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
