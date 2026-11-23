package app.anim.telemetry

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/** Basic usage telemetry (install ID, device, app version; the server records the IP). User can turn it off. */
interface TelemetryController {
    val enabled: StateFlow<Boolean>
    fun setEnabled(value: Boolean)
}

/** Also sends the launch event once (at most every 6h) while enabled. */
@Composable expect fun rememberTelemetry(): TelemetryController
