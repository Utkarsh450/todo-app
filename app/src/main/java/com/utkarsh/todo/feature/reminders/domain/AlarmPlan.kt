package com.utkarsh.todo.feature.reminders.domain

import java.time.Instant
import java.time.ZoneId

/** Decides what to register with the OS for a reminder right now. Pure, so it can be tested without a device. */
object AlarmPlan {
    /**
     * The reminder to schedule, or null for none. A reminder that is off, or a one-off whose time has passed, gets
     * nothing. A repeating reminder whose time passed while the app or phone was off is rolled forward to its next
     * occurrence (counted from its original time, so it keeps its time of day) instead of being dropped.
     */
    fun plan(reminder: Reminder, now: Instant, zone: ZoneId): Reminder? {
        if (!reminder.enabled) return null
        if (reminder.effectiveAt.isAfter(now)) return reminder
        val rule = reminder.repeatRule ?: return null
        val next = RepeatRule.next(rule, reminder.remindAt.atZone(zone), now.atZone(zone)) ?: return null
        return reminder.copy(remindAt = next.toInstant(), snoozedUntil = null)
    }
}
