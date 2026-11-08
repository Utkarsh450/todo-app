package com.utkarsh.todo.feature.quickadd

import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.TokenKind
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    // Saturday 7 November 2026, 10:00 local.
    private val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
    private val parser = QuickAddParser(Clock.fixed(Instant.from(now), zone))

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    @Test
    fun `parses the design example end to end`() {
        val r = parser.parse("Review design tokens tomorrow 9am #Design !high")

        assertEquals("Review design tokens", r.title)
        assertEquals(at(8, 9), r.dueAt)
        assertEquals("Design", r.projectName)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals(listOf(TokenKind.PRIORITY, TokenKind.PROJECT, TokenKind.DAY), r.tokens.map { it.kind })
    }

    @Test
    fun `plain text has no tokens and keeps the title`() {
        val r = parser.parse("Buy oat milk")
        assertEquals("Buy oat milk", r.title)
        assertNull(r.dueAt)
        assertNull(r.projectName)
        assertNull(r.priority)
        assertNull(r.repeat)
        assertEquals(emptyList<Any>(), r.tokens)
    }

    @Test
    fun `day words default to 9am and tonight to 8pm`() {
        assertEquals(at(7, 9), parser.parse("Call mum today").dueAt)
        assertEquals(at(8, 9), parser.parse("Call mum tomorrow").dueAt)
        assertEquals(at(7, 20), parser.parse("Dinner tonight").dueAt)
    }

    @Test
    fun `explicit times honour am pm and minutes`() {
        assertEquals(at(8, 15, 30), parser.parse("Gym tomorrow 3:30 pm").dueAt)
        assertEquals(at(8, 0), parser.parse("Flight tomorrow 12am").dueAt)
        assertEquals(at(8, 12), parser.parse("Lunch tomorrow at 12pm").dueAt)
        assertEquals("Lunch", parser.parse("Lunch tomorrow at 12pm").title)
    }

    @Test
    fun `weekday resolves to the next occurrence and never today`() {
        assertEquals(at(9, 9), parser.parse("Plan sprint Monday").dueAt) // Sat -> Mon
        assertEquals(at(14, 9), parser.parse("Retro saturday").dueAt) // same weekday -> next week
        assertEquals(at(8, 18), parser.parse("Weekly review sun 6pm").dueAt)
    }

    @Test
    fun `a bare time means later today or else tomorrow`() {
        assertEquals(at(7, 17), parser.parse("Groceries 5pm").dueAt)
        assertEquals(at(8, 8), parser.parse("Run 8am").dueAt) // already past 10:00 today
    }

    @Test
    fun `repeat is captured and not mistaken for a due day`() {
        val r = parser.parse("Weekly review every sunday")

        assertEquals("Weekly review", r.title)
        assertEquals("Every Sunday", r.repeat)
        assertNull(r.dueAt)
        assertEquals(listOf(TokenKind.REPEAT), r.tokens.map { it.kind })
    }

    @Test
    fun `repeat with a number`() {
        val r = parser.parse("Drink water every 2 hours")
        assertEquals("Every 2 Hours", r.repeat)
        assertEquals("Drink water", r.title)
    }

    @Test
    fun `priority is case insensitive and normalised for display`() {
        val r = parser.parse("Fix crash !URGENT")
        assertEquals(Priority.URGENT, r.priority)
        assertEquals("Urgent", r.tokens.single().label)
    }

    @Test
    fun `unknown priority stays in the title`() {
        val r = parser.parse("Ship !soon")
        assertNull(r.priority)
        assertEquals("Ship !soon", r.title)
    }

    @Test
    fun `invalid clock times are ignored`() {
        assertNull(parser.parse("Meet 13pm").dueAt)
        assertNull(parser.parse("Meet 9:75am").dueAt)
    }

    @Test
    fun `day-like fragments inside words are not tokens`() {
        val r = parser.parse("Pay money to monitor guy")
        assertNull(r.dueAt)
        assertEquals("Pay money to monitor guy", r.title)
    }

    @Test
    fun `collapses whitespace left behind by removed tokens`() {
        assertEquals("Write notes", parser.parse("  Write   #Learning   notes   !low ").title)
    }

    @Test
    fun `everything together`() {
        val r = parser.parse("Weekly review fri 6pm #Work !normal every friday")

        assertEquals("Weekly review", r.title)
        assertEquals(at(13, 18), r.dueAt) // the coming Friday at 6pm
        assertEquals("Work", r.projectName)
        assertEquals(Priority.NORMAL, r.priority)
        assertEquals("Every Friday", r.repeat)
    }

    @Test
    fun `only the allowed kinds are recognised and the rest stay in the title`() {
        val r = parser.parse("Fix bug !high #Design every week tomorrow 3pm", setOf(TokenKind.DAY))

        assertEquals("Fix bug !high #Design every week", r.title)
        assertEquals(listOf(TokenKind.DAY), r.tokens.map { it.kind })
        assertEquals(at(8, 15), r.dueAt)
        assertNull(r.priority)
        assertNull(r.projectName)
        assertNull(r.repeat)
    }

    @Test
    fun `no allowed kinds leaves the text alone`() {
        val r = parser.parse("Call tomorrow 5pm #x !high every day", emptySet())

        assertEquals("Call tomorrow 5pm #x !high every day", r.title)
        assertEquals(emptyList<Any>(), r.tokens)
        assertNull(r.dueAt)
        assertFalse(r.timeSpecified)
    }

    @Test
    fun `a project is not recognised unless it is allowed`() {
        val r = parser.parse("Plan #Design", setOf(TokenKind.DAY, TokenKind.PRIORITY))
        assertEquals("Plan #Design", r.title)
        assertNull(r.projectName)
    }

    @Test
    fun `it reports whether a clock time came with the day`() {
        assertTrue(parser.parse("Call tomorrow 9am").timeSpecified)
        assertTrue(parser.parse("Call fri at 3:30pm").timeSpecified)
        assertTrue(parser.parse("Call at 5pm").timeSpecified)
        assertFalse(parser.parse("Call tomorrow").timeSpecified)
        assertFalse(parser.parse("Call fri").timeSpecified)
        assertFalse(parser.parse("Buy oat milk").timeSpecified)
    }
}
