package app.anim.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.design.*
import app.anim.sources.AnimeResult
import app.anim.discovery.discoveryTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CommentsSection(anime: AnimeResult, author: String, store: CommentsStore) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember(anime) { mutableStateOf("") }
    var spoiler by remember(anime) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var page by remember(anime) { mutableIntStateOf(0) }
    val comments = remember(state.items, anime) { state.items.filter { it.animeKey == anime.commentKey() } }

    // ── Cabeçalho ─────────────────────────────────────────────────────────────
    SectionHeader("Comentários 💬")
    Spacer(Modifier.height(4.dp))
    Surface(shape = RoundedCornerShape(10.dp), color = SpaceColors.primaryContainer.copy(alpha = .5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ℹ", fontSize = 16.sp, color = SpaceColors.primary)
            Text(
                "Prévia local · só você vê os comentários deste perfil. Ainda não são publicados.",
                style = MaterialTheme.typography.bodySmall,
                color = SpaceColors.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── Campo de novo comentário ───────────────────────────────────────────────
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileAvatar(author, 0, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                draft, { draft = it.take(1000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("O que achou deste anime?") },
                minLines = 3, maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                supportingText = { Text("${draft.length}/1000 · $author") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GenreChip("⚠ Contém spoiler", selected = spoiler, onClick = { spoiler = !spoiler })

                Button(
                    enabled = draft.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                store.add(anime, author, draft, spoiler)
                                if (store.state.value.error == null) { draft = ""; spoiler = false; page = 0 }
                            } finally { busy = false }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceColors.primary),
                ) { Text("Comentar", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    state.error?.let { Text(it, color = SpaceColors.error, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(16.dp))

    // ── Lista de comentários ───────────────────────────────────────────────────
    if (comments.isEmpty()) {
        Surface(shape = RoundedCornerShape(14.dp), color = SpaceColors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💬", fontSize = 28.sp)
                Text("Nenhum comentário ainda", style = MaterialTheme.typography.titleMedium)
                Text("Seja o primeiro a comentar sobre este anime!", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val current = page.coerceAtMost(((comments.size - 1) / 10).coerceAtLeast(0))
    comments.drop(current * 10).take(10).forEach { comment ->
        key(comment.id) {
            var revealed by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SpaceColors.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Avatar + autor + data
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileAvatar(comment.author, comment.author.length % 4, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(comment.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(discoveryTime(comment.createdAt) + " · comentário local", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
                        }
                        if (comment.spoiler) SpaceBadge("SPOILER", color = SpaceColors.error.copy(alpha = .75f))
                    }
                    // Texto
                    if (comment.spoiler && !revealed) {
                        Surface(
                            onClick = { revealed = true },
                            shape = RoundedCornerShape(10.dp),
                            color = SpaceColors.primaryContainer.copy(alpha = .4f),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("👁", fontSize = 16.sp)
                                Text("Toque para revelar o spoiler", color = SpaceColors.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(comment.text, color = SpaceColors.onSurface)
                    }
                    // Ação de excluir
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            enabled = !busy,
                            onClick = { busy = true; scope.launch { try { store.remove(comment.id) } finally { busy = false } } },
                        ) { Text("Excluir", color = SpaceColors.error.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
    if (comments.size > 10) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = current > 0, onClick = { page = current - 1 }) { Text("← Anterior") }
        TextButton(enabled = (current + 1) * 10 < comments.size, onClick = { page = current + 1 }) { Text("Próxima →") }
    }
}

