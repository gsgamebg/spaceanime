package app.anim.notifications

import androidx.compose.runtime.*
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO

@Composable actual fun rememberPlaybackNotifier(): PlaybackNotifier {
    val trayIcon = remember {
        try {
            if (!SystemTray.isSupported()) null else {
                val image = ImageIO.read(object {}.javaClass.getResourceAsStream("/spaceanime.png"))
                TrayIcon(image, "SpaceAnime").apply { isImageAutoSize = true; SystemTray.getSystemTray().add(this) }
            }
        } catch (_: Exception) { null }
    }
    DisposableEffect(trayIcon) { onDispose { if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon) } }
    return remember(trayIcon) { object : PlaybackNotifier {
        override fun nearEnd(title: String) { trayIcon?.displayMessage("SpaceAnime", "Falta pouco para terminar $title", TrayIcon.MessageType.INFO) }
    } }
}
