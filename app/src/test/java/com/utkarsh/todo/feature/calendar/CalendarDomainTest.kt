package com.utkarsh.todo.feature.calendar

import com.utkarsh.todo.feature.calendar.domain.MonthGrid
import com.utkarsh.todo.feature.calendar.domain.TimelineLayout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDomainTest {
    @Test
    fun `november 2026 grid starts on the sunday before the 1st and covers whole weeks`() {
        val dates = MonthGrid.dates(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(LocalDate.of(2026, 11, 1), dates.first())
        assertEquals(LocalDate.of(2026, 12, 5), dates.last()) // 30 Nov is Monday; pad to Saturday
        assertEquals(35, dates.size)
        assertTrue(dates.size % 7 == 0)
    }

    @Test
    fun `grid pads the first row with the previous month`() {
        val dates = MonthGrid.dates(YearMonth.of(2026, 10)) // 1 Oct 2026 is a Thursday
        assertEquals(LocalDate.of(2026, 9, 27), dates.first())
        assertEquals(DayOfWeek.SATURDAY, dates.last().dayOfWeek)
        assertEquals(35, dates.size)
    }

    @Test
    fun `a month can need six rows`() {
        assertEquals(42, MonthGrid.dates(YearMonth.of(2027, 5)).size) // 1 May 2027 is a Saturday, 31 days
    }

    @Test
    fun `monday first weeks are supported`() {
        val dates = MonthGrid.dates(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), dates.first())
        assertEquals(DayOfWeek.SUNDAY, dates.last().dayOfWeek)
    }

    @Test
    fun `week returns seven consecutive days containing the date`() {
        val week = MonthGrid.week(LocalDate.of(2026, 11, 7)) // Saturday
        assertEquals(LocalDate.of(2026, 11, 1), week.first())
        assertEquals(LocalDate.of(2026, 11, 7), week.last())
        assertEquals(7, week.size)
    }

    private data class Block(val name: String, val start: String, val end: String?)

    private fun place(vararg blocks: Block) =
        TimelineLayout.place(blocks.toList(), { Instant.parse(it.start) }, { it.end?.let(Instant::parse) }).associateBy { it.item.name }

    @Test
    fun `non-overlapping blocks all use one full-width lane`() {
        val placed = place(Block("a", "2026-11-07T09:00:00Z", "2026-11-07T10:00:00Z"), Block("b", "2026-11-07T10:00:00Z", "2026-11-07T11:00:00Z"))
        assertEquals(listOf(0 to 1, 0 to 1), placed.values.map { it.lane to it.laneCount })
    }

    @Test
    fun `overlapping blocks split into side-by-side lanes`() {
        val placed = place(
            Block("a", "2026-11-07T09:00:00Z", "2026-11-07T11:00:00Z"),
            Block("b", "2026-11-07T10:00:00Z", "2026-11-07T12:00:00Z"),
            Block("c", "2026-11-07T11:00:00Z", "2026-11-07T11:30:00Z"),
        )
        assertEquals(0, placed.getValue("a").lane)
        assertEquals(1, placed.getValue("b").lane)
        assertEquals(0, placed.getValue("c").lane) // lane 0 is free again at 11:00
        assertTrue(placed.values.all { it.laneCount == 2 })
    }

    @Test
    fun `a later separate block is not squeezed by an earlier cluster`() {
        val placed = place(
            Block("a", "2026-11-07T09:00:00Z", "2026-11-07T10:00:00Z"),
            Block("b", "2026-11-07T09:30:00Z", "2026-11-07T10:30:00Z"),
            Block("late", "2026-11-07T15:00:00Z", "2026-11-07T16:00:00Z"),
        )
        assertEquals(2, placed.getValue("a").laneCount)
        assertEquals(1, placed.getValue("late").laneCount)
    }

    @Test
    fun `tasks without an end get a minimum thirty minute block that can overlap`() {
        val placed = place(Block("task", "2026-11-07T09:00:00Z", null), Block("event", "2026-11-07T09:15:00Z", "2026-11-07T10:00:00Z"))
        assertEquals(Instant.parse("2026-11-07T09:30:00Z"), placed.getValue("task").end)
        assertEquals(2, placed.getValue("task").laneCount)
    }

    @Test
    fun `empty input places nothing`() {
        assertTrue(place().isEmpty())
    }
}
