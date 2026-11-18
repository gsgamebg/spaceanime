package app.anim.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import app.anim.resources.Res
import app.anim.resources.space_hero_v1
import app.anim.resources.profile_cosmos
import app.anim.resources.avatar_nova
import app.anim.resources.avatar_orion
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.anim.design.*
import app.anim.premium.PremiumPlans
import app.anim.auth.AccountState
import app.anim.notifications.NotificationPreferencesPanel
import app.anim.library.*
import app.anim.sources.AnimeResult
import app.anim.player.playbackTime
import app.anim.update.AppRelease
import app.anim.update.UpdateCard
import kotlinx.coroutines.launch

enum class ProfileTab { History, Favorites, Lists, Plans, Settings }

@Composable fun ProfileScreen(
    profiles: Profiles,
    library: LibraryStore,
    mangaFollows: MangaFollowStore,
    initialTab: ProfileTab = ProfileTab.History,
    accountState: AccountState,
    release: AppRelease? = null,
    checking: Boolean = false,
    updateMessage: String? = null,
    onCheckUpdates: () -> Unit = {},
    onOpenAnime: (AnimeResult) -> Unit,
    onContinue: (WatchProgress) -> Unit,
) {
    val state by profiles.state.collectAsState()
    val snapshot by library.state.collectAsState()
    val followedMangaState by mangaFollows.state.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0) }
    var avatarChoice by remember { mutableStateOf("default") }
    var bannerChoice by remember { mutableStateOf("default") }
    var busy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var historyPage by remember { mutableIntStateOf(0) }
    var favoritesPage by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf("Favoritos") }
    val watching = remember(snapshot.progress) { snapshot.progress.filter { !it.completed && it.positionMs > 0 }.map { it.context.anime }.distinctBy { it.commentIdentity() } }
    val watchedEpisodes = snapshot.progress.count { it.completed }
    val favorites = snapshot.favorites
    fun edit() { name = state.active.name; username = state.active.username; bio = state.active.bio; color = state.active.color; avatarChoice = accountState.avatarAsset; bannerChoice = accountState.bannerAsset; saveError = null; creating = false; editing = true }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = SpaceCardColor, contentColor = Color.White) {
            Box(Modifier.fillMaxWidth()) {
                Image(painterResource(if (accountState.bannerAsset == "cosmos") Res.drawable.profile_cosmos else Res.drawable.space_hero_v1), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE8080611), Color(0xC0080611), Color(0x66080611)))))
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 520.dp) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfilePicture(accountState.avatarAsset, state.active.name, state.active.color, 76.dp)
                            Text(state.active.name, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text(accountState.email ?: "Perfil da conta", color = SpaceColors.secondary)
                            Text(state.active.bio.ifBlank { "Seu universo de histórias começa aqui." }, color = Color(0xFFE2DCEC))
                            OutlinedButton(enabled = !busy, onClick = ::edit) { Text("Editar perfil") }
                        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            ProfilePicture(accountState.avatarAsset, state.active.name, state.active.color, 90.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(state.active.name, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                                Text(accountState.email ?: "Perfil da conta", color = SpaceColors.secondary)
                                Text(state.active.bio.ifBlank { "Seu universo de histórias começa aqui." }, color = Color(0xFFE2DCEC))
                            }
                            OutlinedButton(enabled = !busy, onClick = ::edit) { Text("Editar perfil") }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Episódios vistos", watchedEpisodes.toString(), Modifier.widthIn(min = 130.dp))
                        StatPill("Favoritos", favorites.size.toString(), Modifier.widthIn(min = 130.dp))
                        StatPill("Animes em andamento", watching.size.toString(), Modifier.widthIn(min = 130.dp))
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProfileTab.entries.forEach { entry -> GenreChip(when (entry) { ProfileTab.History -> "Histórico"; ProfileTab.Favorites -> "Favoritos"; ProfileTab.Lists -> "Minha lista"; ProfileTab.Plans -> "Planos"; ProfileTab.Settings -> "Configurações" }, tab == entry) { tab = entry } }
        }
        when (tab) {
            ProfileTab.History -> {
                val history = snapshot.progress.sortedByDescending { it.updatedAt }
                if (history.isEmpty()) SpaceEmptyState("Sua primeira sessão está por vir", "O histórico aparece aqui quando você começa um episódio.")
                val page = historyPage.coerceAtMost(((history.size - 1) / 12).coerceAtLeast(0))
                history.drop(page * 12).take(12).forEach { item ->
                    SpacePanel(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(item.context.anime.image, item.context.anime.title, Modifier.width(94.dp).height(76.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.context.anime.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.context.episode.title, style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
                                Text("${playbackTime(item.positionMs)} / ${playbackTime(item.durationMs)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LinearProgressIndicator(progress = { (item.positionMs.toFloat() / item.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onContinue(item) }) { Text(if (item.completed) "Rever episódio" else "Continuar assistindo") }
                            TextButton(onClick = { onOpenAnime(item.context.anime) }) { Text("Ver episódios") }
                        }
                    }
                }
                if (history.size > 12) FlowRow {
                    TextButton(enabled = page > 0, onClick = { historyPage = page - 1 }) { Text("Anterior") }
                    TextButton(enabled = (page + 1) * 12 < history.size, onClick = { historyPage = page + 1 }) { Text("Próxima") }
                }
            }
            ProfileTab.Favorites, ProfileTab.Lists -> {
                val items = if (tab == ProfileTab.Lists && collection == "Assistindo") watching else favorites
                if (tab == ProfileTab.Lists) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GenreChip("Favoritos", collection == "Favoritos") { collection = "Favoritos"; favoritesPage = 0 }
                        GenreChip("Assistindo", collection == "Assistindo") { collection = "Assistindo"; favoritesPage = 0 }
                    }
                }
                if (items.isEmpty()) SpaceEmptyState("Esta lista ainda está vazia", "Adicione um anime aos favoritos ou comece a assistir.")
                val page = favoritesPage.coerceAtMost(((items.size - 1) / 20).coerceAtLeast(0))
                PosterGrid(items.drop(page * 20).take(20), onOpenAnime, { snapshot.isFavorite(it) }, library::toggleFavorite)
                if (items.size > 20) FlowRow {
                    TextButton(enabled = page > 0, onClick = { favoritesPage = page - 1 }) { Text("Anterior") }
                    TextButton(enabled = (page + 1) * 20 < items.size, onClick = { favoritesPage = page + 1 }) { Text("Próxima") }
                }
            }
            ProfileTab.Plans -> PremiumPlans()
            ProfileTab.Settings -> {
                SectionHeader("Sua conta", "Nome e biografia vinculados à sua conta")
                SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF0F172A)) {
                    Text("Conectado como: ${accountState.email.orEmpty().ifBlank { state.active.name }}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Sua biblioteca e histórico permanecem salvos localmente neste dispositivo.", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { accountState.signOut() }
                            finally { busy = false }
                        }
                    }) {
                        Text("Sair da conta")
                    }
                }

                SectionHeader("Notificações", "Escolha quais novidades deseja receber neste dispositivo")
                NotificationPreferencesPanel(accountState, favorites, followedMangaState.followed)

                SectionHeader("Atualizações do SpaceAnime", "Versão e suporte contínuo aos servidores Space")
                UpdateCard(release, checking, updateMessage, onCheckUpdates)

                SectionHeader("Privacidade e Segurança", "Seus dados locais protegidos")
                SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF0F172A)) {
                    Text(
                        "Perfis e biblioteca ficam neste dispositivo. Capas e consultas usam conexões seguras HTTPS sem anúncios invasivos ou rastreamento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpaceColors.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaceBadge("HTTPS Seguro", SpaceColors.secondary)
                        SpaceBadge("Dados Locais", SpaceColors.primary)
                        SpaceBadge("Privacidade Space", SpaceColors.tertiary)
                    }
                }

                SectionHeader("Acessibilidade & Preferências", "Opções visuais do aplicativo")
                SpacePanel(modifier = Modifier.fillMaxWidth(), tonal = Color(0xFF0F172A)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reduzir movimento", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Desativa transições animadas e suaviza rolagem.", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
                        }
                        Switch(state.active.reduceMotion, enabled = !busy, onCheckedChange = { value -> busy = true; scope.launch { try { profiles.setReducedMotion(value) } finally { busy = false } } })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reproduzir próximo episódio automaticamente", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Desativado por padrão. Avança apenas quando há próximo episódio na fila.", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.active.autoPlayNext,
                            enabled = !busy,
                            onCheckedChange = { value ->
                                busy = true
                                scope.launch {
                                    try { profiles.setAutoPlayNext(value) }
                                    finally { busy = false }
                                }
                            },
                        )
                    }
                }
            }
        }
        snapshot.error?.let { Text(it, color = SpaceColors.error) }
        state.error?.let { Text(it, color = SpaceColors.error) }
    }
    if (editing) AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text(if (creating) "Novo perfil" else "Editar perfil") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfilePicture(avatarChoice, name.ifBlank { "S" }, color, 64.dp)
            OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nome de exibição") })
            OutlinedTextField(bio, { bio = it.take(140) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, label = { Text("Biografia") }, supportingText = { Text("${bio.length}/140") })
            Text("Escolha seu avatar", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("default" to "Inicial", "nova" to "Nova", "orion" to "Orion").forEach { (id, label) ->
                    Surface(onClick = { avatarChoice = id }, shape = RoundedCornerShape(14.dp), border = BorderStroke(if (avatarChoice == id) 2.dp else 1.dp, if (avatarChoice == id) SpaceColors.primary else SpaceBorderColor), color = SpaceCardColor) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { ProfilePicture(id, name.ifBlank { "S" }, color, 52.dp); Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            Text("Escolha seu banner", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("default" to "Explorador", "cosmos" to "Cosmos").forEach { (id, label) ->
                    Surface(onClick = { bannerChoice = id }, shape = RoundedCornerShape(12.dp), border = BorderStroke(if (bannerChoice == id) 2.dp else 1.dp, if (bannerChoice == id) SpaceColors.primary else SpaceBorderColor), color = SpaceCardColor) {
                        Column(Modifier.padding(6.dp)) {
                            Image(painterResource(if (id == "cosmos") Res.drawable.profile_cosmos else Res.drawable.space_hero_v1), contentDescription = "Banner $label", modifier = Modifier.width(120.dp).height(54.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            state.error?.let { Text(it, color = SpaceColors.error) }
            saveError?.let { Text(it, color = SpaceColors.error) }
        }
    }, confirmButton = { Button(enabled = !busy && name.isNotBlank(), onClick = {
        busy = true; saveError = null; scope.launch { try {
            accountState.saveProfile(name.trim(), bio.trim())
            accountState.saveArtwork(avatarChoice, bannerChoice)
            profiles.rename(name, color, username, bio)
            if (profiles.state.value.error == null) editing = false
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { saveError = "Não foi possível salvar na conta. Confira a conexão e tente novamente." } finally { busy = false } }
    }) { Text("Salvar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { editing = false }) { Text("Cancelar") } })
}
@Composable internal fun ProfilePicture(id: String, name: String, color: Int, size: androidx.compose.ui.unit.Dp) {
    if (id == "default") ProfileAvatar(name, color, size)
    else Image(painterResource(if (id == "nova") Res.drawable.avatar_nova else Res.drawable.avatar_orion), contentDescription = "Avatar de $name", modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}
private fun AnimeResult.commentIdentity() = Triple(sourceId, id, dubbed)
