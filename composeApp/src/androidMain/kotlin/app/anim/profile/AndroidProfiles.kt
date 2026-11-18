package app.anim.profile
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.File
private object Holder {
    private var instance: FileProfiles? = null
    @Synchronized fun get(root: File) = instance ?: FileProfiles(File(root, "profiles.json")).also { instance = it }
}
@Composable actual fun rememberProfiles(): Profiles {
    val context = LocalContext.current.applicationContext
    return remember(context) { Holder.get(context.filesDir) }
}
