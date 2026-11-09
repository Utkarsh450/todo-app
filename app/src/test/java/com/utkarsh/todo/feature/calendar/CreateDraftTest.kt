package com.utkarsh.todo.feature.calendar

import com.utkarsh.todo.feature.calendar.domain.CreateDraft
import com.utkarsh.todo.feature.calendar.domain.CreateKind
import com.utkarsh.todo.feature.calendar.ui.slotMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 7, 10, 20)
    private val draft = CreateDraft(title = "x", date = LocalDate.of(2026, 11, 10), start = LocalTime.of(9, 0))

    @Test
    fun `a new draft is an hour long with a 30 minute alert and no repeat`() {
        assertEquals(LocalTime.of(10, 0), draft.end)
        assertEquals(30, draft.alertMinutes)
        assertNull(draft.repeat)
        assertEquals(CreateKind.EVENT, draft.kind)
    }

    @Test
    fun `today starts at the next full hour, any other day at nine, and a tapped slot is used as is`() {
        assertEquals(LocalTime.of(11, 0), CreateDraft.at(LocalDate.of(2026, 11, 7), null, now).start)
        assertEquals(LocalTime.of(9, 0), CreateDraft.at(LocalDate.of(2026, 11, 8), null, now).start)
        assertEquals(LocalTime.of(14, 30), CreateDraft.at(LocalDate.of(2026, 11, 8), LocalTime.of(14, 30), now).start)
    }

    @Test
    fun `late at night the draft stays inside the day`() {
        val draft = CreateDraft.at(LocalDate.of(2026, 11, 7), null, LocalDateTime.of(2026, 11, 7, 23, 40))
        assertEquals(LocalTime.of(23, 0), draft.start)
        assertEquals(LocalTime.of(23, 59), draft.end)
        assertEquals(LocalTime.of(23, 59), CreateDraft.at(LocalDate.of(2026, 11, 8), LocalTime.of(23, 30), now).end)
    }

    @Test
    fun `moving the start keeps the length and never wraps past midnight`() {
        val moved = draft.copy(end = LocalTime.of(10, 30)).withStart(LocalTime.of(13, 0))
        assertEquals(LocalTime.of(14, 30), moved.end)
        assertEquals(LocalTime.of(23, 59), draft.withStart(LocalTime.of(23, 30)).end)
    }

    @Test
    fun `a draft needs a title, and an event needs its end after its start`() {
        assertEquals("Give it a title", draft.copy(title = "  ").problem())
        assertEquals("The end must be after the start", draft.copy(end = LocalTime.of(9, 0)).problem())
        assertNull(draft.problem())
        assertNull(draft.copy(end = LocalTime.of(8, 0), allDay = true).problem()) // the end is unused all day
        assertNull(draft.copy(kind = CreateKind.TASK, end = LocalTime.of(8, 0)).problem())
    }

    @Test
    fun `an event becomes instants in the person's time zone`() {
        assertEquals(Instant.parse("2026-11-10T03:30:00Z"), draft.startInstant(zone))
        assertEquals(Instant.parse("2026-11-10T04:30:00Z"), draft.endInstant(zone))
    }

    @Test
    fun `an all day event runs from midnight to midnight`() {
        val allDay = draft.copy(allDay = true)
        assertEquals(Instant.parse("2026-11-09T18:30:00Z"), allDay.startInstant(zone))
        assertEquals(Instant.parse("2026-11-10T18:30:00Z"), allDay.endInstant(zone))
    }

    @Test
    fun `a task with no time is due at nine and a reminder always has a time`() {
        val task = draft.copy(kind = CreateKind.TASK, allDay = true, start = LocalTime.of(15, 0))
        assertEquals(Instant.parse("2026-11-10T03:30:00Z"), task.taskDue(zone))
        assertTrue(draft.copy(kind = CreateKind.REMINDER, allDay = true).hasTime)
        assertEquals(Instant.parse("2026-11-10T03:30:00Z"), draft.copy(kind = CreateKind.REMINDER, allDay = true).startInstant(zone))
    }

    @Test
    fun `a tap lands in its half hour, kept inside the day`() {
        val hour = 100f
        assertEquals(0, slotMinutes(0f, hour))
        assertEquals(0, slotMinutes(49f, hour))
        assertEquals(30, slotMinutes(50f, hour))
        assertEquals(11 * 60, slotMinutes(1_120f, hour))
        assertEquals(11 * 60 + 30, slotMinutes(1_199f, hour))
        assertEquals(23 * 60 + 30, slotMinutes(9_999f, hour))
        assertEquals(0, slotMinutes(-20f, hour))
    }
}
