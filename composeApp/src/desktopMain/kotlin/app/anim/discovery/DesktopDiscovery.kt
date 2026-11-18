package app.anim.discovery
import androidx.compose.runtime.*
import app.anim.sources.SourceRepository
import app.anim.profile.desktopDataRoot
import java.io.File
@Composable actual fun rememberDiscovery(repository: SourceRepository): DiscoveryStore = remember(repository) { FileDiscovery(File(desktopDataRoot(), "discovery.json"), repository) }
