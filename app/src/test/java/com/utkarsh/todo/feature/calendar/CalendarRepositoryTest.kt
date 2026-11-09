package com.utkarsh.todo.feature.calendar

import app.cash.turbine.test
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.calendar.data.CalendarApi
import com.utkarsh.todo.feature.calendar.data.CalendarRepositoryImpl
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import com.utkarsh.todo.feature.reminders.domain.NewReminder
import com.utkarsh.todo.feature.reminders.domain.Reminder
import com.utkarsh.todo.feature.reminders.domain.RemindersRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

class CalendarRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: CalendarRepositoryImpl
    private val reminders = mockk<RemindersRepository>()

    private val event = NewEvent(
        title = "Lunch with Sam",
        start = Instant.parse("2026-11-13T07:30:00Z"),
        end = Instant.parse("2026-11-13T08:30:00Z"),
        allDay = false,
        projectId = "p-design",
    )

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        coEvery { reminders.create(any()) } answers {
            val r = firstArg<NewReminder>()
            Result.success(Reminder("r1", null, r.title, r.remindAt, null, null, true, null, r.eventId))
        }
        repository = CalendarRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<CalendarApi>(), reminders, Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() = server.close()

    private fun created() = MockResponse.Builder().code(201).addHeader("Content-Type", "application/json").body("""{"id":"e1","title":"Lunch with Sam"}""").build()

    @Test
    fun `creating an event posts every field in the shape the API expects`() = runTest {
        server.enqueue(created())

        repository.createEvent(event).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/events", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Lunch with Sam", body.getValue("title").jsonPrimitive.content)
        assertEquals("2026-11-13T07:30:00Z", body.getValue("startAt").jsonPrimitive.content)
        assertEquals("2026-11-13T08:30:00Z", body.getValue("endAt").jsonPrimitive.content)
        assertFalse(body.getValue("allDay").jsonPrimitive.boolean)
        assertEquals("p-design", body.getValue("projectId").jsonPrimitive.content)
    }

    @Test
    fun `an event without a project sends null for it`() = runTest {
        server.enqueue(created())

        repository.createEvent(event.copy(projectId = null, allDay = true)).getOrThrow()

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("null", body.getValue("projectId").toString())
        assertTrue(body.getValue("allDay").jsonPrimitive.boolean)
    }

    @Test
    fun `a created event announces a change`() = runTest {
        server.enqueue(created())

        repository.changes.test {
            repository.createEvent(event).getOrThrow()
            awaitItem()
        }
    }

    @Test
    fun `a rejected event is a failure and announces nothing`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).addHeader("Content-Type", "application/json").body("""{"error":"VALIDATION_ERROR"}""").build())

        repository.changes.test {
            assertTrue(repository.createEvent(event).isFailure)
            expectNoEvents()
        }
    }

    // ---- the alert before an event (clock is Thu 12 Nov 00:00 UTC; the event is Fri 13 Nov 07:30 UTC)

    @Test
    fun `an event gets a reminder 30 minutes before it by default, tied to the event`() = runTest {
        server.enqueue(created())

        val id = repository.createEvent(event).getOrThrow()

        assertEquals("e1", id)
        coVerify(exactly = 1) { reminders.create(NewReminder(title = "Lunch with Sam", remindAt = Instant.parse("2026-11-13T07:00:00Z"), eventId = "e1")) }
    }

    @Test
    fun `the lead time can be changed or switched off`() = runTest {
        server.enqueue(created())
        server.enqueue(created())

        repository.createEvent(event.copy(alertMinutes = 60)).getOrThrow()
        repository.createEvent(event.copy(alertMinutes = null)).getOrThrow()

        coVerify(exactly = 1) { reminders.create(match { it.remindAt == Instant.parse("2026-11-13T06:30:00Z") }) }
        coVerify(exactly = 1) { reminders.create(any()) }
    }

    @Test
    fun `an alert that would already have rung is skipped, and an all day event rings at 9 that morning`() = runTest {
        server.enqueue(created())
        server.enqueue(created())

        repository.createEvent(event.copy(start = Instant.parse("2026-11-12T00:10:00Z"), end = Instant.parse("2026-11-12T01:00:00Z"))).getOrThrow()
        coVerify(exactly = 0) { reminders.create(any()) }

        repository.createEvent(event.copy(allDay = true, start = Instant.parse("2026-11-13T00:00:00Z"), end = Instant.parse("2026-11-14T00:00:00Z"))).getOrThrow()
        coVerify(exactly = 1) { reminders.create(match { it.remindAt == Instant.parse("2026-11-13T09:00:00Z") }) }
    }

    @Test
    fun `a failing alert does not make creating the event fail`() = runTest {
        server.enqueue(created())
        coEvery { reminders.create(any()) } returns Result.failure(RuntimeException("offline"))

        assertTrue(repository.createEvent(event).isSuccess)
    }

    @Test
    fun `deleting an event calls the API and tells open calendars`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        repository.changes.test {
            repository.deleteEvent("e1").getOrThrow()
            awaitItem()
        }

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/events/e1", request.url.encodedPath)
    }

    @Test
    fun `the agenda knows reminders`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("""[{"kind":"reminder","id":"r1","title":"Stretch","startAt":"2026-11-13T08:00:00.000Z","endAt":null,"allDay":false,"projectId":null,"done":false}]""").build(),
        )

        val items = repository.agenda(Instant.parse("2026-11-13T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z")).getOrThrow()

        assertEquals(com.utkarsh.todo.feature.calendar.domain.AgendaKind.REMINDER, items.single().kind)
    }
}
