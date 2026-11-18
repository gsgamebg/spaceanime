package app.anim.update

const val APP_VERSION = "0.7.19"
const val APP_VERSION_CODE = 35
// Set the Render HTTPS address after deployment. Empty means no network checks.
const val UPDATE_MANIFEST_URL = "https://spaceanime.onrender.com/updates.json"
data class AppRelease(val version: String, val code: Int, val url: String, val sha256: String, val notes: String)
interface UpdateClient { suspend fun check(): AppRelease? }
expect fun createUpdateClient(): UpdateClient
expect fun updatePlatform(): String

fun retainHighestRelease(current: AppRelease?, checked: AppRelease?): AppRelease? = when {
    checked == null -> current
    current == null || checked.code >= current.code -> checked
    else -> current
}








