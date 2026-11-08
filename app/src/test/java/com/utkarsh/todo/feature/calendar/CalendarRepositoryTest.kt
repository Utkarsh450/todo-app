package com.utkarsh.todo.feature.calendar

import app.cash.turbine.test
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.calendar.data.CalendarApi
import com.utkarsh.todo.feature.calendar.data.CalendarRepositoryImpl
import com.utkarsh.todo.feature.calendar.domain.NewEvent
import java.time.Instant
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
        repository = CalendarRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<CalendarApi>())
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
}
