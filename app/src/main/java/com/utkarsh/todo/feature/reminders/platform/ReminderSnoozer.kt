package com.utkarsh.todo.feature.reminders.platform

import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snoozes a reminder that is ringing. The server is told through the repository (which also re-arms the alarm); if
 * it can't be reached the alarm is still re-armed from what the alarm carried, so a snooze never silently fails.
 */
@Singleton
class ReminderSnoozer @Inject constructor(
    private val repository: RemindersRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend fun snooze(id: String, title: String, dueAt: Instant, repeatRule: String?, taskId: String?, minutes: Int, style: AlertStyle = AlertStyle.ALARM) {
        if (repository.snooze(id, minutes).isSuccess) return
        val until = clock.instant().plusSeconds(minutes * 60L)
        scheduler.schedule(Reminder(id, taskId, title, dueAt, repeatRule, null, true, until, style = style))
    }
}
