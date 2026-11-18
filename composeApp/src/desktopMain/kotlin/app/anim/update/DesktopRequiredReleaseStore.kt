package app.anim.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.anim.profile.desktopDataRoot
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal class DesktopRequiredReleaseStore(private val file: File) : RequiredReleaseStore {
    private val mutex = Mutex()

    override suspend fun load(): AppRelease? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            if (file.length() !in 1..MAX_RELEASE_STORE_BYTES.toLong()) {
                throw IOException("Stored required release is empty or too large.")
            }
            try {
                decode(file.readText(Charsets.UTF_8))
            } catch (failure: Exception) {
                throw IOException("Stored required release is invalid.", failure)
            }
        }
    }

    override suspend fun save(release: AppRelease) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(release.isValidForPersistence()) { "Required release is invalid." }
            val text = encode(release)
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_RELEASE_STORE_BYTES) { "Required release is too large to store." }
            val parent = file.absoluteFile.parentFile
                ?: throw IOException("Required release storage path is invalid.")
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Could not create required release storage directory.")
            }
            val temp = File(parent, file.name + ".tmp")
            try {
                temp.outputStream().use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
            Unit
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists() && !file.delete()) throw IOException("Could not clear required release storage.")
        }
    }

    private fun encode(release: AppRelease): String = buildJsonObject {
        put("schemaVersion", 1)
        putJsonObject("release") {
            put("version", release.version)
            put("code", release.code)
            put("url", release.url)
            put("sha256", release.sha256)
            put("notes", release.notes)
        }
    }.toString()

    private fun decode(text: String): AppRelease {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.keys == setOf("schemaVersion", "release"))
        require(root.getValue("schemaVersion").jsonPrimitive.int == 1)
        val stored = root.getValue("release").jsonObject
        require(stored.keys == setOf("version", "code", "url", "sha256", "notes"))
        val release = AppRelease(
            version = stored.getValue("version").jsonPrimitive.content,
            code = stored.getValue("code").jsonPrimitive.int,
            url = stored.getValue("url").jsonPrimitive.content,
            sha256 = stored.getValue("sha256").jsonPrimitive.content,
            notes = stored.getValue("notes").jsonPrimitive.content,
        )
        require(release.isValidForPersistence())
        return release
    }
}

@Composable
actual fun rememberRequiredReleaseStore(): RequiredReleaseStore =
    remember { DesktopRequiredReleaseStore(File(desktopDataRoot(), "required-release.json")) }

private const val MAX_RELEASE_STORE_BYTES = 8 * 1024
