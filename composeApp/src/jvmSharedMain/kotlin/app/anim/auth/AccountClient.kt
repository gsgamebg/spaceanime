package app.anim.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import app.anim.profile.LocalProfile
import app.anim.notifications.NotificationPreferences
import app.anim.notifications.NotificationSubscription

internal data class AccountSession(val accessToken: String, val email: String, val expiresAt: Long, val userId: String)
internal data class AccountResult(val session: AccountSession?, val message: String)
internal data class OnlineProfile(val name: String, val bio: String, val avatar: String = "default", val banner: String = "default")
internal fun parseOnlineProfile(text: String): OnlineProfile? {
    val rows = Json.parseToJsonElement(text).jsonArray
    require(rows.size <= 1)
    if (rows.isEmpty()) return null
    val row = rows.single().jsonObject
    val name = row.getValue("display_name").jsonPrimitive.content
    val bio = row.getValue("biography").jsonPrimitive.content
    require(name.isNotBlank() && name.length <= 24 && name.none(Char::isISOControl))
    require(bio.length <= 140 && bio.none(Char::isISOControl))
    val avatar = row["avatar_asset_id"]?.jsonPrimitive?.contentOrNull ?: "default"
    val banner = row["banner_asset_id"]?.jsonPrimitive?.contentOrNull ?: "default"
    return OnlineProfile(name, bio, avatar.takeIf { it in setOf("default", "nova", "orion") } ?: "default", banner.takeIf { it in setOf("default", "cosmos") } ?: "default")
}
internal class AccountClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val backendUrl: String = BACKEND_URL,
) {
    suspend fun authenticate(email: String, password: String, signup: Boolean): AccountResult = withContext(Dispatchers.IO) {
        require(email.length in 3..254 && email.contains('@') && password.length in 6..128)
        val body = buildJsonObject { put("email", email); put("password", password) }.toString().toRequestBody("application/json".toMediaType())
        val path = if (signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val request = Request.Builder().url(SUPABASE_URL + path).header("apikey", SUPABASE_PUBLISHABLE_KEY).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    429 -> "Muitas tentativas. Aguarde um pouco e tente novamente."
                    in 500..599 -> "O serviço de conta está indisponível. Tente novamente."
                    else -> if (signup) "Não foi possível criar a conta. Verifique os dados ou tente novamente." else "Não foi possível entrar. Confira o email, a senha e a confirmação do email."
                }
                return@withContext AccountResult(null, message)
            }
            val data = readJson(response)
            val token = data["access_token"]?.jsonPrimitive?.contentOrNull
            if (token.isNullOrBlank()) return@withContext AccountResult(null, if (signup) "Se o cadastro foi aceito, confira seu email para confirmar a conta antes de entrar." else "Não foi possível iniciar a sessão.")
            val expiresIn = data["expires_in"]?.jsonPrimitive?.longOrNull?.coerceIn(1, 86400) ?: 3600
            val userId = data["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            if (userId == null || !Regex("[0-9a-fA-F-]{36}").matches(userId)) return@withContext AccountResult(null, "Não foi possível validar a conta.")
            AccountResult(AccountSession(token, email, System.currentTimeMillis() + expiresIn * 1000, userId), "Conta conectada.")
        }
    }
    suspend fun linkInitialProfile(session: AccountSession, profile: LocalProfile): String = withContext(Dispatchers.IO) {
        val endpoint = SUPABASE_URL + "/rest/v1/profiles"
        fun request(url: String) = Request.Builder().url(url).header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer ${session.accessToken}")
        val existing = client.newCall(request(endpoint + "?user_id=eq." + session.userId + "&select=user_id").get().build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("profile read failed")
            val body = response.body ?: throw java.io.IOException("empty")
            val text = body.string()
            if (text.length > 65536) throw java.io.IOException("too large")
            Json.parseToJsonElement(text).jsonArray.isNotEmpty()
        }
        if (existing) return@withContext "Esta conta já possui um perfil online. Nenhum dado foi substituído; a sincronização ainda será concluída."
        val payload = buildJsonObject { put("user_id", session.userId); put("display_name", profile.name); put("biography", profile.bio) }.toString().toRequestBody("application/json".toMediaType())
        client.newCall(request(endpoint).post(payload).build()).execute().use { response ->
            if (response.code == 409) return@withContext "Esta conta já possui um perfil online. Nenhum dado foi substituído."
            if (!response.isSuccessful) throw java.io.IOException("profile save failed")
        }
        "Nome e biografia enviados para sua conta. Favoritos e histórico continuam apenas neste dispositivo."
    }
    suspend fun getOnlineProfile(session: AccountSession): OnlineProfile? = withContext(Dispatchers.IO) {
        val url = SUPABASE_URL + "/rest/v1/profiles?user_id=eq." + session.userId + "&select=display_name,biography,avatar_asset_id,banner_asset_id"
        val request = Request.Builder().url(url).header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${session.accessToken}").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("profile read failed")
            val body = response.body ?: throw java.io.IOException("empty")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > 65536) throw java.io.IOException("too large"); output.write(buffer, 0, n) }
                output.toByteArray()
            }
            parseOnlineProfile(bytes.toString(Charsets.UTF_8))
        }
    }
    suspend fun saveArtwork(session: AccountSession, avatar: String, banner: String) = withContext(Dispatchers.IO) {
        require(avatar in setOf("default", "nova", "orion") && banner in setOf("default", "cosmos"))
        val payload = buildJsonObject { put("avatar_asset_id", avatar); put("banner_asset_id", banner) }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(SUPABASE_URL + "/rest/v1/profiles?user_id=eq." + session.userId)
            .header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer ${session.accessToken}")
            .header("Prefer", "return=representation").patch(payload).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("artwork save failed")
            val obj = response.body ?: throw java.io.IOException("empty response")
            val body = obj.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (output.size() + n > 65536) throw java.io.IOException("response too large"); output.write(buffer, 0, n) }
                output.toByteArray().toString(Charsets.UTF_8)
            }
            val rows = Json.parseToJsonElement(body).jsonArray
            if (rows.size != 1) throw java.io.IOException("profile not updated")
        }
    }
    suspend fun saveOnlineProfile(session: AccountSession, name: String, bio: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 24 && name.none(Char::isISOControl))
        require(bio.length <= 140 && bio.none(Char::isISOControl))
        val payload = buildJsonObject { put("user_id", session.userId); put("display_name", name.trim()); put("biography", bio.trim()) }
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(SUPABASE_URL + "/rest/v1/profiles?on_conflict=user_id")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer ${session.accessToken}")
            .header("Prefer", "resolution=merge-duplicates,return=minimal").post(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("profile save failed") }
    }
    suspend fun registerNotificationDevice(
        session: AccountSession,
        deviceId: String,
        pushToken: String,
        preferences: NotificationPreferences,
    ) = withContext(Dispatchers.IO) {
        require(validNotificationDeviceId(deviceId) && pushToken.length in 1..4096 && pushToken.none(Char::isISOControl))
        require(preferences.isValid() && preferences.enabled)
        val payload = buildJsonObject {
            put("device_id", deviceId)
            put("push_token", pushToken)
            put("preferences", notificationPreferencesJson(preferences))
            put("time_zone", preferences.timeZone)
            put("digest_time", preferences.digestTime)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = notificationRequest(session, "/api/v1/notifications/devices").post(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("notification registration failed") }
    }
    suspend fun updateNotificationPreferences(
        session: AccountSession,
        deviceId: String,
        preferences: NotificationPreferences,
    ) = withContext(Dispatchers.IO) {
        require(validNotificationDeviceId(deviceId) && preferences.isValid())
        val payload = buildJsonObject {
            put("device_id", deviceId)
            put("preferences", notificationPreferencesJson(preferences))
            put("time_zone", preferences.timeZone)
            put("digest_time", preferences.digestTime)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = notificationRequest(session, "/api/v1/notifications/preferences").put(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("notification preferences failed") }
    }
    suspend fun syncNotificationSubscriptions(
        session: AccountSession,
        subscriptions: List<NotificationSubscription>,
    ) = withContext(Dispatchers.IO) {
        require(subscriptions.size <= 500)
        val payload = buildJsonObject {
            put("subscriptions", JsonArray(subscriptions.map { item ->
                buildJsonObject {
                    put("media_type", item.mediaType)
                    put("source_id", item.sourceId)
                    put("content_id", item.contentId)
                    put("audio_variant", item.audioVariant)
                }
            }))
        }.toString().toRequestBody("application/json".toMediaType())
        val request = notificationRequest(session, "/api/v1/notifications/subscriptions").put(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("notification subscriptions failed") }
    }
    suspend fun deleteNotificationDevice(session: AccountSession, deviceId: String) = withContext(Dispatchers.IO) {
        require(validNotificationDeviceId(deviceId))
        val request = notificationRequest(session, "/api/v1/notifications/devices/$deviceId").delete().build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("notification device removal failed") }
    }
    suspend fun signOut(session: AccountSession) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(SUPABASE_URL + "/auth/v1/logout?scope=local")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY).header("Authorization", "Bearer ${session.accessToken}")
            .post(ByteArray(0).toRequestBody()).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) throw java.io.IOException("logout failed") }
    }
    private fun notificationRequest(session: AccountSession, path: String): Request.Builder =
        Request.Builder().url(backendUrl + path)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
    private fun notificationPreferencesJson(preferences: NotificationPreferences) = buildJsonObject {
        put("anime", preferences.anime)
        put("manga", preferences.manga)
        put("announcements", preferences.announcements)
    }
    private fun validNotificationDeviceId(value: String) = Regex("^[A-Za-z0-9._:-]{1,128}$").matches(value)
    private fun readJson(response: Response): JsonObject {
        val body = response.body ?: throw java.io.IOException("empty")
        val bytes = body.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = input.read(buffer); if (n < 0) break; if (out.size() + n > 65536) throw java.io.IOException("too large"); out.write(buffer, 0, n) }
            out.toByteArray()
        }
        return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    }
}
