package app.anim.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.anim.MainActivity

private const val DELAY = 10 * 60 * 60 * 1000L
private const val PREF = "inactivity_reminder"
private fun reminderIntent(context: Context) = PendingIntent.getBroadcast(context, 21, Intent(context, InactivityReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
fun cancelInactivityReminder(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("left_at").apply()
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderIntent(context))
}
fun scheduleInactivityReminder(context: Context, leftAt: Long = System.currentTimeMillis()) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("left_at", leftAt).apply()
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, (leftAt + DELAY).coerceAtLeast(System.currentTimeMillis() + 1000), reminderIntent(context))
}
class InactivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val leftAt = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("left_at", 0L)
        if (leftAt == 0L) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { scheduleInactivityReminder(context, leftAt); return }
        if (System.currentTimeMillis() < leftAt + DELAY) { scheduleInactivityReminder(context, leftAt); return }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("left_at").apply()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("return_reminders", "Lembretes do SpaceAnime", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Um lembrete após 10 horas fora do aplicativo" })
        val open = PendingIntent.getActivity(context, 22, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1002, NotificationCompat.Builder(context, "return_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Seu universo anime está esperando ✨")
            .setContentText("Que tal continuar sua próxima aventura no SpaceAnime?")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
