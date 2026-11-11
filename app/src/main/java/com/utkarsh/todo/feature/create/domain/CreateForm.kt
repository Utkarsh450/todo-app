package com.utkarsh.todo.feature.create.domain

import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The two things the + button can make. There is no separate reminder: an alert is something a task or event has. */
enum class CreateType(val label: String) { TASK("Task"), EVENT("Event") }

/** Alert offsets (minutes before the start) offered as chips; anything else comes from "Custom…". */
val AlertPresets: List<Int> = listOf(0, 10, 30, 60, 1440)

/** Times of day offered as chips; "Pick time…" covers the rest. */
val TimePresets: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0))

/** Event lengths offered as chips (minutes). */
val DurationPresets: List<Int> = listOf(30, 60, 120)

/** Repeat rules offered as chips, in the text form the reminder engine understands. */
val RepeatPresets: List<String> = listOf("Every Day", "Every Week", "Every Month")

const val DEFAULT_EVENT_ALERT_MINUTES = 30

/** "At time", "10 min before", "2 hours before", "1 day before". */
fun alertLabel(minutes: Int): String = when {
    minutes == 0 -> "At time"
    minutes % 1440 == 0 -> "${minutes / 1440} ${if (minutes == 1440) "day" else "days"} before"
    minutes % 60 == 0 -> "${minutes / 60} ${if (minutes == 60) "hour" else "hours"} before"
    else -> "$minutes min before"
}

/** `Every 2 Weeks` -> "Every 2 weeks"; plain rules are shown as they are. */
fun repeatLabel(rule: String?): String = when (rule) {
    null -> "Never"
    "Every Day" -> "Daily"
    "Every Week" -> "Weekly"
    "Every Month" -> "Monthly"
    else -> rule
}

/**
 * Everything the Create screen edits. Pure data: the view-model changes it and turns it into a task
 * (with alerts) or an event (with alerts).
 */
data class CreateForm(
    val type: CreateType = CreateType.TASK,
    val title: String = "",
    val date: LocalDate,
    /** Null = no particular time (a task is then due at 9:00 that morning). Events always have one unless [allDay]. */
    val time: LocalTime? = null,
    val durationMinutes: Int = 60,
    /** Events only. */
    val allDay: Boolean = false,
    /** Minutes before the start; each one becomes an alert. */
    val alerts: Set<Int> = emptySet(),
    /** Tasks only (events do not repeat). */
    val repeat: String? = null,
    val priority: Priority = Priority.NORMAL,
    val projectId: String? = null,
    /** How the alerts show up: an ordinary notification, or an alarm that takes over the screen. */
    val alertStyle: AlertStyle = AlertStyle.ALARM,
    /** Tasks: notes (or a link) kept with the task. */
    val notes: String = "",
    /** Events only. */
    val location: String = "",
    val videoLink: String = "",
    val guests: List<String> = emptyList(),
    val busy: Boolean = true,
) {
    /** The time everything is measured from: the chosen time, 9:00 for a task with none, 9:00 for an all-day event. */
    val effectiveTime: LocalTime get() = time ?: LocalTime.of(9, 0)

    fun problem(): String? = if (title.isBlank()) "Give it a title" else null

    fun startInstant(zone: ZoneId): Instant =
        if (type == CreateType.EVENT && allDay) date.atStartOfDay(zone).toInstant() else date.atTime(effectiveTime).atZone(zone).toInstant()

    fun endInstant(zone: ZoneId): Instant =
        if (allDay) date.plusDays(1).atStartOfDay(zone).toInstant() else startInstant(zone).plus(Duration.ofMinutes(durationMinutes.toLong()))

    /**
     * When each alert rings and is still ahead of [now]. An all-day event rings once, at 9:00 that morning,
     * however many offsets are chosen.
     */
    fun alertInstants(zone: ZoneId, now: Instant): List<Instant> {
        if (alerts.isEmpty()) return emptyList()
        val times = if (type == CreateType.EVENT && allDay) listOf(date.atTime(9, 0).atZone(zone).toInstant())
        else alerts.sorted().map { startInstant(zone).minusSeconds(it * 60L) }
        return times.filter { it.isAfter(now) }
    }

    /** The alert minutes a new form of [type] starts with: events keep Google Calendar's 30 minutes, tasks none. */
    fun withType(newType: CreateType): CreateForm {
        if (newType == type) return this
        val untouched = alerts == defaultAlerts(type)
        return copy(type = newType, alerts = if (untouched) defaultAlerts(newType) else alerts, repeat = if (newType == CreateType.EVENT) null else repeat, allDay = false)
    }

    companion object {
        fun defaultAlerts(type: CreateType): Set<Int> = if (type == CreateType.EVENT) setOf(DEFAULT_EVENT_ALERT_MINUTES) else emptySet()

        /**
         * A fresh form for [type] on [date]. With no time given: today -> the next full hour (so it is ahead), another
         * day -> 9:00. Tasks start with no time at all until one is picked.
         */
        fun start(type: CreateType, date: LocalDate, time: LocalTime?, now: LocalDateTime): CreateForm {
            val chosen = when {
                time != null -> time
                type == CreateType.TASK -> null
                date == now.toLocalDate() -> now.toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1).takeIf { it > now.toLocalTime() } ?: LocalTime.of(23, 0)
                else -> LocalTime.of(9, 0)
            }
            return CreateForm(type = type, date = date, time = chosen, alerts = defaultAlerts(type))
        }
    }
}
