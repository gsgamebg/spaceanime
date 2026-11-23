package app.anim.auth

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Platform encryption bound to this device and user (Android Keystore / Windows DPAPI). */
internal interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** Keeps the signed-in session across app restarts. Never stores the password. */
internal interface SessionVault {
    fun read(): AccountSession?
    fun write(session: AccountSession)
    fun clear()
}

/** Returns null when the platform has no secure storage; the session then lives only in memory. */
@Composable internal expect fun rememberSessionVault(): SessionVault?

internal class EncryptedFileSessionVault(private val file: File, private val cipher: SecretCipher) : SessionVault {
    private val temp get() = File(file.parentFile, file.name + ".tmp")

    @Synchronized override fun read(): AccountSession? {
        if (!file.isFile) return null
        return try {
            val sealed = file.readBytes()
            require(sealed.size <= MAX_SIZE)
            decodeStoredSession(cipher.decrypt(sealed).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            // Tampered, corrupted or encrypted with a key that no longer exists: sign in again.
            clear()
            null
        }
    }

    @Synchronized override fun write(session: AccountSession) {
        require(session.refreshToken.isNotBlank()) { "Sessão sem token de renovação." }
        val sealed = cipher.encrypt(encodeStoredSession(session).toByteArray(Charsets.UTF_8))
        file.parentFile?.mkdirs()
        temp.writeBytes(sealed)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Synchronized override fun clear() {
        temp.delete()
        if (file.exists() && !file.delete()) throw IOException("Não foi possível remover a sessão salva.")
    }

    private companion object { const val MAX_SIZE = 64 * 1024 }
}

internal fun encodeStoredSession(session: AccountSession): String = buildJsonObject {
    put("v", 1)
    put("access_token", session.accessToken)
    put("refresh_token", session.refreshToken)
    put("email", session.email)
    put("user_id", session.userId)
    put("expires_at", session.expiresAt)
}.toString()

internal fun decodeStoredSession(text: String): AccountSession {
    val root = Json.parseToJsonElement(text).jsonObject
    require(root["v"]?.jsonPrimitive?.intOrNull == 1)
    fun field(name: String, max: Int) = root.getValue(name).jsonPrimitive.content
        .also { require(it.isNotBlank() && it.length <= max && it.none(Char::isISOControl)) }
    val userId = field("user_id", 36)
    require(Regex("[0-9a-fA-F-]{36}").matches(userId))
    return AccountSession(
        accessToken = field("access_token", 8192),
        email = field("email", 254),
        expiresAt = root.getValue("expires_at").jsonPrimitive.long,
        userId = userId,
        refreshToken = field("refresh_token", 1024),
    )
}
