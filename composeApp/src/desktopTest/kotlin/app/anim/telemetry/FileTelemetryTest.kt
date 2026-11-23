package app.anim.telemetry

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.nio.file.Files
import kotlin.test.*

class FileTelemetryTest {
    private var now = 1_000_000_000_000L
    private val requests = mutableListOf<Request>()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(202).message("ok")
            .body("{}".toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val file = Files.createTempDirectory("telemetry").toFile().resolve("telemetry.json")
    private fun telemetry() = FileTelemetry(file, { DeviceInfo("Android 14 (API 34)", "samsung SM-A546E") }, "https://backend.example/api/v1/telemetry", { now }, client)

    @Test fun sendsAllowListedEventOncePerSixHoursWithStableInstallId() = runBlocking {
        val first = telemetry()
        assertTrue(first.sendLaunchIfDue())
        assertFalse(telemetry().sendLaunchIfDue())
        now += 6 * 60 * 60 * 1000L
        assertTrue(telemetry().sendLaunchIfDue())
        val bodies = requests.map { Json.parseToJsonElement(Buffer().also { b -> it.body!!.writeTo(b) }.readUtf8()).jsonObject }
        assertEquals(setOf("schemaVersion", "install_id", "platform", "app_version", "app_code", "os_version", "device_model", "locale", "time_zone"), bodies[0].keys)
        assertEquals(bodies[0]["install_id"], bodies[1]["install_id"])
    }

    @Test fun optOutStopsSendingAndPersists() = runBlocking {
        telemetry().setEnabled(false)
        val reopened = telemetry()
        assertFalse(reopened.enabled.value)
        assertFalse(reopened.sendLaunchIfDue())
        assertTrue(requests.isEmpty())
    }
}
