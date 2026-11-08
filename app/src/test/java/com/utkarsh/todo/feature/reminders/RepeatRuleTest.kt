package com.utkarsh.todo.feature.reminders

import com.utkarsh.todo.feature.reminders.domain.RepeatRule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatRuleTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    // Saturday 7 Nov 2026
    @Test
    fun `weekday rules land on the next such weekday keeping the time of day`() {
        assertEquals(at(8, 18), RepeatRule.next("Every Sunday", from = at(1, 18), now = at(7, 10)))
        assertEquals(at(14, 18), RepeatRule.next("Every Saturday", from = at(7, 18), now = at(7, 18)))
        assertEquals(at(13, 9), RepeatRule.next("every friday", from = at(6, 9), now = at(7, 10)))
    }

    @Test
    fun `interval rules step from the original time rather than from now`() {
        assertEquals(at(7, 16), RepeatRule.next("Every 2 Hours", from = at(7, 14), now = at(7, 14, 30)))
        assertEquals(at(8, 7), RepeatRule.next("Every Day", from = at(7, 7), now = at(7, 7, 30)))
        assertEquals(at(28, 9), RepeatRule.next("Every 3 Weeks", from = at(7, 9), now = at(8, 0)))
        assertEquals(at(7, 10, 15), RepeatRule.next("Every 15 Minutes", from = at(7, 10), now = at(7, 10, 1)))
    }

    @Test
    fun `a long-missed reminder skips to the first future occurrence instead of bursting`() {
        // Every day at 07:00, last scheduled 5 days ago: next is tomorrow 07:00, not a replay of each missed day.
        assertEquals(at(8, 7), RepeatRule.next("Every Day", from = at(2, 7), now = at(7, 10)))
    }

    @Test
    fun `month rule keeps month ends valid`() {
        val jan31 = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 2, 28, 9, 0, 0, 0, zone), RepeatRule.next("Every Month", jan31, jan31))
    }

    @Test
    fun `a monthly rule on the 31st does not slide to the 28th after a short month`() {
        val jan31 = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, zone)
        val afterFeb = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone)
        // Counting from the original date: March 31, not March 28.
        assertEquals(ZonedDateTime.of(2026, 3, 31, 9, 0, 0, 0, zone), RepeatRule.next("Every Month", jan31, afterFeb))
        assertEquals(ZonedDateTime.of(2026, 5, 31, 9, 0, 0, 0, zone), RepeatRule.next("Every 2 Months", jan31, ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, zone)))
    }

    @Test
    fun `a reminder that fires a little late keeps its schedule`() {
        // Meant for 14:00, the alarm actually went off at 14:00:40. The next one is 16:00 sharp, not 16:00:40.
        val meant = at(7, 14)
        val late = at(7, 14).plusSeconds(40)
        assertEquals(at(7, 16), RepeatRule.next("Every 2 Hours", meant, late))
        assertEquals(at(8, 14), RepeatRule.next("Every Day", meant, late))
        assertEquals(at(14, 14), RepeatRule.next("Every Saturday", meant, late))
    }

    @Test
    fun `many cycles in a row never drift`() {
        var scheduled = at(1, 7)
        var now = scheduled.plusMinutes(3) // always three minutes late
        repeat(30) {
            scheduled = RepeatRule.next("Every Day", scheduled, now)!!
            now = scheduled.plusMinutes(3)
        }
        assertEquals(at(1, 7).plusDays(30), scheduled)
    }

    @Test
    fun `unknown or malformed rules are unsupported`() {
        assertNull(RepeatRule.next("Every Blue Moon", at(7, 9), at(7, 10)))
        assertNull(RepeatRule.next("sometimes", at(7, 9), at(7, 10)))
        assertNull(RepeatRule.next("Every 0 Hours", at(7, 9), at(7, 10)))
        assertNull(RepeatRule.next("Every 2 Sundays", at(7, 9), at(7, 10)))
        assertFalse(RepeatRule.isSupported("Every Blue Moon"))
        assertTrue(RepeatRule.isSupported("Every 2 Hours"))
    }
}
