package app.anim.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.anim.design.SpaceColors
import app.anim.design.SpaceIcon
import app.anim.notifications.NotificationPreferences
import app.anim.notifications.NotificationSubscription
import app.anim.profile.Profiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual class AccountState internal constructor(
    internal val client: AccountClient = AccountClient(),
    private val vault: SessionVault? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    internal var session by mutableStateOf<AccountSession?>(null)
    /** True while a saved session is being checked at startup, so the login form is not flashed. */
    internal var restoring by mutableStateOf(vault != null)
    internal var restoreMessage by mutableStateOf<String?>(null)
    private var notificationDeviceId: String? = null
    private val notificationOperations = Mutex()
    /** Supabase rotates refresh tokens; two concurrent refreshes would revoke the session. */
    private val sessionOperations = Mutex()
    internal var notificationStatus by mutableStateOf<String?>(null)
    internal fun rememberNotificationDevice(deviceId: String?) {
        notificationDeviceId = deviceId
    }
    actual val isAuthenticated: Boolean get() = session != null
    actual val email: String? get() = session?.email
    actual val notificationStatusMessage: String? get() = notificationStatus

    private fun persist(current: AccountSession) {
        if (vault == null || current.refreshToken.isBlank()) return
        try { vault.write(current) } catch (_: Exception) {
            restoreMessage = "Não foi possível salvar a sessão neste dispositivo; será preciso entrar novamente ao reabrir o app."
        }
    }
    private fun forget() { try { vault?.clear() } catch (_: Exception) {} }

    internal fun signedIn(current: AccountSession) {
        session = current
        restoring = false
        restoreMessage = null
        persist(current)
    }

    /** Loads the encrypted session saved on this device, renewing it when the access token expired. */
    internal suspend fun restore(): RefreshOutcome? = sessionOperations.withLock {
        try {
            if (session != null) return@withLock null
            val stored = vault?.read() ?: return@withLock null
            if (stored.expiresAt - clock() > RENEW_MARGIN_MS) {
                session = stored
                return@withLock RefreshOutcome.Renewed(stored)
            }
            val outcome = client.refresh(stored)
            when (outcome) {
                is RefreshOutcome.Renewed -> if (session == null) { session = outcome.session; persist(outcome.session) }
                RefreshOutcome.Rejected -> { forget(); restoreMessage = "Sua sessão expirou. Entre novamente." }
                RefreshOutcome.Unavailable -> restoreMessage = "Sem conexão para restaurar sua sessão. Tentaremos novamente automaticamente."
            }
            outcome
        } finally { restoring = false }
    }

    /** Renews [current] with its refresh token; returns null when the session changed meanwhile. */
    internal suspend fun renew(current: AccountSession): RefreshOutcome? = sessionOperations.withLock {
        if (session != current) return@withLock null
        val outcome = client.refresh(current)
        if (session != current) return@withLock null
        when (outcome) {
            is RefreshOutcome.Renewed -> { session = outcome.session; persist(outcome.session) }
            RefreshOutcome.Rejected -> { session = null; forget(); restoreMessage = "Sua sessão expirou. Entre novamente." }
            RefreshOutcome.Unavailable -> Unit
        }
        outcome
    }

    actual suspend fun signOut(): Boolean = notificationOperations.withLock {
        forget()
        val current = session ?: return@withLock true
        val deviceRemoved = notificationDeviceId?.let { deviceId ->
            try { client.deleteNotificationDevice(current, deviceId); true }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { false }
        } ?: true
        val revoked = try { client.signOut(current); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
        if (session == current) session = null
        notificationDeviceId = null
        notificationStatus = if (deviceRemoved && revoked) null else
            "Sessão encerrada neste dispositivo, mas a remoção de notificações ou a saída do servidor não foi confirmada."
        revoked && deviceRemoved
    }
    internal var currentAvatar by mutableStateOf("default")
    internal var currentBanner by mutableStateOf("default")
    actual val avatarAsset: String get() = currentAvatar
    actual val bannerAsset: String get() = currentBanner
    actual suspend fun saveProfile(name: String, bio: String) {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.saveOnlineProfile(current, name, bio)
        if (session != current) throw IllegalStateException("Sessão alterada")
    }
    actual suspend fun saveArtwork(avatar: String, banner: String) {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.saveArtwork(current, avatar, banner)
        if (session != current) throw IllegalStateException("Sessão alterada")
        currentAvatar = avatar; currentBanner = banner
    }
    actual suspend fun registerNotificationDevice(deviceId: String, pushToken: String, preferences: NotificationPreferences) =
        notificationOperations.withLock {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.registerNotificationDevice(current, deviceId, pushToken, preferences)
        if (session != current) throw IllegalStateException("Sessão alterada")
        notificationDeviceId = deviceId
    }
    actual suspend fun updateNotificationPreferences(deviceId: String, preferences: NotificationPreferences) =
        notificationOperations.withLock {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.updateNotificationPreferences(current, deviceId, preferences)
        if (session != current) throw IllegalStateException("Sessão alterada")
        notificationDeviceId = deviceId.takeIf { preferences.enabled }
    }
    actual suspend fun syncNotificationSubscriptions(subscriptions: List<NotificationSubscription>) =
        notificationOperations.withLock {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.syncNotificationSubscriptions(current, subscriptions)
        if (session != current) throw IllegalStateException("Sessão alterada")
    }
    actual suspend fun removeNotificationDevice(deviceId: String) = notificationOperations.withLock {
        val current = session ?: throw IllegalStateException("Sessão encerrada")
        client.deleteNotificationDevice(current, deviceId)
        if (session != current) throw IllegalStateException("Sessão alterada")
        if (notificationDeviceId == deviceId) notificationDeviceId = null
    }
    private companion object { const val RENEW_MARGIN_MS = 60_000L }
}
@Composable actual fun rememberAccountState(): AccountState {
    val vault = rememberSessionVault()
    val state = remember(vault) { AccountState(vault = vault) }
    LaunchedEffect(state) {
        // Retry while offline; stops once signed in (restored or by hand) or the token is refused.
        while (state.session == null) {
            val outcome = state.restore() ?: break
            if (outcome is RefreshOutcome.Renewed) {
                val restored = outcome.session
                try {
                    state.client.getOnlineProfile(restored)?.let { state.currentAvatar = it.avatar; state.currentBanner = it.banner }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
                break
            }
            if (outcome == RefreshOutcome.Rejected) break
            delay(30_000)
        }
    }
    val current = state.session
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        var wait = current.expiresAt - System.currentTimeMillis() - 60_000
        while (state.session == current) {
            delay(wait.coerceAtLeast(0))
            when (state.renew(current)) {
                // A new session object restarts this effect; a refused token signs out (see renew).
                null, is RefreshOutcome.Renewed, RefreshOutcome.Rejected -> break
                RefreshOutcome.Unavailable -> wait = 30_000
            }
        }
    }
    return state
}
@Composable actual fun AccountScreen(state: AccountState, profiles: Profiles, onBack: () -> Unit, mandatory: Boolean) {
    val client = state.client
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val session = state.session
    val profilesState by profiles.state.collectAsState()
    val profile = profilesState.active
    var message by remember(state.notificationStatusMessage) { mutableStateOf(state.notificationStatusMessage) }
    var busy by remember { mutableStateOf(false) }
    var signupMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val passwordVisual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
    val passwordToggle: @Composable () -> Unit = {
        IconButton(onClick = { showPassword = !showPassword }) {
            SpaceIcon(if (showPassword) "eye-off" else "eye", if (showPassword) "Ocultar senha" else "Mostrar senha", glow = false)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF24123F), Color(0xFF110C20))), RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!mandatory) TextButton(onClick = onBack) { Text("← Voltar ao perfil") }
        Text("✦  SPACEANIME", color = SpaceColors.secondary, style = MaterialTheme.typography.labelLarge)
        if (session == null && state.restoring) {
            Text("Restaurando sua sessão…", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
        Text(if (session != null) "Sua conta" else if (signupMode) "Crie sua conta" else "Bem-vindo de volta", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(if (session != null) "Seu acesso está conectado neste dispositivo." else "Entre no seu universo de animes e mangás.", color = SpaceColors.onSurfaceVariant)
        if (session == null) {
            state.restoreMessage?.let { Text(it, color = SpaceColors.secondary) }
            OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { if (it.length <= 128) password = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text("Senha") }, visualTransformation = passwordVisual, trailingIcon = passwordToggle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (signupMode) OutlinedTextField(confirmPassword, { if (it.length <= 128) confirmPassword = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text("Confirmar senha") }, visualTransformation = passwordVisual, trailingIcon = passwordToggle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = confirmPassword.isNotEmpty() && confirmPassword != password, supportingText = { if (confirmPassword.isNotEmpty() && confirmPassword != password) Text("As senhas não coincidem") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun submit(signup: Boolean) {
                    val submittedEmail = email.trim(); val submittedPassword = password
                    password = ""; confirmPassword = ""; showPassword = false; busy = true; message = null
                    scope.launch {
                        try {
                            val result = client.authenticate(submittedEmail, submittedPassword, signup)
                            val signedIn = result.session
                            if (signedIn != null) {
                                var remote = client.getOnlineProfile(signedIn)
                                if (remote == null) {
                                    client.linkInitialProfile(signedIn, profiles.state.value.active)
                                    remote = client.getOnlineProfile(signedIn) ?: error("profile unavailable")
                                }
                                val local = profiles.state.value.active
                                profiles.rename(remote.name, local.color, local.username, remote.bio)
                                if (profiles.state.value.error != null) error("local profile unavailable")
                                state.currentAvatar = remote.avatar; state.currentBanner = remote.banner
                                state.signedIn(signedIn)
                            }
                            message = result.message
                        }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { message = "Não foi possível conectar. Confira sua internet e tente novamente." }
                        finally { busy = false }
                    }
                }
                Button(onClick = { submit(signupMode) }, enabled = !busy && email.trim().length in 3..254 && email.contains('@') && password.length in 6..128 && (!signupMode || confirmPassword == password)) { Text(if (signupMode) "Criar conta" else "Entrar") }
                TextButton(enabled = !busy, onClick = { signupMode = !signupMode; confirmPassword = ""; message = null }) { Text(if (signupMode) "Já tenho conta" else "Criar uma conta") }
            }
        } else {
            Text("Conectado: ${session!!.email}", color = Color.White)
            Text("Seu nome e sua biografia são salvos na conta.", color = SpaceColors.onSurfaceVariant)
            TextButton(enabled = !busy, onClick = {
                password = ""; busy = true
                scope.launch {
                    try {
                        message = if (state.signOut()) "Sessão encerrada." else "Sessão encerrada neste dispositivo, mas a remoção de notificações ou a saída do servidor não foi confirmada."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { message = "Sessão encerrada neste dispositivo; não foi possível confirmar a saída no servidor." }
                    finally { busy = false }
                }
            }) { Text("Sair da conta") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = SpaceColors.secondary) }
        Text("Você continua conectado ao reabrir o app: a sessão fica salva com criptografia neste dispositivo até você sair da conta. A senha nunca é armazenada.", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
        }
    }
    }
}
