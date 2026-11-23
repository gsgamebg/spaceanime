package app.anim.telemetry

import androidx.compose.runtime.Composable
import app.anim.profile.desktopDataRoot
import java.io.File

@Composable internal actual fun telemetryDirectory(): File = desktopDataRoot()

internal actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    osVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
    deviceModel = "PC ${System.getProperty("os.arch")} ${Runtime.getRuntime().availableProcessors()} CPU",
)
