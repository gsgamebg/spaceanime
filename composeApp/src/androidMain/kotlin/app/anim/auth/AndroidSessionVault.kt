package app.anim.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable key kept in the Android Keystore (hardware-backed when available).
 * The key never leaves the device, so a copied file cannot be decrypted elsewhere.
 */
internal class AndroidKeystoreCipher(private val alias: String = "spaceanime_session_v1") : SecretCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        cipher.updateAAD(AAD)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(2 + iv.size + body.size).put(VERSION).put(iv.size.toByte()).put(iv).put(body).array()
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(sealed)
        require(buffer.get() == VERSION)
        val ivSize = buffer.get().toInt()
        require(ivSize in 12..16)
        val iv = ByteArray(ivSize).also(buffer::get)
        val body = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        cipher.updateAAD(AAD)
        return cipher.doFinal(body)
    }

    private companion object {
        const val VERSION: Byte = 1
        val AAD = "spaceanime.session".toByteArray()
    }
}

private object AndroidSessionVaultHolder {
    private var instance: SessionVault? = null
    @Synchronized fun get(context: Context): SessionVault =
        instance ?: EncryptedFileSessionVault(File(context.noBackupFilesDir, "account-session.bin"), AndroidKeystoreCipher()).also { instance = it }
}

@Composable internal actual fun rememberSessionVault(): SessionVault? {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidSessionVaultHolder.get(context) }
}
