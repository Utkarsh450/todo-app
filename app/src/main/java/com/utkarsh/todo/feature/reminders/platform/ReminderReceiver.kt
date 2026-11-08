package com.utkarsh.todo.feature.reminders.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.reminders.domain.RepeatRule
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires when a reminder alarm goes off: shows the notification and rolls repeating reminders forward. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var repository: RemindersRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Reminder" }
        notifications.show(id, title)

        val rule = intent.getStringExtra(EXTRA_REPEAT) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = ZonedDateTime.now(clock)
                RepeatRule.next(rule, now, now)?.let { repository.reschedule(id, it.toInstant()) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.utkarsh.todo.action.FIRE_REMINDER"
        const val EXTRA_ID = "reminderId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REPEAT = "repeatRule"
    }
}
