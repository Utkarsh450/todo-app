package com.utkarsh.todo.feature.reminders.platform

import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderAlarmStore
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class StoredReminder(
    val id: String,
    val taskId: String? = null,
    val title: String,
    val remindAtMillis: Long,
    val repeatRule: String? = null,
    val snoozedUntilMillis: Long? = null,
    val style: String = "ALARM",
)

/** Keeps the scheduled reminders in one small JSON file. A missing or damaged file simply reads as empty. */
class FileReminderAlarmStore(private val file: File) : ReminderAlarmStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    override fun put(reminder: Reminder) = synchronized(lock) {
        write(read().filterNot { it.id == reminder.id } + reminder.toStored())
    }

    override fun remove(id: String) = synchronized(lock) {
        val current = read()
        if (current.any { it.id == id }) write(current.filterNot { it.id == id })
    }

    override fun all(): List<Reminder> = synchronized(lock) { read().map { it.toDomain() } }

    private fun read(): List<StoredReminder> =
        runCatching { json.decodeFromString(ListSerializer(StoredReminder.serializer()), file.readText()) }.getOrDefault(emptyList())

    /** Writes to a temporary file first so a crash half way never leaves a broken store. */
    private fun write(items: List<StoredReminder>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(ListSerializer(StoredReminder.serializer()), items))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun Reminder.toStored() = StoredReminder(id, taskId, title, remindAt.toEpochMilli(), repeatRule, snoozedUntil?.toEpochMilli(), style.name)

    private fun StoredReminder.toDomain() = Reminder(
        id = id, taskId = taskId, title = title, remindAt = Instant.ofEpochMilli(remindAtMillis), repeatRule = repeatRule,
        locationTrigger = null, enabled = true, snoozedUntil = snoozedUntilMillis?.let(Instant::ofEpochMilli),
        style = if (style == "NOTIFICATION") AlertStyle.NOTIFICATION else AlertStyle.ALARM,
    )
}
