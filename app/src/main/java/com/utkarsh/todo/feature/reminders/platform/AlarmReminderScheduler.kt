package com.utkarsh.todo.feature.reminders.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [ReminderScheduler] backed by [AlarmManager]. Exact alarms are used when permitted, else inexact-while-idle. */
@Singleton
class AlarmReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: Reminder) {
        val intent = checkNotNull(pendingIntent(reminder.id, reminder.title, reminder.repeatRule, reminder.remindAt.toEpochMilli(), PendingIntent.FLAG_UPDATE_CURRENT, reminder.taskId))
        val triggerAt = reminder.effectiveAt.toEpochMilli()
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    override fun cancel(id: String) {
        pendingIntent(id, title = "", repeatRule = null, remindAtMillis = 0L, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            alarms.cancel(pending)
            pending.cancel() // also drop the token so it does not linger after the alarm is gone
        }
    }

    private fun pendingIntent(id: String, title: String, repeatRule: String?, remindAtMillis: Long, flag: Int, taskId: String? = null): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderReceiver.EXTRA_REPEAT, repeatRule)
            .putExtra(ReminderReceiver.EXTRA_REMIND_AT, remindAtMillis)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        // One alarm per reminder: the request code is derived from the reminder id.
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
