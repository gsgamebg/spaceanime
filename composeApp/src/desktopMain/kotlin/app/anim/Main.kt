package app.anim

import androidx.compose.runtime.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--check-runtime"))) {
        app.anim.player.prepareDesktopRuntime()
        val factory = uk.co.caprica.vlcj.factory.MediaPlayerFactory("--quiet", "--no-metadata-network-access")
        try {
            check(factory.application().videoFilters().isNotEmpty()) { "VLC plugins unavailable" }
            println("Anim runtime OK: Java ${System.getProperty("java.version")}, VLC and plugins loaded")
        } finally { factory.release() }
        return
    }
    application {
    val windowState = rememberWindowState(width = 1120.dp, height = 760.dp)
    var fullScreen by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "SpaceAnime",
        icon = androidx.compose.ui.res.painterResource("spaceanime.png"),
        state = windowState,
        undecorated = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!fullScreen) WindowDraggableArea {
                Row(Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF0D0919)), verticalAlignment = Alignment.CenterVertically) {
                    Text("  SpaceAnime", Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                    TextButton(onClick = { windowState.isMinimized = true }, modifier = Modifier.width(48.dp)) { Text("—", color = Color.White) }
                    TextButton(onClick = { windowState.placement = if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized }, modifier = Modifier.width(48.dp)) { Text("□", color = Color.White) }
                    TextButton(onClick = ::exitApplication, modifier = Modifier.width(48.dp)) { Text("×", color = Color.White) }
                }
            }
            Box(Modifier.weight(1f)) { App(onFullScreenChange = { full -> fullScreen = full; windowState.placement = if (full) WindowPlacement.Maximized else WindowPlacement.Floating }) }
        }
    }
}
}




