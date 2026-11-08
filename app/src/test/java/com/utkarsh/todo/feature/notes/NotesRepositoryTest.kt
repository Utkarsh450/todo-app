package com.utkarsh.todo.feature.notes

import app.cash.turbine.test
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.notes.data.NotesApi
import com.utkarsh.todo.feature.notes.data.NotesRepositoryImpl
import com.utkarsh.todo.feature.notes.data.appendToNote
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

class NotesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NotesRepositoryImpl
    private val day = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = NotesRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<NotesApi>())
    }

    @After
    fun tearDown() = server.close()

    private fun note(text: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body("""{"date":"2026-11-07","text":${kotlinx.serialization.json.JsonPrimitive(text)}}""").build()

    private fun failure() = MockResponse.Builder().code(500).addHeader("Content-Type", "application/json").body("""{"error":"INTERNAL_ERROR"}""").build()

    // ---- the text rule ----

    @Test
    fun `adding to an empty note writes just the new text`() {
        assertEquals("Buy milk", appendToNote("", "Buy milk"))
        assertEquals("Buy milk", appendToNote("   \n ", "  Buy milk  "))
    }

    @Test
    fun `adding to a note puts the new text on its own line and tidies the edges`() {
        assertEquals("Ask Sam\nBuy milk", appendToNote("Ask Sam", "Buy milk"))
        assertEquals("Ask Sam\nBuy milk", appendToNote("Ask Sam  \n\n", "  Buy milk "))
    }

    @Test
    fun `existing lines are never changed`() {
        val existing = "line one\n\n  indented line\nlast"
        assertEquals("$existing\nnew", appendToNote(existing, "new"))
    }

    // ---- over HTTP ----

    @Test
    fun `append reads the note and then writes the combined text`() = runTest {
        server.enqueue(note("Ask Sam"))
        server.enqueue(note("Ask Sam\nBuy milk"))

        repository.append(day, "Buy milk").getOrThrow()

        val read = server.takeRequest()
        assertEquals("GET", read.method)
        assertEquals("/api/notes/2026-11-07", read.url.encodedPath)
        val write = server.takeRequest()
        assertEquals("PUT", write.method)
        assertEquals("/api/notes/2026-11-07", write.url.encodedPath)
        assertEquals("""{"text":"Ask Sam\nBuy milk"}""".replace("\n", "\\n"), write.body!!.utf8())
    }

    @Test
    fun `appending to a day with no note writes only the new text`() = runTest {
        server.enqueue(note(""))
        server.enqueue(note("Buy milk"))

        repository.append(day, "Buy milk").getOrThrow()

        server.takeRequest()
        assertEquals("""{"text":"Buy milk"}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `if the note cannot be read nothing is written so nothing can be lost`() = runTest {
        server.enqueue(failure())

        val result = repository.append(day, "Buy milk")

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount) // no PUT was sent
    }

    @Test
    fun `a failed write is reported`() = runTest {
        server.enqueue(note("Ask Sam"))
        server.enqueue(failure())

        assertTrue(repository.append(day, "Buy milk").isFailure)
    }

    // ---- change signal ----

    @Test
    fun `a successful append announces a change`() = runTest {
        server.enqueue(note(""))
        server.enqueue(note("x"))

        repository.changes.test {
            repository.append(day, "x").getOrThrow()
            awaitItem()
        }
    }

    @Test
    fun `a failed append announces nothing`() = runTest {
        server.enqueue(failure())

        repository.changes.test {
            assertFalse(repository.append(day, "x").isSuccess)
            expectNoEvents()
        }
    }

    @Test
    fun `saving from the scratchpad itself does not announce a change`() = runTest {
        server.enqueue(note("typed"))

        repository.changes.test {
            repository.save(day, "typed").getOrThrow()
            expectNoEvents() // otherwise the editor would reload its own save
        }
    }
}
