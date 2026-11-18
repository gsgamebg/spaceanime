package app.anim.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.anim.R

@Composable actual fun rememberPlaybackNotifier(): PlaybackNotifier {
    val context = LocalContext.current
    return remember(context) {
        object : PlaybackNotifier {
            override fun nearEnd(title: String) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("playback", "Reprodução", NotificationManager.IMPORTANCE_DEFAULT))
                val notification = NotificationCompat.Builder(context, "playback")
                    .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("SpaceAnime")
                    .setContentText("Falta pouco para terminar $title")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Falta pouco para terminar $title"))
                    .setAutoCancel(true).build()
                manager.notify(1001, notification)
            }
        }
    }
}
