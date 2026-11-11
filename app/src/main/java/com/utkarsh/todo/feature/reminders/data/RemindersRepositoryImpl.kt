package com.utkarsh.todo.feature.reminders.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.reminders.domain.AlarmPlan
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
import com.utkarsh.todo.feature.reminders.domain.ReminderAlarmStore
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Talks to the API and keeps the device alarms in step with every change. */
class RemindersRepositoryImpl @Inject constructor(
    private val api: RemindersApi,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
    private val store: ReminderAlarmStore,
) : RemindersRepository {

    override suspend fun grouped(now: Instant, endOfToday: Instant, endOfWeek: Instant) = resultOf {
        val dto = api.grouped(now.toString(), endOfToday.toString(), endOfWeek.toString())
        ReminderGroups(
            overdue = dto.overdue.map { it.toDomain() },
            today = dto.today.map { it.toDomain() },
            thisWeek = dto.thisWeek.map { it.toDomain() },
            later = dto.later.map { it.toDomain() },
        )
    }

    override suspend fun create(reminder: NewReminder) = resultOf {
        api.create(CreateReminderRequest(reminder.title, reminder.remindAt, reminder.taskId, reminder.repeatRule, reminder.locationTrigger, reminder.eventId, reminder.style.name)).toDomain()
    }.synced()

    override suspend fun setEnabled(id: String, enabled: Boolean) = resultOf {
        api.update(id, buildJsonObject { put("enabled", JsonPrimitive(enabled)) }).toDomain()
    }.synced()

    override suspend fun snooze(id: String, minutes: Int) = resultOf { api.snoozeMinutes(id, SnoozeMinutesRequest(minutes)).toDomain() }.synced()

    override suspend fun snoozeUntil(id: String, until: Instant) = resultOf { api.snoozeUntil(id, SnoozeUntilRequest(until)).toDomain() }.synced()

    override suspend fun reschedule(id: String, remindAt: Instant) = resultOf {
        api.update(id, buildJsonObject {
            put("remindAt", JsonPrimitive(remindAt.toString()))
            put("snoozedUntil", JsonNull)
        }).toDomain()
    }.synced()

    override suspend fun delete(id: String) = resultOf { api.delete(id) }.also { result -> result.onSuccess { scheduler.cancel(id) } }

    override suspend fun forTask(taskId: String) = resultOf { api.list().map { it.toDomain() }.firstOrNull { it.taskId == taskId } }

    override fun forgetAlarm(id: String) = scheduler.cancel(id)

    override suspend fun restoreAlarms() = resultOf { store.all().forEach(::apply) }

    override suspend fun syncAlarms() = resultOf {
        api.list().map { it.toDomain() }.forEach { reminder ->
            val planned = apply(reminder)
            // A repeating reminder rolled forward because it came due while the phone was off: tell the server too.
            if (planned != null && planned.remindAt != reminder.remindAt) {
                runCatching {
                    api.update(reminder.id, buildJsonObject {
                        put("remindAt", JsonPrimitive(planned.remindAt.toString()))
                        put("snoozedUntil", JsonNull)
                    })
                }
            }
        }
    }

    private fun Result<Reminder>.synced() = onSuccess(::apply)

    /** Registers (or cancels) the alarm for [reminder] and returns what was registered, null if none. */
    private fun apply(reminder: Reminder): Reminder? {
        val planned = AlarmPlan.plan(reminder, clock.instant(), clock.zone)
        if (planned != null) scheduler.schedule(planned) else scheduler.cancel(reminder.id)
        return planned
    }
}

internal fun ReminderDto.toDomain() = Reminder(
    id = id, taskId = taskId, title = title, remindAt = remindAt, repeatRule = repeatRule,
    locationTrigger = locationTrigger, enabled = enabled, snoozedUntil = snoozedUntil, eventId = eventId,
    style = if (style == "NOTIFICATION") AlertStyle.NOTIFICATION else AlertStyle.ALARM,
)
