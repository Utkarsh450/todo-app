package com.utkarsh.todo.feature.reminders.data

import com.utkarsh.todo.core.common.resultOf
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderGroups
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
        api.create(CreateReminderRequest(reminder.title, reminder.remindAt, reminder.taskId, reminder.repeatRule, reminder.locationTrigger)).toDomain()
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

    override suspend fun syncAlarms() = resultOf {
        api.list().map { it.toDomain() }.forEach(::apply)
    }

    private fun Result<Reminder>.synced() = onSuccess(::apply)

    private fun apply(reminder: Reminder) {
        if (reminder.enabled && reminder.effectiveAt.isAfter(clock.instant())) scheduler.schedule(reminder) else scheduler.cancel(reminder.id)
    }
}

internal fun ReminderDto.toDomain() = Reminder(
    id = id, taskId = taskId, title = title, remindAt = remindAt, repeatRule = repeatRule,
    locationTrigger = locationTrigger, enabled = enabled, snoozedUntil = snoozedUntil,
)
