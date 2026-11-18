package app.anim

import android.os.Bundle
import android.content.pm.ActivityInfo
import app.anim.notifications.cancelInactivityReminder
import app.anim.notifications.scheduleInactivityReminder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); cancelInactivityReminder(this) }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) scheduleInactivityReminder(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(onFullScreenChange = { full ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (full) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
                }
                // Keep a movie session stable while the device rotates; returning from the player restores normal rotation.
                requestedOrientation = if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            })
        }
    }
}
