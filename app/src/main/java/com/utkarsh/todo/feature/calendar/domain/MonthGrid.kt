package com.utkarsh.todo.feature.calendar.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The dates shown for a month: whole weeks starting on [firstDayOfWeek], so leading and trailing
 * days of neighbouring months pad the first and last rows. Five or six rows depending on the month.
 */
object MonthGrid {
    fun dates(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val lastDayOfWeek = firstDayOfWeek.minus(1)
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    /** The seven dates of the week containing [date]. */
    fun week(date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (0L until 7L).map(start::plusDays)
    }
}
