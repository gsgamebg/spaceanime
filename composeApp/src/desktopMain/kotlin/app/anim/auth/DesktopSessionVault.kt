package app.anim.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.anim.profile.desktopDataRoot
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.io.File

/**
 * Windows DPAPI: data is encrypted with a key derived from the signed-in Windows user,
 * so another user or another computer cannot read the saved session.
 */
internal class DpapiCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(plain, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "SpaceAnime", null)

    override fun decrypt(sealed: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(sealed, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)

    private companion object { val ENTROPY = "SpaceAnime.session.v1".toByteArray() }
}

private val desktopSessionVault: SessionVault? by lazy {
    // Never fall back to plaintext: without DPAPI the session stays in memory only.
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) null
    else EncryptedFileSessionVault(File(desktopDataRoot(), "account-session.bin"), DpapiCipher())
}

@Composable internal actual fun rememberSessionVault(): SessionVault? = remember { desktopSessionVault }
