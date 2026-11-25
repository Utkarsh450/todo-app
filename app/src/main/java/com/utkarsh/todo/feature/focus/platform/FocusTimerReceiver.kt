package com.utkarsh.todo.feature.focus.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.utkarsh.todo.MainActivity
import com.utkarsh.todo.R

/** Time is up while the app is closed: one ordinary notification that opens the app. Nothing else rings. */
class FocusTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { context.getString(R.string.focus_timer_default_title) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.focus_timer_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.focus_timer_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.focus_timer_done_title))
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_DONE = "com.utkarsh.todo.FOCUS_TIMER_DONE"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 7001
    }
}
