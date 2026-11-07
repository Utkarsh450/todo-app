package com.utkarsh.todo.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val headerFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** `9:00 AM` */
fun Instant.formatTime(zone: ZoneId): String = timeFormatter.format(atZone(zone))

/** `SAT 7 NOV · W45` - the mono caption above the Today title. */
fun Instant.formatDayCaption(zone: ZoneId): String {
    val date = atZone(zone)
    return "${headerFormatter.format(date).uppercase(Locale.ENGLISH)} · W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
}
