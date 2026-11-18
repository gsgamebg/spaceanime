package app.anim.discovery
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.anim.sources.SourceRepository
import java.io.File
@Composable actual fun rememberDiscovery(repository: SourceRepository): DiscoveryStore {
    val root = LocalContext.current.applicationContext.filesDir
    return remember(root, repository) { FileDiscovery(File(root, "discovery.json"), repository) }
}
