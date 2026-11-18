package app.anim.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidRequiredReleaseStore(context: Context) : RequiredReleaseStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): AppRelease? = withContext(Dispatchers.IO) {
        val values = preferences.all
        if (values.isEmpty()) return@withContext null
        if (values.keys != STORED_KEYS) {
            throw IOException("Stored required release is incomplete.")
        }
        val release = AppRelease(
            version = preferences.getString(KEY_VERSION, null) ?: throw IOException("Stored release version is missing."),
            code = preferences.getInt(KEY_CODE, 0),
            url = preferences.getString(KEY_URL, null) ?: throw IOException("Stored release URL is missing."),
            sha256 = preferences.getString(KEY_SHA256, null) ?: throw IOException("Stored release hash is missing."),
            notes = preferences.getString(KEY_NOTES, null) ?: throw IOException("Stored release notes are missing."),
        )
        if (!release.isValidStoredRelease()) {
            throw IOException("Stored required release is invalid.")
        }
        release
    }

    override suspend fun save(release: AppRelease) = withContext(Dispatchers.IO) {
        require(release.isValidForPersistence()) { "Required release is invalid." }
        val committed = preferences.edit()
            .putString(KEY_VERSION, release.version)
            .putInt(KEY_CODE, release.code)
            .putString(KEY_URL, release.url)
            .putString(KEY_SHA256, release.sha256)
            .putString(KEY_NOTES, release.notes)
            .commit()
        if (!committed) throw IOException("Could not save required release.")
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        if (!preferences.edit().clear().commit()) {
            throw IOException("Could not clear required release storage.")
        }
    }
}

@Composable
actual fun rememberRequiredReleaseStore(): RequiredReleaseStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRequiredReleaseStore(context) }
}

private const val PREFERENCES_NAME = "spaceanime_required_release"
private const val KEY_VERSION = "version"
private const val KEY_CODE = "code"
private const val KEY_URL = "url"
private const val KEY_SHA256 = "sha256"
private const val KEY_NOTES = "notes"
private val STORED_KEYS = setOf(KEY_VERSION, KEY_CODE, KEY_URL, KEY_SHA256, KEY_NOTES)
