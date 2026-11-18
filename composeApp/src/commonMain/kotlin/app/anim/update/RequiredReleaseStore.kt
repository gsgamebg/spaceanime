package app.anim.update

import androidx.compose.runtime.Composable

internal fun AppRelease.isValidForPersistence(): Boolean {
    val manifestHost = UPDATE_MANIFEST_URL.removePrefix("https://").substringBefore('/')
    val downloadPrefix = "https://$manifestHost/"
    return code > APP_VERSION_CODE &&
        Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version) &&
        version.length <= 32 &&
        url.length in 1..2048 &&
        url.startsWith(downloadPrefix) &&
        url.none { it.isWhitespace() || it.code < 32 } &&
        '?' !in url && '#' !in url &&
        (url.endsWith(".apk") || url.endsWith(".zip")) &&
        Regex("[a-fA-F0-9]{64}").matches(sha256) &&
        notes.length <= 4000
}

@Composable
expect fun rememberRequiredReleaseStore(): RequiredReleaseStore
interface RequiredReleaseStore {
    suspend fun load(): AppRelease?
    suspend fun save(release: AppRelease)
    suspend fun clear()
}
