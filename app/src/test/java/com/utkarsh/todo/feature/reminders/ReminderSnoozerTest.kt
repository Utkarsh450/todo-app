package com.utkarsh.todo.feature.reminders

import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import com.utkarsh.todo.feature.reminders.platform.ReminderSnoozer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSnoozerTest {
    private val repository = mockk<RemindersRepository>()
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)
    private val now = Instant.parse("2026-11-08T10:00:00Z")
    private val snoozer = ReminderSnoozer(repository, scheduler, Clock.fixed(now, ZoneOffset.UTC))
    private val due = Instant.parse("2026-11-08T09:58:00Z")
    private val saved = Reminder("r", null, "Dentist", due, null, null, true, now.plusSeconds(600))

    @Test
    fun `asks the repository to snooze and leaves the alarm to it`() = runTest {
        coEvery { repository.snooze("r", 10) } returns Result.success(saved)

        snoozer.snooze("r", "Dentist", due, null, null, 10)

        coVerify(exactly = 1) { repository.snooze("r", 10) }
        verify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `when the server cannot be reached the alarm is still set from what the alarm carried`() = runTest {
        coEvery { repository.snooze(any(), any()) } returns Result.failure(RuntimeException("offline"))
        val armed = slot<Reminder>()

        snoozer.snooze("r", "Dentist", due, "Every day", "task-1", 30)

        verify(exactly = 1) { scheduler.schedule(capture(armed)) }
        assertEquals("r", armed.captured.id)
        assertEquals("Dentist", armed.captured.title)
        assertEquals("task-1", armed.captured.taskId)
        assertEquals("Every day", armed.captured.repeatRule)
        assertEquals(due, armed.captured.remindAt)
        assertEquals(now.plusSeconds(30 * 60), armed.captured.effectiveAt)
    }
}
