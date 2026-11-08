package com.utkarsh.todo.feature.reminders.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Computes the next occurrence of a textual repeat rule such as `Every Sunday`, `Every 2 Hours`,
 * `Every Day` or `Every 3 Weeks`. Rules come from the quick-add parser, so they are capitalised words.
 */
object RepeatRule {
    private val RULE = Regex("""^every\s+(?:(\d+)\s+)?([a-z]+)$""", RegexOption.IGNORE_CASE)

    /** True when [rule] is understood by [next]. */
    fun isSupported(rule: String): Boolean = parse(rule) != null

    /**
     * First occurrence strictly after [now], keeping the time of day of [from] for day-based rules.
     * Returns null for unknown rules. Interval rules step from [from] until they pass [now], so a
     * reminder that was missed for days does not fire a burst of catch-up notifications.
     */
    fun next(rule: String, from: ZonedDateTime, now: ZonedDateTime): ZonedDateTime? {
        val parsed = parse(rule) ?: return null
        var candidate = from
        // Step in a loop: bounded because every step moves forward by at least a minute-sized unit,
        // and the cap protects against absurdly old `from` values.
        repeat(MAX_STEPS) {
            candidate = parsed.step(candidate)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    private const val MAX_STEPS = 100_000

    private sealed interface Parsed {
        fun step(from: ZonedDateTime): ZonedDateTime
    }

    private data class Interval(val amount: Long, val unit: Unit) : Parsed {
        enum class Unit { MINUTES, HOURS, DAYS, WEEKS, MONTHS }
        override fun step(from: ZonedDateTime): ZonedDateTime = when (unit) {
            Unit.MINUTES -> from.plus(Duration.ofMinutes(amount))
            Unit.HOURS -> from.plus(Duration.ofHours(amount))
            Unit.DAYS -> from.plusDays(amount)
            Unit.WEEKS -> from.plusWeeks(amount)
            Unit.MONTHS -> from.plusMonths(amount)
        }
    }

    private data class Weekday(val day: DayOfWeek) : Parsed {
        override fun step(from: ZonedDateTime): ZonedDateTime {
            var next = from.plusDays(1)
            while (next.dayOfWeek != day) next = next.plusDays(1)
            return next
        }
    }

    private fun parse(rule: String): Parsed? {
        val match = RULE.matchEntire(rule.trim()) ?: return null
        val amount = match.groupValues[1].ifEmpty { "1" }.toLong().takeIf { it > 0 } ?: return null
        val word = match.groupValues[2].lowercase()
        weekday(word)?.let { return if (match.groupValues[1].isEmpty()) Weekday(it) else null }
        val unit = when (word.removeSuffix("s")) {
            "minute", "min" -> Interval.Unit.MINUTES
            "hour", "hr" -> Interval.Unit.HOURS
            "day" -> Interval.Unit.DAYS
            "week" -> Interval.Unit.WEEKS
            "month" -> Interval.Unit.MONTHS
            else -> return null
        }
        return Interval(amount, unit)
    }

    private fun weekday(word: String): DayOfWeek? = when (word) {
        "monday", "mon" -> DayOfWeek.MONDAY
        "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY
        "wednesday", "wed" -> DayOfWeek.WEDNESDAY
        "thursday", "thu", "thurs" -> DayOfWeek.THURSDAY
        "friday", "fri" -> DayOfWeek.FRIDAY
        "saturday", "sat" -> DayOfWeek.SATURDAY
        "sunday", "sun" -> DayOfWeek.SUNDAY
        else -> null
    }
}
