package com.andika.watersupply.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andika.watersupply.MainActivity
import com.andika.watersupply.R
import com.andika.watersupply.data.WaterRepository
import com.andika.watersupply.waterApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class ReminderController(private val context: Context, private val repository: WaterRepository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && !channelBlocked()

    fun channelBlocked() = Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pengingat minum", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pending(action: String, code: Int) = PendingIntent.getBroadcast(context, code,
        Intent(context, WaterReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reschedule() {
        val reminder = pending(REMIND, 1)
        val now = ZonedDateTime.now()
        val (total, target) = repository.today()
        val p = repository.preferences.first()
        if (total >= target || !p.enabled) NotificationManagerCompat.from(context).cancel(1)
        val next = if (notificationsAllowed()) ReminderSchedule.next(p, now, total >= target) else null
        if (next == null) alarms.cancel(reminder)
        else schedule(next.toInstant().toEpochMilli(), reminder, true)
        // A separate daily alarm refreshes widget progress even with reminders disabled.
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        // Widget maintenance can wait until the device is awake.
        alarms.set(AlarmManager.RTC, midnight, pending(DAY_CHANGED, 2))
    }

    private fun schedule(at: Long, pending: PendingIntent, precise: Boolean) {
        try {
            if (precise && exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    suspend fun notifyIfNeeded() {
        val p = repository.preferences.first()
        val (total, target) = repository.today()
        if (!p.enabled || total >= target || !notificationsAllowed() || !ReminderSchedule.isActive(p, ZonedDateTime.now())) return
        createChannel()
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water).setContentTitle("Saatnya minum air")
            .setContentText("Luangkan waktu untuk segelas air. Catat setelah kamu minum.")
            .setContentIntent(open).setAutoCancel(true).build()
        try { manager.notify(1, notification) } catch (_: SecurityException) { /* Permission may be revoked while delivering. */ }
    }
    companion object {
        const val CHANNEL_ID = "water"
        const val REMIND = "com.andika.watersupply.REMIND"
        const val DAY_CHANGED = "com.andika.watersupply.DAY_CHANGED"
    }
}

class WaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        context.waterApp.scope.launch {
            try {
                if (intent.action == ReminderController.REMIND) {
                    try { context.waterApp.reminders.notifyIfNeeded() }
                    finally { context.waterApp.reminders.reschedule() }
                } else context.waterApp.refresh()
            } finally { result.finish() }
        }
    }
}
