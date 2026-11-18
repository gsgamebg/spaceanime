package app.anim.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SpaceFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val local = getSharedPreferences("spaceanime_notifications", Context.MODE_PRIVATE)
        val consented = local.getBoolean("anime", false) ||
            local.getBoolean("manga", false) ||
            local.getBoolean("announcements", false)
        if (consented && token.isNotBlank()) {
            local.edit().putString("fcm_token", token).apply()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = message.notification ?: return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Novidades do SpaceAnime",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Resumo diário de novos episódios e capítulos" },
            )
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notification.title?.take(100) ?: "Novidades do SpaceAnime")
            .setContentText(notification.body?.take(300).orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body?.take(300).orEmpty()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        manager.notify("spaceanime-digest", NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "spaceanime_updates"
        const val NOTIFICATION_ID = 1
    }
}
