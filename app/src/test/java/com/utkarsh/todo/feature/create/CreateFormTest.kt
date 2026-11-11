package com.utkarsh.todo.feature.create

import com.utkarsh.todo.feature.create.domain.CreateForm
import com.utkarsh.todo.feature.create.domain.CreateType
import com.utkarsh.todo.feature.create.domain.alertLabel
import com.utkarsh.todo.feature.create.domain.repeatLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreateFormTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 12)
    private val now = LocalDateTime.of(2026, 11, 12, 10, 20)
    private val nowInstant = now.atZone(zone).toInstant()

    @Test
    fun `a new task has no time and no alerts, a new event starts at the next full hour with the 30 minute alert`() {
        val task = CreateForm.start(CreateType.TASK, today, null, now)
        assertNull(task.time)
        assertEquals(emptySet<Int>(), task.alerts)

        val event = CreateForm.start(CreateType.EVENT, today, null, now)
        assertEquals(LocalTime.of(11, 0), event.time)
        assertEquals(setOf(30), event.alerts)
        assertEquals(LocalTime.of(9, 0), CreateForm.start(CreateType.EVENT, today.plusDays(1), null, now).time)
    }

    @Test
    fun `a tapped time is kept as it is`() {
        assertEquals(LocalTime.of(14, 30), CreateForm.start(CreateType.EVENT, today, LocalTime.of(14, 30), now).time)
        assertEquals(LocalTime.of(14, 30), CreateForm.start(CreateType.TASK, today, LocalTime.of(14, 30), now).time)
    }

    @Test
    fun `late in the evening the next hour would be tomorrow, so an event starts at 11 pm`() {
        assertEquals(LocalTime.of(23, 0), CreateForm.start(CreateType.EVENT, today, null, LocalDateTime.of(2026, 11, 12, 23, 40)).time)
    }

    @Test
    fun `a task without a time is due at 9 that morning`() {
        val form = CreateForm.start(CreateType.TASK, today.plusDays(1), null, now)
        assertEquals(Instant.parse("2026-11-13T03:30:00Z"), form.startInstant(zone))
    }

    @Test
    fun `an event runs for its length, or the whole day`() {
        val form = CreateForm.start(CreateType.EVENT, today, LocalTime.of(15, 0), now).copy(durationMinutes = 90)
        assertEquals(Instant.parse("2026-11-12T09:30:00Z"), form.startInstant(zone))
        assertEquals(Instant.parse("2026-11-12T11:00:00Z"), form.endInstant(zone))

        val allDay = form.copy(allDay = true)
        assertEquals(Instant.parse("2026-11-11T18:30:00Z"), allDay.startInstant(zone))
        assertEquals(Instant.parse("2026-11-12T18:30:00Z"), allDay.endInstant(zone))
    }

    @Test
    fun `each alert rings that many minutes before, and ones already past are left out`() {
        val form = CreateForm.start(CreateType.TASK, today, LocalTime.of(10, 40), now).copy(alerts = setOf(0, 10, 30, 60))
        // due 10:40, now 10:20 -> only "at time" (10:40) and "10 min" (10:30) are still ahead
        assertEquals(
            listOf(Instant.parse("2026-11-12T05:10:00Z"), Instant.parse("2026-11-12T05:00:00Z")), // "at time" first, then 10 min before
            form.alertInstants(zone, nowInstant),
        )
    }

    @Test
    fun `an all day event rings once at 9 that morning`() {
        val form = CreateForm.start(CreateType.EVENT, today.plusDays(2), null, now).copy(allDay = true, alerts = setOf(10, 60))
        assertEquals(listOf(Instant.parse("2026-11-14T03:30:00Z")), form.alertInstants(zone, nowInstant))
    }

    @Test
    fun `no alerts means nothing rings`() {
        assertEquals(emptyList<Instant>(), CreateForm.start(CreateType.TASK, today, LocalTime.of(18, 0), now).alertInstants(zone, nowInstant))
    }

    @Test
    fun `switching type keeps a hand-picked alert list but swaps untouched defaults and drops event-only things`() {
        val event = CreateForm.start(CreateType.EVENT, today, null, now)
        assertEquals(emptySet<Int>(), event.withType(CreateType.TASK).alerts) // the event default does not follow into a task
        val custom = event.copy(alerts = setOf(10))
        assertEquals(setOf(10), custom.withType(CreateType.TASK).alerts)
        assertEquals(setOf(30), CreateForm.start(CreateType.TASK, today, null, now).withType(CreateType.EVENT).alerts)
        assertNull(CreateForm.start(CreateType.TASK, today, null, now).copy(repeat = "Every Day").withType(CreateType.EVENT).repeat)
    }

    @Test
    fun `a title is needed`() {
        val form = CreateForm.start(CreateType.TASK, today, null, now)
        assertEquals("Give it a title", form.problem())
        assertEquals("Give it a title", form.copy(title = "   ").problem())
        assertNull(form.copy(title = "Pay rent").problem())
    }

    @Test
    fun `labels read naturally`() {
        assertEquals("At time", alertLabel(0))
        assertEquals("10 min before", alertLabel(10))
        assertEquals("90 min before", alertLabel(90))
        assertEquals("1 hour before", alertLabel(60))
        assertEquals("3 hours before", alertLabel(180))
        assertEquals("1 day before", alertLabel(1440))
        assertEquals("2 days before", alertLabel(2880))
        assertEquals("Never", repeatLabel(null))
        assertEquals("Weekly", repeatLabel("Every Week"))
        assertEquals("Every 2 Weeks", repeatLabel("Every 2 Weeks"))
    }
}
