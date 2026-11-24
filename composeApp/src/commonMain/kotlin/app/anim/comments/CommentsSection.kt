package app.anim.comments

import androidx.compose.foundation.BorderStroke
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Cabeçalho ─────────────────────────────────────────────────────────────
        SectionHeader("Comentários da Comunidade")

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF22123A),
            border = BorderStroke(1.dp, Color(0x33A855F7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("✦", fontSize = 16.sp, color = Color(0xFF2DD4BF))
                Text(
                    "Prévia local: seus comentários ficam salvos neste perfil para você acompanhar suas reações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
            }
        }

        // ── Campo de novo comentário ───────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF120B20),
            border = BorderStroke(1.dp, Color(0x33A855F7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProfileAvatar(author, 0, size = 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("O que achou deste anime?") },
                        placeholder = { Text("Compartilhe suas impressões, teorias ou nota...", color = Color(0xFF64748B)) },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        supportingText = { Text("${draft.length}/1000 · $author", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF05030C),
                            unfocusedContainerColor = Color(0xFF05030C),
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color(0x33A855F7),
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        ) { Text("Comentar", fontWeight = FontWeight.SemiBold, color = Color.White) }
                    }
                }
            }
        }

        state.error?.let { Text(it, color = SpaceColors.error) }

        // ── Lista de comentários ───────────────────────────────────────────────────
        if (comments.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF120B20),
                border = BorderStroke(1.dp, Color(0x22A855F7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("💬", fontSize = 32.sp)
                    Text(
                        "Nenhum comentário ainda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        "Seja o primeiro a compartilhar sua opinião sobre este anime!",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        val current = page.coerceAtMost(((comments.size - 1) / 10).coerceAtLeast(0))
        comments.drop(current * 10).take(10).forEach { comment ->
            key(comment.id) {
                var revealed by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF120B20),
                    border = BorderStroke(1.dp, Color(0x22A855F7)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Avatar + autor + data
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfileAvatar(comment.author, comment.author.length % 4, size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(comment.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFF8FAFC))
                                Text(discoveryTime(comment.createdAt) + " · local", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                            if (comment.spoiler) SpaceBadge("SPOILER", color = Color(0xFFEF4444).copy(alpha = .8f))
                        }
                        // Texto
                        if (comment.spoiler && !revealed) {
                            Surface(
                                onClick = { revealed = true },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF22123A),
                                border = BorderStroke(1.dp, Color(0x33A855F7))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("👁", fontSize = 16.sp)
                                    Text("Toque para revelar o spoiler", color = Color(0xFF2DD4BF), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text(comment.text, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodyMedium)
                        }
                        // Ação de excluir
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                enabled = !busy,
                                onClick = { busy = true; scope.launch { try { store.remove(comment.id) } finally { busy = false } } },
                            ) { Text("Excluir", color = Color(0xFFEF4444).copy(alpha = .8f), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
        if (comments.size > 10) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = current > 0, onClick = { page = current - 1 }) { Text("← Anterior", color = Color(0xFF2DD4BF)) }
            TextButton(enabled = (current + 1) * 10 < comments.size, onClick = { page = current + 1 }) { Text("Próxima →", color = Color(0xFF2DD4BF)) }
        }
    }
}
