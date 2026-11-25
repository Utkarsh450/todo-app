package com.utkarsh.todo.feature.reminders.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface RemindersRepository {
    /** Emits after every successful create, change, snooze or delete, so screens that show alerts can refresh. */
    val changes: Flow<Unit>

    /** Reminders bucketed for the client's day boundaries. */
    suspend fun grouped(now: Instant, endOfToday: Instant, endOfWeek: Instant): Result<ReminderGroups>
    suspend fun create(reminder: NewReminder): Result<Reminder>
    suspend fun setEnabled(id: String, enabled: Boolean): Result<Reminder>
    suspend fun snooze(id: String, minutes: Int): Result<Reminder>
    suspend fun snoozeUntil(id: String, until: Instant): Result<Reminder>

    /** Moves a (repeating) reminder to [remindAt] and clears any snooze. */
    suspend fun reschedule(id: String, remindAt: Instant): Result<Reminder>
    suspend fun delete(id: String): Result<Unit>

    /**
     * Puts alarms back from what this phone remembers, without the network (after a reboot, or on app start while
     * offline). Repeating reminders that came due in the meantime are rolled forward.
     */
    suspend fun restoreAlarms(): Result<Unit>

    /** The reminder attached to a task (the one made from the task page), if any. */
    suspend fun forTask(taskId: String): Result<Reminder?>

    /** Drops the device alarm for a reminder whose task was deleted (the server removes the reminder itself). */
    fun forgetAlarm(id: String)

    /** Re-registers device alarms for every enabled, upcoming reminder (app start / after reboot). */
    suspend fun syncAlarms(): Result<Unit>
}

/** Registers and cancels the OS alarms that fire reminders. */
interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(id: String)
}
