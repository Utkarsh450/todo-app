package com.utkarsh.todo.feature.reminders.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The Snooze and Done buttons on an alert's notification: both stop it; Snooze also re-arms the alarm, Done ticks off the task. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var snoozer: ReminderSnoozer
    @Inject lateinit var done: ReminderDone

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderReceiver.EXTRA_ID) ?: return
        notifications.cancel(id)
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID)
        if (intent.action == ACTION_DONE) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { done.complete(taskId) } finally { pending.finish() }
            }
            return
        }
        if (intent.action != ACTION_SNOOZE) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, DEFAULT_SNOOZE_MINUTES)
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty()
        val dueAt = Instant.ofEpochMilli(intent.getLongExtra(ReminderReceiver.EXTRA_REMIND_AT, System.currentTimeMillis()))
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                snoozer.snooze(id, title, dueAt, intent.getStringExtra(ReminderReceiver.EXTRA_REPEAT), taskId, minutes, ReminderReceiver.styleOf(intent.getStringExtra(ReminderReceiver.EXTRA_STYLE)))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.utkarsh.todo.action.SNOOZE_REMINDER"
        const val ACTION_DONE = "com.utkarsh.todo.action.DONE_REMINDER"
        const val EXTRA_MINUTES = "minutes"
        const val DEFAULT_SNOOZE_MINUTES = 10
    }
}
