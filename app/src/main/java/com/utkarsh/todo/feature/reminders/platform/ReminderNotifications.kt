package com.utkarsh.todo.feature.reminders.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.utkarsh.todo.R
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An alert that is due. As a NOTIFICATION it is an ordinary heads-up notification with Snooze and Done. As an ALARM it
 * also plays the alarm sound until it is dealt with and opens [ReminderAlarmActivity] over the lock screen (a
 * full-screen intent).
 */
@Singleton
class ReminderNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) // the old, silent one
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminders_channel_description)
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = VIBRATION
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.notifications_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notifications_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** Shows the reminder. Silently does nothing if notification permission is missing. */
    fun show(reminderId: String, title: String, dueAtMillis: Long = System.currentTimeMillis(), taskId: String? = null, repeatRule: String? = null, style: AlertStyle = AlertStyle.ALARM) {
        if (!canNotify()) return
        ensureChannel()
        val code = reminderId.hashCode()
        val alert = PendingIntent.getActivity(
            context, code, ReminderAlarmActivity.intent(context, reminderId, title, dueAtMillis, repeatRule, taskId, style),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarm = style == AlertStyle.ALARM
        val builder = NotificationCompat.Builder(context, if (alarm) CHANNEL_ID else NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_due_now))
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (alarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(alert)
            .addAction(0, context.getString(R.string.reminder_snooze_action), action(ReminderActionReceiver.ACTION_SNOOZE, reminderId, title, dueAtMillis, repeatRule, taskId, style))
            .addAction(0, context.getString(R.string.reminder_dismiss_action), action(ReminderActionReceiver.ACTION_DONE, reminderId, title, dueAtMillis, repeatRule, taskId, style))
        if (alarm) builder.setTimeoutAfter(RING_TIMEOUT_MS).setFullScreenIntent(alert, true)
        val notification = builder.build().apply { if (alarm) flags = flags or Notification.FLAG_INSISTENT } // an alarm keeps ringing until acted on
        NotificationManagerCompat.from(context).notify(code, notification)
    }

    /** Stops the ring and removes the notification. */
    fun cancel(reminderId: String) = NotificationManagerCompat.from(context).cancel(reminderId.hashCode())

    private fun action(name: String, id: String, title: String, dueAtMillis: Long, repeatRule: String?, taskId: String?, style: AlertStyle): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(name)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderReceiver.EXTRA_REMIND_AT, dueAtMillis)
            .putExtra(ReminderReceiver.EXTRA_REPEAT, repeatRule)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderReceiver.EXTRA_STYLE, style.name)
            .putExtra(ReminderActionReceiver.EXTRA_MINUTES, ReminderActionReceiver.DEFAULT_SNOOZE_MINUTES)
        return PendingIntent.getBroadcast(context, id.hashCode() xor name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Android 14+ lets people switch full-screen alerts off for an app; then reminders only pop up as a notification. */
    fun canShowFullScreen(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun canNotify() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    companion object {
        const val CHANNEL_ID = "reminder_alerts"
        const val NOTIFICATION_CHANNEL_ID = "alert_notifications"
        private const val LEGACY_CHANNEL_ID = "reminders"
        private const val RING_TIMEOUT_MS = 5 * 60_000L
        private val VIBRATION = longArrayOf(0, 600, 300, 600, 300, 900)
    }
}
