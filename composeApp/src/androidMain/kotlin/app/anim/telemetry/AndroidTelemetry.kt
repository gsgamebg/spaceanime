package app.anim.telemetry

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable internal actual fun telemetryDirectory(): File = LocalContext.current.applicationContext.noBackupFilesDir

internal actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
)
