package app.anim.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.anim.auth.BACKEND_URL
import app.anim.update.APP_VERSION
import app.anim.update.APP_VERSION_CODE
import app.anim.update.updatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class DeviceInfo(val osVersion: String, val deviceModel: String)

/** Platform: directory for the telemetry state file and a short device description. */
@Composable internal expect fun telemetryDirectory(): File
internal expect fun currentDeviceInfo(): DeviceInfo

private fun clean(value: String, max: Int) = value.filter { it.isLetterOrDigit() || it in " ._()/+:,-" }.trim().take(max)

internal fun telemetryEvent(installId: String, device: DeviceInfo, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): JsonObject =
    buildJsonObject {
        put("schemaVersion", 1)
        put("install_id", installId)
        put("platform", updatePlatform())
        put("app_version", APP_VERSION)
        put("app_code", APP_VERSION_CODE)
        put("os_version", clean(device.osVersion, 64))
        put("device_model", clean(device.deviceModel, 96))
        put("locale", locale.toLanguageTag().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(16))
        put("time_zone", zone.id.filter { it.isLetterOrDigit() || it in "_+/-" }.take(64))
    }

/** State file: random install ID (not a hardware identifier), opt-out flag and last send time. */
internal class FileTelemetry(
    private val file: File,
    private val device: () -> DeviceInfo,
    private val endpoint: String = "$BACKEND_URL/api/v1/telemetry",
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build(),
) : TelemetryController {
    private data class State(val installId: String, val enabled: Boolean, val lastSent: Long)
    private var state = load()
    private val enabledFlow = MutableStateFlow(state.enabled)
    override val enabled: StateFlow<Boolean> = enabledFlow

    private fun load(): State = try {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val id = root.getValue("install_id").jsonPrimitive.content
        UUID.fromString(id)
        State(id, root["enabled"]?.jsonPrimitive?.booleanOrNull ?: true, root["last_sent"]?.jsonPrimitive?.longOrNull ?: 0)
    } catch (_: Exception) { State(UUID.randomUUID().toString(), true, 0) }

    @Synchronized private fun save(next: State) {
        state = next
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(buildJsonObject {
                put("install_id", next.installId); put("enabled", next.enabled); put("last_sent", next.lastSent)
            }.toString())
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        } catch (_: Exception) {}
    }

    override fun setEnabled(value: Boolean) {
        save(state.copy(enabled = value))
        enabledFlow.value = value
    }

    /** Returns true when an event was delivered. Never throws: telemetry must not affect the app. */
    suspend fun sendLaunchIfDue(): Boolean = withContext(Dispatchers.IO) {
        val current = state
        if (!current.enabled || clock() - current.lastSent < MIN_INTERVAL_MS) return@withContext false
        try {
            val body = telemetryEvent(current.installId, device()).toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(endpoint).post(body).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
            }
            save(state.copy(lastSent = clock()))
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    private companion object { const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L }
}

private object TelemetryHolder {
    private var instance: FileTelemetry? = null
    @Synchronized fun get(dir: File) = instance ?: FileTelemetry(File(dir, "telemetry.json"), ::currentDeviceInfo).also { instance = it }
}

@Composable actual fun rememberTelemetry(): TelemetryController {
    val telemetry = TelemetryHolder.get(telemetryDirectory())
    LaunchedEffect(telemetry) { telemetry.sendLaunchIfDue() }
    return telemetry
}
