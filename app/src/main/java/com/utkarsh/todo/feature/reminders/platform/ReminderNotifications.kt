package com.utkarsh.todo.feature.reminders.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.utkarsh.todo.MainActivity
import com.utkarsh.todo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminders_channel_description)
            },
        )
    }

    /** Shows the reminder notification. Silently does nothing if notification permission is missing. */
    fun show(reminderId: String, title: String) {
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, reminderId.hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_due_now))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
    }

    private fun canNotify() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}
