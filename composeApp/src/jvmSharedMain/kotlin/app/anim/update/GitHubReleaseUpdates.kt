package app.anim.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

actual fun createUpdateClient(): UpdateClient = GitHubReleaseUpdates(UPDATE_MANIFEST_URL, updatePlatform())

/**
 * Reads the latest published (non-draft, non-prerelease) GitHub Release.
 * Asset naming contract: `SpaceAnime-<version>-<versionCode>-android.apk` and `...-windows.zip`.
 * The SHA-256 comes from the `digest` GitHub computes for every uploaded asset.
 */
internal class GitHubReleaseUpdates(
    private val endpoint: String,
    private val platform: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build(),
) : UpdateClient {
    override suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val address = endpoint.toHttpUrlOrNull()
        require(address != null && address.isHttps && address.host == "api.github.com") { "Canal de atualização inválido." }
        val request = Request.Builder().url(address)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SpaceAnime-Updater")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                // 404 = repository has no published release yet: nothing to require.
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty")
                val bytes = body.byteStream().use { stream ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer); if (count < 0) break
                        if (out.size() + count > 1_048_576) throw IOException("too large")
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                parseGitHubRelease(bytes.toString(Charsets.UTF_8), platform)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { throw IOException("Não foi possível consultar as atualizações.") }
    }
}

internal fun parseGitHubRelease(text: String, platform: String, installedCode: Int = APP_VERSION_CODE): AppRelease? {
    val root = Json.parseToJsonElement(text).jsonObject
    if (root["draft"]?.jsonPrimitive?.booleanOrNull == true || root["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
    val tag = root.getValue("tag_name").jsonPrimitive.content
    val version = tag.removePrefix("v")
    require(Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}").matches(version)) { "Tag inválida." }
    val suffix = if (platform == "android") "android.apk" else "windows.zip"
    val namePattern = Regex("SpaceAnime-${Regex.escape(version)}-([0-9]{1,9})-${Regex.escape(suffix)}")
    val asset = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        .firstOrNull { namePattern.matches(it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()) } ?: return null
    val name = asset.getValue("name").jsonPrimitive.content
    val code = namePattern.matchEntire(name)!!.groupValues[1].toInt()
    require(code > 0)
    if (code <= installedCode) return null
    val url = asset.getValue("browser_download_url").jsonPrimitive.content
    require(url == "$RELEASE_DOWNLOAD_PREFIX$tag/$name") { "Download fora do repositório oficial." }
    val digest = asset["digest"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val sha256 = digest.removePrefix("sha256:").lowercase()
    require(digest.startsWith("sha256:") && Regex("[a-f0-9]{64}").matches(sha256)) { "Release sem SHA-256." }
    val notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4000)
    return AppRelease(version, code, url, sha256, notes)
}

/** Hosts GitHub uses to serve release assets (the download URL redirects to them). */
private val releaseHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
internal fun allowedReleaseHost(raw: String): Boolean =
    raw.toHttpUrlOrNull()?.let { it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() && it.host in releaseHosts } == true

/** Streams the asset to [target] and keeps it only when the SHA-256 matches. */
internal suspend fun downloadVerifiedRelease(
    release: AppRelease,
    target: File,
    onProgress: (Float) -> Unit,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            if (!allowedReleaseHost(chain.request().url.toString())) throw IOException("Destino de download não autorizado.")
            chain.proceed(chain.request())
        }.build(),
): File = withContext(Dispatchers.IO) {
    require(release.url.startsWith(RELEASE_DOWNLOAD_PREFIX) && Regex("[a-f0-9]{64}").matches(release.sha256))
    target.parentFile?.mkdirs()
    val partial = File(target.parentFile, target.name + ".part")
    val digest = MessageDigest.getInstance("SHA-256")
    try {
        client.newCall(Request.Builder().url(release.url).header("User-Agent", "SpaceAnime-Updater").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty")
            val total = body.contentLength()
            if (total > MAX_RELEASE_BYTES) throw IOException("Arquivo muito grande.")
            var read = 0L
            body.byteStream().use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    read += count
                    if (read > MAX_RELEASE_BYTES) throw IOException("Arquivo muito grande.")
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                    onProgress(if (total > 0) read.toFloat() / total else -1f)
                }
            } }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != release.sha256) throw IOException("O arquivo baixado não confere com o SHA-256 publicado.")
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("Não foi possível salvar a atualização.")
        target
    } finally { partial.delete() }
}

private const val MAX_RELEASE_BYTES = 600L * 1024 * 1024
