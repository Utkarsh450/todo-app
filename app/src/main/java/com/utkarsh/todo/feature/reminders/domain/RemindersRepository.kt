package com.utkarsh.todo.feature.reminders.domain

import java.time.Instant

interface RemindersRepository {
    /** Reminders bucketed for the client's day boundaries. */
    suspend fun grouped(now: Instant, endOfToday: Instant, endOfWeek: Instant): Result<ReminderGroups>
    suspend fun create(reminder: NewReminder): Result<Reminder>
    suspend fun setEnabled(id: String, enabled: Boolean): Result<Reminder>
    suspend fun snooze(id: String, minutes: Int): Result<Reminder>
    suspend fun snoozeUntil(id: String, until: Instant): Result<Reminder>

    /** Moves a (repeating) reminder to [remindAt] and clears any snooze. */
    suspend fun reschedule(id: String, remindAt: Instant): Result<Reminder>
    suspend fun delete(id: String): Result<Unit>

    /** Re-registers device alarms for every enabled, upcoming reminder (app start / after reboot). */
    suspend fun syncAlarms(): Result<Unit>
}

/** Registers and cancels the OS alarms that fire reminders. */
interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(id: String)
}
