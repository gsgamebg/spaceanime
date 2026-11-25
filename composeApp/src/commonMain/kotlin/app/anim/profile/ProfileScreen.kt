package app.anim.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import app.anim.premium.VipScreen
import app.anim.premium.VipPill
import app.anim.auth.AccountState
import app.anim.notifications.NotificationPreferencesPanel
import app.anim.library.*
import app.anim.sources.AnimeResult
import app.anim.player.playbackTime
import app.anim.telemetry.rememberTelemetry
import app.anim.update.APP_VERSION
import app.anim.update.AppRelease
import app.anim.update.UpdateCard
import kotlinx.coroutines.launch

enum class ProfileTab { History, Favorites, Lists, Account, Plans, Settings }

private val CardColor = Color(0xFF0C0817)
private val CardBorder = Color(0x22A855F7)
private val Muted = Color(0xFF94A3B8)

/** Visible tabs; Favorites is shown inside "Minha lista" so the two never duplicate each other. */
/** Account, VIP and Settings are separate pages so none of them piles everything up. */
private val visibleTabs = listOf(ProfileTab.History, ProfileTab.Lists, ProfileTab.Account, ProfileTab.Plans, ProfileTab.Settings)
private fun ProfileTab.label() = when (this) {
    ProfileTab.History -> "Histórico"
    ProfileTab.Favorites, ProfileTab.Lists -> "Minha lista"
    ProfileTab.Account -> "Conta"
    ProfileTab.Plans -> "✦ VIP"
    ProfileTab.Settings -> "Configurações"
}

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
    val telemetry = rememberTelemetry()
    val telemetryEnabled by telemetry.enabled.collectAsState()
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
    var tab by remember(initialTab) { mutableStateOf(if (initialTab == ProfileTab.Favorites) ProfileTab.Lists else initialTab) }
    var historyPage by remember { mutableIntStateOf(0) }
    var favoritesPage by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf("Favoritos") }
    val watching = remember(snapshot.progress) { snapshot.progress.filter { !it.completed && it.positionMs > 0 }.map { it.context.anime }.distinctBy { it.commentIdentity() } }
    val watchedEpisodes = snapshot.progress.count { it.completed }
    val favorites = snapshot.favorites
    fun edit() { name = state.active.name; username = state.active.username; bio = state.active.bio; color = state.active.color; avatarChoice = accountState.avatarAsset; bannerChoice = accountState.bannerAsset; saveError = null; creating = false; editing = true }
    fun toggle(action: suspend () -> Unit) { busy = true; scope.launch { try { action() } finally { busy = false } } }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // ── Cabeçalho: banner, avatar, nome e estatísticas (um layout só, adaptado à largura) ──
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 640.dp
            // One proportional, full-bleed banner (≈3.6:1) for every width — no blur, no thin over-zoomed strip.
            val bannerHeight = (maxWidth / 3.6f).coerceIn(140.dp, 420.dp)
            val avatarSize = if (compact) 88.dp else 116.dp
            val banner = painterResource(if (accountState.bannerAsset == "cosmos") Res.drawable.profile_cosmos else Res.drawable.space_hero_v1)
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = CardColor, border = BorderStroke(1.dp, CardBorder), contentColor = Color.White) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Banner + avatar straddling its bottom edge (the space below is reserved, not offset).
                    Box(Modifier.fillMaxWidth().height(bannerHeight + avatarSize / 2)) {
                        Image(
                            banner, null,
                            Modifier.fillMaxWidth().height(bannerHeight).clipToBounds(),
                            alignment = BiasAlignment(0f, -0.15f),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.fillMaxWidth().height(bannerHeight).background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to CardColor)))
                        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            VipPill(onClick = { tab = ProfileTab.Plans })
                            Surface(onClick = ::edit, enabled = !busy, shape = CircleShape, color = Color(0xB305030C), border = BorderStroke(1.dp, Color(0x55A855F7))) {
                                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { SpaceIcon("edit", "Editar perfil", Modifier.size(16.dp), SpaceLagoon, glow = false) }
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter).border(4.dp, CardColor, CircleShape)) {
                            app.anim.premium.VipFrame(accountState.vip.plan.takeIf { accountState.vip.active }, avatarSize) { ProfilePicture(accountState.avatarAsset, state.active.name, state.active.color, avatarSize) }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(state.active.name, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))
                        Text(accountState.email ?: "Perfil local", style = MaterialTheme.typography.bodySmall, color = SpaceLagoon)
                        if (accountState.vip.active) app.anim.premium.VipBadge(accountState.vip.plan)
                        if (state.active.bio.isNotBlank()) Text(state.active.bio, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill("Episódios vistos", watchedEpisodes.toString())
                            StatPill("Favoritos", favorites.size.toString())
                            StatPill("Em andamento", watching.size.toString())
                        }
                    }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            visibleTabs.forEach { entry -> GenreChip(entry.label(), tab == entry) { tab = entry } }
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
                val items = if (collection == "Assistindo") watching else favorites
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenreChip("Favoritos · ${favorites.size}", collection == "Favoritos") { collection = "Favoritos"; favoritesPage = 0 }
                    GenreChip("Assistindo · ${watching.size}", collection == "Assistindo") { collection = "Assistindo"; favoritesPage = 0 }
                }
                if (items.isEmpty()) SpaceEmptyState("Esta lista ainda está vazia", "Adicione um anime aos favoritos ou comece a assistir.")
                val page = favoritesPage.coerceAtMost(((items.size - 1) / 20).coerceAtLeast(0))
                PosterGrid(items.drop(page * 20).take(20), onOpenAnime, { snapshot.isFavorite(it) }, library::toggleFavorite)
                if (items.size > 20) FlowRow {
                    TextButton(enabled = page > 0, onClick = { favoritesPage = page - 1 }) { Text("Anterior") }
                    TextButton(enabled = (page + 1) * 20 < items.size, onClick = { favoritesPage = page + 1 }) { Text("Próxima") }
                }
            }
            ProfileTab.Plans -> VipScreen(accountState)
            ProfileTab.Account -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroup("Sua conta", "user-round") {
                    SettingsRow("Conectado como", accountState.email.orEmpty().ifBlank { state.active.name })
                    SettingsRow("Perfil público", "Nome, biografia, avatar e banner ficam salvos na conta.") {
                        OutlinedButton(enabled = !busy, onClick = ::edit, shape = RoundedCornerShape(12.dp)) { Text("Editar perfil") }
                    }
                    SettingsRow("Sessão", "Você continua conectado ao reabrir o app; a sessão fica criptografada neste dispositivo.") {
                        OutlinedButton(enabled = !busy, onClick = { toggle { accountState.signOut() } }, shape = RoundedCornerShape(12.dp)) { Text("Sair da conta") }
                    }
                }
                SettingsGroup("Seus dados", "library") {
                    SettingsRow("Biblioteca e histórico", "${favorites.size} favoritos e ${snapshot.progress.size} episódios no histórico, salvos neste dispositivo.")
                }
                SettingsGroup("Notificações", "bell") {
                    NotificationPreferencesPanel(accountState, favorites, followedMangaState.followed)
                }
            }
            ProfileTab.Settings -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroup("Reprodução", "play") {
                    SettingsRow("Próximo episódio automático", "Avança apenas quando há próximo episódio na fila.") {
                        Switch(state.active.autoPlayNext, enabled = !busy, onCheckedChange = { value -> toggle { profiles.setAutoPlayNext(value) } })
                    }
                }
                SettingsGroup("Aparência", "sparkles") {
                    SettingsRow("Reduzir movimento", "Desativa transições animadas e suaviza a rolagem.") {
                        Switch(state.active.reduceMotion, enabled = !busy, onCheckedChange = { value -> toggle { profiles.setReducedMotion(value) } })
                    }
                }
                SettingsGroup("Privacidade", "shield-check") {
                    SettingsRow(
                        "Enviar dados de uso",
                        "Versão do app, modelo e sistema do aparelho, idioma, fuso e endereço IP, com um identificador aleatório desta instalação. Usado para estatísticas e suporte; apagado após 90 dias.",
                    ) { Switch(telemetryEnabled, onCheckedChange = telemetry::setEnabled) }
                    SettingsRow("Anúncios", "Somente banners na Início e no Catálogo do Android, após o seu consentimento.")
                }
                SettingsGroup("Sobre", "refresh-cw") {
                    SettingsRow("Versão instalada", "SpaceAnime $APP_VERSION")
                    UpdateCard(release, checking, updateMessage, onCheckUpdates)
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

/** One settings card: icon + title header, rows separated by thin dividers. */
@Composable private fun SettingsGroup(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardColor, border = BorderStroke(1.dp, CardBorder), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x26A855F7)), contentAlignment = Alignment.Center) {
                    SpaceIcon(icon, null, Modifier.size(16.dp), Color(0xFF2DD4BF), glow = false)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF8FAFC))
            }
            HorizontalDivider(color = CardBorder)
            content()
        }
    }
}

@Composable private fun SettingsRow(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        trailing?.invoke()
    }
}
