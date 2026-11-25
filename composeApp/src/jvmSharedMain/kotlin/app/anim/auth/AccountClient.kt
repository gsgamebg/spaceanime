package app.anim.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import app.anim.profile.LocalProfile
import app.anim.notifications.NotificationPreferences
import app.anim.notifications.NotificationSubscription

internal data class AccountSession(
    val accessToken: String,
    val email: String,
    val expiresAt: Long,
    val userId: String,
    val refreshToken: String = "",
) {
    override fun toString() = "AccountSession(email=$email, userId=$userId, expiresAt=$expiresAt, tokens=<redacted>)"
}
internal data class AccountResult(val session: AccountSession?, val message: String)
internal sealed interface RefreshOutcome {
    data class Renewed(val session: AccountSession) : RefreshOutcome
    /** The server refused the refresh token (revoked, reused or expired): the user must sign in again. */
    data object Rejected : RefreshOutcome
    /** Network or server problem: keep the stored token and retry later. */
    data object Unavailable : RefreshOutcome
}
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
/** Parses `{active, plan, expires_at}` from the backend; anything unexpected means "not VIP". */
internal fun parseVipStatus(value: JsonObject): app.anim.premium.VipStatus {
    val active = value["active"]?.jsonPrimitive?.booleanOrNull == true
    val plan = value["plan"]?.jsonPrimitive?.contentOrNull?.takeIf { it in setOf("ouro", "platina", "ametista") }
    val expires = value["expires_at"]?.jsonPrimitive?.contentOrNull?.let { raw ->
        try { java.time.OffsetDateTime.parse(raw) } catch (_: Exception) {
            try { java.time.OffsetDateTime.parse(raw.replace(' ', 'T').let { if (Regex("[+-]\\d{2}$").containsMatchIn(it)) "$it:00" else it }) } catch (_: Exception) { null }
        }
    }
    if (!active || plan == null || expires == null) return app.anim.premium.VipStatus()
    val label = expires.atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    return app.anim.premium.VipStatus(true, plan, label)
}
internal class AccountClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val backendUrl: String = BACKEND_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun sessionFrom(data: JsonObject, fallbackEmail: String?): AccountSession? {
        val token = data["access_token"]?.jsonPrimitive?.contentOrNull
        if (token.isNullOrBlank()) return null
        val expiresIn = data["expires_in"]?.jsonPrimitive?.longOrNull?.coerceIn(1, 86400) ?: 3600
        val user = data["user"] as? JsonObject
        val userId = user?.get("id")?.jsonPrimitive?.contentOrNull
        if (userId == null || !Regex("[0-9a-fA-F-]{36}").matches(userId)) return null
        val email = user["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackEmail ?: return null
        val refresh = data["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return AccountSession(token, email, clock() + expiresIn * 1000, userId, refresh)
    }

    suspend fun refresh(session: AccountSession): RefreshOutcome = withContext(Dispatchers.IO) {
        if (session.refreshToken.isBlank()) return@withContext RefreshOutcome.Rejected
        val body = buildJsonObject { put("refresh_token", session.refreshToken) }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code in setOf(400, 401, 403) -> RefreshOutcome.Rejected
                    !response.isSuccessful -> RefreshOutcome.Unavailable
                    else -> sessionFrom(readJson(response), session.email)
                        ?.takeIf { it.userId == session.userId }
                        ?.let { RefreshOutcome.Renewed(it) } ?: RefreshOutcome.Unavailable
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { RefreshOutcome.Unavailable }
    }

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
            val session = sessionFrom(data, email) ?: return@withContext AccountResult(null, "Não foi possível validar a conta.")
            AccountResult(session, "Conta conectada.")
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
    suspend fun createPix(session: AccountSession, plan: String): app.anim.premium.PixCharge = withContext(Dispatchers.IO) {
        require(plan in setOf("ouro", "platina", "ametista"))
        val payload = buildJsonObject { put("plan", plan) }.toString().toRequestBody("application/json".toMediaType())
        val request = notificationRequest(session, "/api/vip/create-pix").post(payload).build()
        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw app.anim.premium.VipException(when (response.code) {
                    401 -> "Sua sessão expirou. Entre novamente."
                    429 -> "Muitas tentativas de pagamento. Aguarde alguns minutos."
                    503 -> "Os pagamentos estão indisponíveis no momento. Tente mais tarde."
                    else -> "Não foi possível gerar o Pix. Tente novamente."
                })
                readJson(response)
            }
        } catch (e: java.io.IOException) { throw app.anim.premium.VipException("Sem conexão. Verifique a internet e tente novamente.") }
        fun text(key: String) = body[key]?.jsonPrimitive?.contentOrNull
        val orderId = text("order_id")?.takeIf { Regex("[0-9a-fA-F-]{36}").matches(it) }
        val qrCode = text("qr_code")?.takeIf { it.length in 1..1024 }
        val image = text("qr_code_base64")?.takeIf { it.length in 1..200_000 }
        val amount = body["amount_cents"]?.jsonPrimitive?.intOrNull
        if (orderId == null || qrCode == null || image == null || amount == null) throw app.anim.premium.VipException("Resposta inválida do servidor de pagamentos.")
        val ticket = text("ticket_url")?.takeIf { url ->
            url.toHttpUrlOrNull()?.let { it.isHttps && (it.host == "mercadopago.com.br" || it.host.endsWith(".mercadopago.com.br") || it.host == "mercadopago.com" || it.host.endsWith(".mercadopago.com")) } == true
        }
        val png = try { java.util.Base64.getMimeDecoder().decode(image) } catch (_: IllegalArgumentException) { throw app.anim.premium.VipException("Resposta inválida do servidor de pagamentos.") }
        app.anim.premium.PixCharge(orderId, plan, amount, qrCode, png, ticket)
    }
    suspend fun vipOrder(session: AccountSession, orderId: String): Pair<app.anim.premium.PixOrderState, app.anim.premium.VipStatus?> = withContext(Dispatchers.IO) {
        require(Regex("[0-9a-fA-F-]{36}").matches(orderId))
        val request = notificationRequest(session, "/api/vip/orders/$orderId").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext app.anim.premium.PixOrderState.Pending to null
            val body = readJson(response)
            val state = when (body["status"]?.jsonPrimitive?.contentOrNull) {
                "paid" -> app.anim.premium.PixOrderState.Paid
                "expired" -> app.anim.premium.PixOrderState.Expired
                "failed" -> app.anim.premium.PixOrderState.Failed
                else -> app.anim.premium.PixOrderState.Pending
            }
            state to (body["vip"] as? JsonObject)?.let(::parseVipStatus)
        }
    }
    suspend fun vipStatus(session: AccountSession): app.anim.premium.VipStatus = withContext(Dispatchers.IO) {
        val request = notificationRequest(session, "/api/vip/me").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("vip status unavailable")
            parseVipStatus(readJson(response))
        }
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
