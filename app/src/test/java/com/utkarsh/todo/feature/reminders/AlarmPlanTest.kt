package com.utkarsh.todo.feature.reminders

import com.utkarsh.todo.feature.reminders.domain.AlarmPlan
import com.utkarsh.todo.feature.reminders.domain.Reminder
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmPlanTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-07T10:00:00Z") // 15:30 in Kolkata

    private fun reminder(at: String, repeat: String? = null, enabled: Boolean = true, snoozed: String? = null) =
        Reminder("r", null, "Drink water", Instant.parse(at), repeat, null, enabled, snoozed?.let(Instant::parse))

    @Test
    fun `an upcoming reminder is scheduled as it is`() {
        val r = reminder("2026-11-07T14:00:00Z")
        assertEquals(r, AlarmPlan.plan(r, now, zone))
    }

    @Test
    fun `a switched off reminder gets no alarm`() {
        assertNull(AlarmPlan.plan(reminder("2026-11-07T14:00:00Z", enabled = false), now, zone))
    }

    @Test
    fun `a one-off whose time has passed gets no alarm`() {
        assertNull(AlarmPlan.plan(reminder("2026-11-07T08:00:00Z"), now, zone))
    }

    @Test
    fun `a snooze in the future wins over the original time`() {
        val r = reminder("2026-11-07T08:00:00Z", snoozed = "2026-11-07T11:00:00Z")
        assertEquals(r, AlarmPlan.plan(r, now, zone))
    }

    @Test
    fun `a repeating reminder that came due is rolled forward from its original time and loses its snooze`() {
        // Every day at 09:00 Kolkata (03:30Z), last set for 3 Nov; the next one after 15:30 on the 7th is the 8th at 09:00.
        val planned = AlarmPlan.plan(reminder("2026-11-03T03:30:00Z", repeat = "Every Day", snoozed = "2026-11-03T04:00:00Z"), now, zone)!!
        assertEquals(Instant.parse("2026-11-08T03:30:00Z"), planned.remindAt)
        assertNull(planned.snoozedUntil)
    }

    @Test
    fun `a repeat rule that is not understood is dropped rather than guessed`() {
        assertNull(AlarmPlan.plan(reminder("2026-11-03T03:30:00Z", repeat = "whenever"), now, zone))
    }
}
