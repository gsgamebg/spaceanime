package app.anim.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

actual fun updatePlatform(): String = "windows"

/** Saves the verified ZIP to Downloads and reveals it; the running app cannot replace its own files. */
internal class DesktopUpdateInstaller : UpdateInstaller {
    override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): String {
        val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: File(System.getProperty("java.io.tmpdir"))
        val zip = downloadVerifiedRelease(release, File(downloads, "SpaceAnime-${release.version}-windows.zip"), onProgress)
        runCatching { ProcessBuilder("explorer.exe", "/select,", zip.absolutePath).start() }
        return "Download verificado: ${zip.absolutePath}. Feche o app, extraia o ZIP e abra SpaceAnime.exe da nova pasta."
    }
}

@Composable actual fun rememberUpdateInstaller(): UpdateInstaller = remember { DesktopUpdateInstaller() }
