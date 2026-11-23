package app.anim.auth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class AccountSessionPersistenceTest {
    private val userId = "11111111-1111-4111-8111-111111111111"
    private val now = 1_000_000_000L

    /** Reversible scrambling so the tests can prove the vault never writes plaintext. */
    private class FakeCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = byteArrayOf(7) + plain.map { (it.toInt() xor 0x5A).toByte() }
        override fun decrypt(sealed: ByteArray): ByteArray {
            require(sealed.firstOrNull() == 7.toByte())
            return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private class MemoryVault(var stored: AccountSession? = null) : SessionVault {
        var cleared = 0
        override fun read() = stored
        override fun write(session: AccountSession) { stored = session }
        override fun clear() { stored = null; cleared++ }
    }

    private fun tempFile(): File = Files.createTempDirectory("anim-session").toFile().resolve("account-session.bin")
    private fun session(access: String = "access-1", refresh: String = "refresh-1", expiresAt: Long = now + 3_600_000) =
        AccountSession(access, "user@example.com", expiresAt, userId, refresh)

    private fun tokenJson(access: String, refresh: String) =
        """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"user":{"id":"$userId","email":"user@example.com"}}"""

    private fun client(requests: MutableList<Request>, respond: (Request) -> Pair<Int, String>) = AccountClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = respond(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build(),
        "https://backend.example",
        clock = { now },
    )

    private fun Request.bodyText(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()

    @Test fun vaultRoundTripsSessionWithoutWritingTokensInPlaintext() {
        val file = tempFile()
        val vault = EncryptedFileSessionVault(file, FakeCipher())
        vault.write(session())
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("refresh-1") || raw.contains("access-1") || raw.contains("user@example.com"))
        assertEquals(session(), vault.read())
    }

    @Test fun corruptedVaultIsDiscardedInsteadOfCrashing() {
        val file = tempFile().also { it.parentFile.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(EncryptedFileSessionVault(file, FakeCipher()).read())
        assertFalse(file.exists())
    }

    @Test fun vaultClearRemovesStoredSession() {
        val file = tempFile()
        val vault = EncryptedFileSessionVault(file, FakeCipher())
        vault.write(session())
        vault.clear()
        assertFalse(file.exists())
        assertNull(vault.read())
    }

    @Test fun sessionToStringNeverLeaksTokens() {
        val text = session().toString()
        assertFalse(text.contains("access-1") || text.contains("refresh-1"))
    }

    @Test fun loginKeepsTheRefreshToken() = runBlocking {
        val requests = mutableListOf<Request>()
        val result = client(requests) { 200 to tokenJson("access-1", "refresh-1") }.authenticate("user@example.com", "secret1", signup = false)
        assertEquals("refresh-1", result.session!!.refreshToken)
        assertEquals(now + 3_600_000, result.session!!.expiresAt)
    }

    @Test fun refreshRotatesTokens() = runBlocking {
        val requests = mutableListOf<Request>()
        val outcome = client(requests) { 200 to tokenJson("access-2", "refresh-2") }.refresh(session())
        val renewed = assertIs<RefreshOutcome.Renewed>(outcome).session
        assertEquals("access-2", renewed.accessToken)
        assertEquals("refresh-2", renewed.refreshToken)
        val request = requests.single()
        assertEquals("refresh_token", request.url.queryParameter("grant_type"))
        assertEquals("refresh-1", Json.parseToJsonElement(request.bodyText()).jsonObject.getValue("refresh_token").jsonPrimitive.content)
    }

    @Test fun refreshRejectedByServerAndOfflineAreDistinguished() = runBlocking {
        val requests = mutableListOf<Request>()
        assertEquals(RefreshOutcome.Rejected, client(requests) { 400 to """{"error":"invalid_grant"}""" }.refresh(session()))
        assertEquals(RefreshOutcome.Unavailable, client(requests) { 503 to "{}" }.refresh(session()))
        assertEquals(RefreshOutcome.Unavailable, client(requests) { throw IOException("offline") }.refresh(session()))
        assertEquals(RefreshOutcome.Rejected, client(requests) { error("no call") }.refresh(session(refresh = "")))
    }

    @Test fun restoreUsesStoredSessionWhileStillValid() = runBlocking {
        val requests = mutableListOf<Request>()
        val state = AccountState(client(requests) { error("no network expected") }, MemoryVault(session())) { now }
        assertEquals(RefreshOutcome.Renewed(session()), state.restore())
        assertEquals(session(), state.session)
        assertTrue(requests.isEmpty())
    }

    @Test fun restoreRefreshesExpiredSessionAndPersistsRotatedToken() = runBlocking {
        val vault = MemoryVault(session(expiresAt = now - 1))
        val state = AccountState(client(mutableListOf()) { 200 to tokenJson("access-2", "refresh-2") }, vault) { now }
        assertIs<RefreshOutcome.Renewed>(state.restore())
        assertEquals("access-2", state.session!!.accessToken)
        assertEquals("refresh-2", vault.stored!!.refreshToken)
    }

    @Test fun restoreClearsVaultWhenServerRejectsToken() = runBlocking {
        val vault = MemoryVault(session(expiresAt = now - 1))
        val state = AccountState(client(mutableListOf()) { 400 to "{}" }, vault) { now }
        assertEquals(RefreshOutcome.Rejected, state.restore())
        assertNull(state.session)
        assertNull(vault.stored)
    }

    @Test fun restoreKeepsVaultWhenOffline() = runBlocking {
        val vault = MemoryVault(session(expiresAt = now - 1))
        val state = AccountState(client(mutableListOf()) { throw IOException("offline") }, vault) { now }
        assertEquals(RefreshOutcome.Unavailable, state.restore())
        assertNull(state.session)
        assertEquals("refresh-1", vault.stored!!.refreshToken)
    }

    @Test fun signInPersistsAndSignOutClearsVault() = runBlocking {
        val vault = MemoryVault()
        val state = AccountState(client(mutableListOf()) { 200 to "{}" }, vault) { now }
        state.signedIn(session())
        assertEquals(session(), vault.stored)
        state.signOut()
        assertNull(state.session)
        assertNull(vault.stored)
    }

    @Test fun windowsDpapiCipherRoundTrips() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val cipher = DpapiCipher()
        val sealed = cipher.encrypt("refresh-secret".toByteArray())
        assertFalse(sealed.toString(Charsets.ISO_8859_1).contains("refresh-secret"))
        assertEquals("refresh-secret", cipher.decrypt(sealed).toString(Charsets.UTF_8))
    }
}
