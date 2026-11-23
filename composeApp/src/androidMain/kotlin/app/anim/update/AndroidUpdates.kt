package app.anim.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

actual fun updatePlatform(): String = "android"

internal class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {
    override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): String {
        val apk = downloadVerifiedRelease(release, File(File(context.cacheDir, "updates"), "SpaceAnime-${release.version}.apk"), onProgress)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Download verificado. Permita \"instalar apps desconhecidos\" para o SpaceAnime e toque em instalar novamente."
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Download verificado. Confirme a instalação para continuar."
    }
}

@Composable actual fun rememberUpdateInstaller(): UpdateInstaller {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidUpdateInstaller(context) }
}
