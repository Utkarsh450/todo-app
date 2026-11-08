package com.utkarsh.todo.feature.reminders.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.reminders.domain.RepeatRule
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.Instant
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
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Reminder" }
        notifications.show(id, title)

        val rule = intent.getStringExtra(EXTRA_REPEAT) ?: return
        // Count the next occurrence from the time the reminder was meant to fire, not from when the alarm actually went
        // off: alarms fire a little late, and anchoring on "now" would slide the reminder later every single cycle.
        val scheduledAt = intent.getLongExtra(EXTRA_REMIND_AT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: clock.instant()
        val now = ZonedDateTime.now(clock)
        val next = RepeatRule.next(rule, scheduledAt.atZone(clock.zone), now) ?: return
        // Register the next alarm on the device straight away, so it exists even when the backend can't be reached.
        scheduler.schedule(Reminder(id, null, title, next.toInstant(), rule, null, true, null))

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.reschedule(id, next.toInstant()) // best effort: the server is brought up to date on the next sync
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
        const val EXTRA_REMIND_AT = "remindAtMillis"
    }
}
