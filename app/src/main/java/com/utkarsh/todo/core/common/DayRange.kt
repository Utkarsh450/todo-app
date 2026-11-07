package com.utkarsh.todo.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Half-open instant range [start, end) covering one local calendar day. */
data class DayRange(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant) = instant >= start && instant < end
}

fun LocalDate.toDayRange(zone: ZoneId) = DayRange(
    start = atStartOfDay(zone).toInstant(),
    end = plusDays(1).atStartOfDay(zone).toInstant(),
)

/** The local day containing "now" for this clock, shifted by [offsetDays]. */
fun Clock.dayRange(offsetDays: Long = 0): DayRange = LocalDate.now(this).plusDays(offsetDays).toDayRange(zone)
