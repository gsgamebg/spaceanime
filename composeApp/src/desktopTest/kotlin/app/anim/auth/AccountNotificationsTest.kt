package app.anim.auth

import app.anim.notifications.NotificationPreferences
import app.anim.notifications.NotificationSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountNotificationsTest {
    private val session = AccountSession("user-access-token", "user@example.com", 0, "11111111-1111-4111-8111-111111111111")

    @Test
    fun notificationRegistrationUsesTheSessionAndSendsOnlyConsentAndDeviceData() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val client = accountClient(requests)
        val preferences = NotificationPreferences(anime = true, digestTime = "20:00", timeZone = "America/Sao_Paulo")

        client.registerNotificationDevice(session, "device-1", "fcm-token", preferences)

        val request = requests.single()
        assertEquals("/api/v1/notifications/devices", request.url.encodedPath)
        assertEquals("Bearer user-access-token", request.header("Authorization"))
        val payload = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("device-1", payload.getValue("device_id").toString().trim('"'))
        assertEquals("fcm-token", payload.getValue("push_token").toString().trim('"'))
        assertFalse("user_id" in payload)
        assertTrue("preferences" in payload)
        assertEquals("20:00", payload.getValue("digest_time").toString().trim('"'))
    }

    @Test
    fun subscriptionSyncSendsOnlyTheProjectedIdentifiers() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val client = accountClient(requests)
        val subscriptions = listOf(NotificationSubscription("manga", "dex", "manga-id", "none"))

        client.syncNotificationSubscriptions(session, subscriptions)

        val request = requests.single()
        assertEquals("/api/v1/notifications/subscriptions", request.url.encodedPath)
        assertEquals("Bearer user-access-token", request.header("Authorization"))
        val payload = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(setOf("subscriptions"), payload.keys)
        val subscription = payload.getValue("subscriptions").jsonArray.single().jsonObject
        assertEquals(setOf("media_type", "source_id", "content_id", "audio_variant"), subscription.keys)
        assertEquals("manga-id", subscription.getValue("content_id").toString().trim('"'))
    }

    private fun accountClient(requests: MutableList<okhttp3.Request>) =
        AccountClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            "https://backend.example",
        )

    private fun okhttp3.Request.bodyText(): String {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8()
    }
}
