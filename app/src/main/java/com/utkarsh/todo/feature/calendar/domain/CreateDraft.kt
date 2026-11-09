package com.utkarsh.todo.feature.calendar.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the person is making from the calendar's "+" or by tapping a time slot. */
enum class CreateKind(val label: String) { EVENT("Event"), TASK("Task"), REMINDER("Reminder") }

/** The alert choices for an event, as minutes before it starts (null = none). */
val AlertChoices: List<Pair<Int?, String>> = listOf(null to "None", 0 to "At time", 10 to "10 min", 30 to "30 min", 60 to "1 hour", 1440 to "1 day")

/** How often a task or reminder repeats, as the repeat-rule text the rest of the app understands (null = never). */
val RepeatChoices: List<Pair<String?, String>> = listOf(null to "Never", "Every Day" to "Daily", "Every Week" to "Weekly", "Every Month" to "Monthly")

/** The form behind the create sheet. Pure data: the view-model edits it and turns it into an event, a task or a reminder. */
data class CreateDraft(
    val kind: CreateKind = CreateKind.EVENT,
    val title: String = "",
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime = start.plusHours(1),
    /** Events: all day. Tasks: due that day without a particular time. Ignored for reminders. */
    val allDay: Boolean = false,
    val alertMinutes: Int? = DEFAULT_ALERT_MINUTES,
    val repeat: String? = null,
) {
    /** Moving the start keeps the length of the event; the end never wraps past midnight (it stops at 23:59). */
    fun withStart(newStart: LocalTime): CreateDraft {
        val length = java.time.Duration.between(start, end).coerceAtLeast(java.time.Duration.ofMinutes(15))
        val newEnd = if (newStart.plus(length).isAfter(newStart)) newStart.plus(length) else LocalTime.of(23, 59)
        return copy(start = newStart, end = newEnd)
    }

    val hasTime: Boolean get() = kind == CreateKind.REMINDER || !allDay

    /** Null when the draft can be saved, else a short message for the person. */
    fun problem(): String? = when {
        title.isBlank() -> "Give it a title"
        kind == CreateKind.EVENT && !allDay && !end.isAfter(start) -> "The end must be after the start"
        else -> null
    }

    fun startInstant(zone: ZoneId): Instant =
        if (hasTime) date.atTime(start).atZone(zone).toInstant() else date.atStartOfDay(zone).toInstant()

    fun endInstant(zone: ZoneId): Instant =
        if (allDay) date.plusDays(1).atStartOfDay(zone).toInstant() else date.atTime(end).atZone(zone).toInstant()

    /** A task without a time is due at 9:00 that morning, so it shows up in the day and can still be reminded. */
    fun taskDue(zone: ZoneId): Instant = if (allDay) date.atTime(9, 0).atZone(zone).toInstant() else startInstant(zone)

    companion object {
        /**
         * The draft for a tap at [date] and [time]; with no time, the next full hour when the day is today
         * (so it is in the future) or 9:00 on any other day. Slot taps pass the tapped half hour.
         */
        fun at(date: LocalDate, time: LocalTime?, now: java.time.LocalDateTime, kind: CreateKind = CreateKind.EVENT): CreateDraft {
            val start = time ?: if (date == now.toLocalDate()) now.toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1).takeIf { it > now.toLocalTime() } ?: LocalTime.of(23, 0) else LocalTime.of(9, 0)
            return CreateDraft(kind = kind, date = date, start = start, end = if (start.hour >= 23) LocalTime.of(23, 59) else start.plusHours(1))
        }
    }
}
