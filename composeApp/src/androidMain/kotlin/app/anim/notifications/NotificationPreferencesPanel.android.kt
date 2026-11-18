package app.anim.notifications

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.anim.auth.AccountState
import app.anim.design.SpaceBorderColor
import app.anim.design.SpaceCardColor
import app.anim.design.SpaceColors
import app.anim.library.MangaReference
import app.anim.sources.AnimeResult
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PREFERENCES_FILE = "spaceanime_notifications"

@Composable
actual fun NotificationPreferencesPanel(
    accountState: AccountState,
    animeFavorites: List<AnimeResult>,
    followedManga: List<MangaReference>,
) {
    val context = LocalContext.current
    val local = remember(context) { context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var preferences by remember(local) { mutableStateOf(loadPreferences(local)) }
    var latestToken by remember(local) { mutableStateOf(local.getString("fcm_token", null)) }
    var registeredToken by remember(local) { mutableStateOf(local.getString("registered_fcm_token", null)) }
    var syncedSubscriptions by remember { mutableStateOf<List<NotificationSubscription>?>(null) }
    var digestTime by remember(preferences.digestTime) { mutableStateOf(preferences.digestTime) }
    var pendingPermissionPreferences by remember { mutableStateOf<NotificationPreferences?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(local) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "fcm_token") latestToken = local.getString("fcm_token", null)
        }
        local.registerOnSharedPreferenceChangeListener(listener)
        onDispose { local.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    SideEffect {
        accountState.rememberNotificationDevice(local.getString("device_id", null))
    }

    suspend fun synchronize(current: NotificationPreferences) {
        val deviceId = local.deviceId()
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        val token = local.getString("fcm_token", null) ?: messaging.awaitToken()
        val subscriptions = projectNotificationSubscriptions(animeFavorites, followedManga, current)
        local.edit().putBoolean("pending_removal", true).putString("device_id", deviceId).apply()
        var registered = false
        try {
            accountState.registerNotificationDevice(deviceId, token, current)
            registered = true
            accountState.syncNotificationSubscriptions(subscriptions)
            local.edit()
                .putBoolean("anime", current.anime)
                .putBoolean("manga", current.manga)
                .putBoolean("announcements", current.announcements)
                .putString("digest_time", current.digestTime)
                .putString("time_zone", current.timeZone)
                .putString("fcm_token", token)
                .putString("registered_fcm_token", token)
                .putBoolean("pending_removal", false)
                .apply()
            registeredToken = token
            syncedSubscriptions = subscriptions
            preferences = current
            message = "Preferências salvas neste dispositivo."
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (registered) {
                try {
                    accountState.removeNotificationDevice(deviceId)
                    local.edit().putBoolean("pending_removal", false).apply()
                } catch (_: Exception) {
                    message = "Não foi possível concluir o cadastro nem remover o dispositivo do servidor. Tente novamente."
                }
            }
            messaging.isAutoInitEnabled = false
            try { messaging.deleteToken().awaitCompletion() } catch (_: Exception) { }
            if (message == null) message = "Não foi possível salvar as preferências. Verifique a conexão e tente novamente."
        }
    }

    suspend fun turnOff(current: NotificationPreferences) {
        val deviceId = local.getString("device_id", null)
        var removalConfirmed = deviceId == null
        if (deviceId != null && accountState.isAuthenticated) {
            try {
                accountState.removeNotificationDevice(deviceId)
                removalConfirmed = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                removalConfirmed = false
            }
        }
        local.edit()
            .putBoolean("anime", false)
            .putBoolean("manga", false)
            .putBoolean("announcements", false)
            .putString("digest_time", current.digestTime)
            .putString("time_zone", current.timeZone)
            .putBoolean("pending_removal", !removalConfirmed)
            .remove("fcm_token")
            .remove("registered_fcm_token")
            .apply()
        registeredToken = null
        syncedSubscriptions = null
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
        try { FirebaseMessaging.getInstance().deleteToken().awaitCompletion() } catch (_: Exception) { }
        preferences = current
        message = if (removalConfirmed) "Notificações desativadas neste dispositivo." else
            "Notificações desativadas localmente; a remoção no servidor será tentada novamente."
    }

    suspend fun save(current: NotificationPreferences) {
        if (!current.isValid()) {
            message = "Informe um horário válido e um fuso horário válido."
            return
        }
        if (!current.enabled) {
            turnOff(current)
            return
        }
        if (!accountState.isAuthenticated) {
            message = "Entre na sua conta antes de ativar notificações."
            return
        }
        busy = true
        message = null
        try { synchronize(current) }
        finally { busy = false }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingPermissionPreferences
        pendingPermissionPreferences = null
        if (granted && pending != null) {
            scope.launch { save(pending) }
        } else if (!granted) {
            message = "Permita notificações nas configurações do Android para ativar este recurso."
        }
    }

    fun apply(current: NotificationPreferences) {
        if (!current.isValid()) {
            message = "Informe um horário válido e um fuso horário válido."
            return
        }
        if (current.enabled && !hasNotificationPermission(context)) {
            pendingPermissionPreferences = current
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launch { save(current) }
        }
    }

    LaunchedEffect(accountState.isAuthenticated, animeFavorites, followedManga, preferences, latestToken) {
        if (!accountState.isAuthenticated) return@LaunchedEffect
        if (local.getBoolean("pending_removal", false)) {
            val deviceId = local.getString("device_id", null) ?: return@LaunchedEffect
            try {
                accountState.removeNotificationDevice(deviceId)
                local.edit().putBoolean("pending_removal", false).apply()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "A remoção pendente do dispositivo não foi confirmada."
            }
        }
        val currentSubscriptions = if (preferences.isValid()) {
            projectNotificationSubscriptions(animeFavorites, followedManga, preferences)
        } else {
            emptyList()
        }
        val needsSync = currentSubscriptions != syncedSubscriptions ||
            (latestToken != null && latestToken != registeredToken)
        if (preferences.enabled && preferences.isValid() && hasNotificationPermission(context) && needsSync) {
            busy = true
            try { synchronize(preferences) }
            finally { busy = false }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SpaceCardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceBorderColor),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Receba novidades sem compartilhar seu histórico.", style = MaterialTheme.typography.bodyMedium, color = SpaceColors.onSurfaceVariant)
            PreferenceSwitch("Novos episódios de anime", preferences.anime, busy) {
                apply(preferences.copy(anime = it, timeZone = currentTimeZone(preferences.timeZone)))
            }
            PreferenceSwitch("Novos capítulos de mangá", preferences.manga, busy) {
                apply(preferences.copy(manga = it, timeZone = currentTimeZone(preferences.timeZone)))
            }
            PreferenceSwitch("Avisos importantes do aplicativo", preferences.announcements, busy) {
                apply(preferences.copy(announcements = it, timeZone = currentTimeZone(preferences.timeZone)))
            }
            OutlinedTextField(
                value = digestTime,
                onValueChange = { digestTime = it.take(5) },
                enabled = !busy,
                label = { Text("Horário do resumo (HH:mm)") },
                supportingText = { Text("Fuso horário: ${preferences.timeZone}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && preferences.enabled,
                onClick = { apply(preferences.copy(digestTime = digestTime, timeZone = currentTimeZone(preferences.timeZone))) },
            ) {
                Text("Salvar horário")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = if (it.startsWith("Não foi") || it.startsWith("Informe")) SpaceColors.error else SpaceColors.secondary) }
            Text(
                "A permissão do Android só será solicitada quando você ativar uma categoria.",
                style = MaterialTheme.typography.bodySmall,
                color = SpaceColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Ativado" else "Desativado"
            }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun currentTimeZone(saved: String): String = TimeZone.getDefault().id.takeIf {
    it == "UTC" || Regex("^[A-Za-z][A-Za-z0-9_+-]*(?:/[A-Za-z0-9_+-]+)+$").matches(it)
} ?: saved

private fun loadPreferences(local: SharedPreferences) = NotificationPreferences(
    anime = local.getBoolean("anime", false),
    manga = local.getBoolean("manga", false),
    announcements = local.getBoolean("announcements", false),
    digestTime = local.getString("digest_time", "20:00") ?: "20:00",
    timeZone = local.getString("time_zone", "America/Sao_Paulo") ?: "America/Sao_Paulo",
)

private fun SharedPreferences.deviceId(): String =
    getString("device_id", null) ?: UUID.randomUUID().toString().also { edit().putString("device_id", it).apply() }

private suspend fun FirebaseMessaging.awaitToken(): String = suspendCancellableCoroutine { continuation ->
    token.addOnSuccessListener { value ->
        if (continuation.isActive) {
            if (value.isNullOrBlank()) continuation.resumeWithException(IOException("FCM token unavailable"))
            else continuation.resume(value)
        }
    }.addOnFailureListener {
        if (continuation.isActive) continuation.resumeWithException(IOException("FCM token unavailable"))
    }
}

private suspend fun com.google.android.gms.tasks.Task<*>.awaitCompletion() = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener {
        if (continuation.isActive) continuation.resume(Unit)
    }.addOnFailureListener {
        if (continuation.isActive) continuation.resumeWithException(IOException("FCM token operation failed"))
    }
}
