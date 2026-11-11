package com.utkarsh.todo.feature.reminders.domain

import java.time.Instant

/** How an alert shows up: an ordinary notification (Done / Snooze), or an alarm that rings and takes over the screen. */
enum class AlertStyle { NOTIFICATION, ALARM }

data class Reminder(
    val id: String,
    val taskId: String?,
    val title: String,
    val remindAt: Instant,
    val repeatRule: String?,
    val locationTrigger: String?,
    val enabled: Boolean,
    val snoozedUntil: Instant?,
    /** The calendar event this reminder is the alert for, if any. */
    val eventId: String? = null,
    val style: AlertStyle = AlertStyle.ALARM,
) {
    /** A snoozed reminder fires at its snooze time, otherwise at its original time. */
    val effectiveAt: Instant get() = snoozedUntil ?: remindAt
}

data class NewReminder(
    val title: String,
    val remindAt: Instant,
    val taskId: String? = null,
    val repeatRule: String? = null,
    val locationTrigger: String? = null,
    val eventId: String? = null,
    val style: AlertStyle = AlertStyle.ALARM,
)

/** Reminder buckets of the Reminders screen, in display order. */
data class ReminderGroups(
    val overdue: List<Reminder> = emptyList(),
    val today: List<Reminder> = emptyList(),
    val thisWeek: List<Reminder> = emptyList(),
    val later: List<Reminder> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && thisWeek.isEmpty() && later.isEmpty()
    val all: List<Reminder> get() = overdue + today + thisWeek + later
}
