package app.anim.update

const val APP_VERSION = "0.7.24"
const val APP_VERSION_CODE = 40
/** Public GitHub repository whose Releases drive the mandatory update. */
const val GITHUB_REPOSITORY = "gsgamebg/spaceanime"
// Empty means no network checks.
const val UPDATE_MANIFEST_URL = "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/latest"
/** Only release assets of this repository are accepted as downloads. */
const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/$GITHUB_REPOSITORY/releases/download/"
data class AppRelease(val version: String, val code: Int, val url: String, val sha256: String, val notes: String)
interface UpdateClient { suspend fun check(): AppRelease? }
expect fun createUpdateClient(): UpdateClient
expect fun updatePlatform(): String

/** Downloads the release, verifies its SHA-256 and hands it to the platform installer. */
interface UpdateInstaller {
    /** [onProgress] receives 0..1, or a negative value when the size is unknown. Returns a user-facing status. */
    suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): String
}
@androidx.compose.runtime.Composable expect fun rememberUpdateInstaller(): UpdateInstaller

fun retainHighestRelease(current: AppRelease?, checked: AppRelease?): AppRelease? = when {
    checked == null -> current
    current == null || checked.code >= current.code -> checked
    else -> current
}

fun requiredReleaseForStartup(installedCode: Int, stored: AppRelease?): AppRelease? =
    stored?.takeIf { it.code > installedCode }

fun updateRequirementAfterCheck(
    installedCode: Int,
    stored: AppRelease?,
    checked: AppRelease?,
): AppRelease? =
    retainHighestRelease(
        requiredReleaseForStartup(installedCode, stored),
        checked?.takeIf { it.code > installedCode },
    )








