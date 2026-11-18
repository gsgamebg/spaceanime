package app.anim.profile
import androidx.compose.runtime.*
import java.io.File
fun desktopDataRoot(): File = File(System.getenv("ANIM_DATA_DIR") ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Anim").path)
private val profiles by lazy { FileProfiles(File(desktopDataRoot(), "profiles.json")) }
@Composable actual fun rememberProfiles(): Profiles = remember { profiles }
