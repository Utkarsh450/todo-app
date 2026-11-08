package com.utkarsh.todo.feature.reminders

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.reminders.data.RemindersApi
import com.utkarsh.todo.feature.reminders.data.RemindersRepositoryImpl
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

private class FakeScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<String, Reminder>()
    val cancelled = mutableListOf<String>()
    override fun schedule(reminder: Reminder) { scheduled[reminder.id] = reminder }
    override fun cancel(id: String) { cancelled += id; scheduled.remove(id) }
}

class RemindersRepositoryTest {
    private val now = Instant.parse("2026-11-07T10:00:00Z")
    private val scheduler = FakeScheduler()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemindersRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = RemindersRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<RemindersApi>(), scheduler, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = server.close()

    private fun reminder(id: String = "r1", at: String = "2026-11-07T14:00:00.000Z", enabled: Boolean = true, snoozed: String? = null, repeat: String? = null) =
        """{"id":"$id","taskId":null,"title":"Drink water","remindAt":"$at","repeatRule":${repeat?.let { "\"$it\"" }},"locationTrigger":null,
           "enabled":$enabled,"snoozedUntil":${snoozed?.let { "\"$it\"" }},"createdAt":"2026-11-01T00:00:00.000Z"}"""

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `creating a future reminder schedules an alarm`() = runTest {
        server.enqueue(json(reminder(), 201))

        repository.create(NewReminder("Drink water", Instant.parse("2026-11-07T14:00:00Z"))).getOrThrow()

        assertEquals(setOf("r1"), scheduler.scheduled.keys)
    }

    @Test
    fun `a reminder in the past is not scheduled`() = runTest {
        server.enqueue(json(reminder(at = "2026-11-06T14:00:00.000Z"), 201))

        repository.create(NewReminder("Old", Instant.parse("2026-11-06T14:00:00Z"))).getOrThrow()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf("r1"), scheduler.cancelled)
    }

    @Test
    fun `disabling cancels the alarm and enabling schedules it again`() = runTest {
        server.enqueue(json(reminder(enabled = false)))
        server.enqueue(json(reminder(enabled = true)))

        repository.setEnabled("r1", false).getOrThrow()
        assertTrue(scheduler.scheduled.isEmpty())
        repository.setEnabled("r1", true).getOrThrow()
        assertEquals(setOf("r1"), scheduler.scheduled.keys)
    }

    @Test
    fun `snoozing schedules the alarm at the snooze time`() = runTest {
        server.enqueue(json(reminder(snoozed = "2026-11-07T11:00:00.000Z")))

        repository.snooze("r1", 60).getOrThrow()

        assertEquals(Instant.parse("2026-11-07T11:00:00Z"), scheduler.scheduled.getValue("r1").effectiveAt)
        assertEquals("""{"minutes":60}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `reschedule sends the new time and an explicit null snooze`() = runTest {
        server.enqueue(json(reminder(at = "2026-11-07T16:00:00.000Z", repeat = "Every 2 Hours")))

        repository.reschedule("r1", Instant.parse("2026-11-07T16:00:00Z")).getOrThrow()

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(setOf("remindAt", "snoozedUntil"), body.keys)
        assertEquals("null", body["snoozedUntil"].toString())
    }

    @Test
    fun `deleting cancels the alarm only when the server call succeeds`() = runTest {
        server.enqueue(json("""{"error":"x"}""", 500))
        assertTrue(repository.delete("r1").isFailure)
        assertTrue(scheduler.cancelled.isEmpty())

        server.enqueue(MockResponse.Builder().code(204).build())
        assertTrue(repository.delete("r1").isSuccess)
        assertEquals(listOf("r1"), scheduler.cancelled)
    }

    @Test
    fun `sync registers only enabled upcoming reminders`() = runTest {
        server.enqueue(json("[${reminder("a")},${reminder("b", enabled = false)},${reminder("c", at = "2026-11-01T00:00:00.000Z")}]"))

        repository.syncAlarms().getOrThrow()

        assertEquals(setOf("a"), scheduler.scheduled.keys)
    }

    @Test
    fun `grouped maps all four buckets and sends the day boundaries`() = runTest {
        server.enqueue(json("""{"overdue":[${reminder("o", "2026-11-06T20:00:00.000Z")}],"today":[${reminder("t")}],"thisWeek":[],"later":[${reminder("l", "2026-12-01T09:00:00.000Z")}]}"""))

        val groups = repository.grouped(now, Instant.parse("2026-11-08T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z")).getOrThrow()

        assertEquals(listOf("o"), groups.overdue.map { it.id })
        assertEquals(listOf("t"), groups.today.map { it.id })
        assertEquals(listOf("l"), groups.later.map { it.id })
        assertEquals(listOf("o", "t", "l"), groups.all.map { it.id })
        assertEquals("2026-11-08T00:00:00Z", server.takeRequest().url.queryParameter("endOfToday"))
    }
}
