package com.utkarsh.todo.feature.ai

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

class AiRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AiRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repository = AiRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<AiApi>())
    }

    @After
    fun tearDown() = server.close()

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `sends the task and returns the suggested steps`() = runTest {
        server.enqueue(json(200, """{"subtasks":["Draft outline","Write intro"]}"""))

        val result = repository.breakDown("Write essay", "about owls", Instant.parse("2026-11-10T09:00:00Z"))

        assertEquals(listOf("Draft outline", "Write intro"), result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/ai/breakdown", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Write essay", body["title"]!!.jsonPrimitive.content)
        assertEquals("about owls", body["notes"]!!.jsonPrimitive.content)
        assertEquals("2026-11-10T09:00:00Z", body["dueAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a task without a due date leaves it out`() = runTest {
        server.enqueue(json(200, """{"subtasks":["a"]}"""))
        repository.breakDown("x", "", null).getOrThrow()
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertFalse("dueAt" in body)
    }

    @Test
    fun `a server without a key says how to fix it`() = runTest {
        server.enqueue(json(503, """{"error":"AI_NOT_CONFIGURED","message":"AI is not set up. Add XAI_API_KEY to backend/.env and restart the server."}"""))

        val error = repository.breakDown("x", "", null).exceptionOrNull() as AiException

        assertTrue(error.notConfigured)
        assertTrue(error.message!!.contains("XAI_API_KEY"))
    }

    @Test
    fun `upstream trouble keeps the server's own message`() = runTest {
        server.enqueue(json(502, """{"error":"AI_UPSTREAM_ERROR","message":"The AI service is busy or over its limit. Try again in a moment."}"""))

        val error = repository.breakDown("x", "", null).exceptionOrNull() as AiException

        assertFalse(error.notConfigured)
        assertEquals("The AI service is busy or over its limit. Try again in a moment.", error.message)
    }

    @Test
    fun `a timeout from the server is reported with its message`() = runTest {
        server.enqueue(json(504, """{"error":"AI_TIMEOUT","message":"The AI service took too long to answer. Try again."}"""))
        assertEquals("The AI service took too long to answer. Try again.", repository.breakDown("x", "", null).exceptionOrNull()!!.message)
    }

    @Test
    fun `an unexpected server error gets a generic message with the status`() = runTest {
        server.enqueue(json(500, """{"error":"INTERNAL_ERROR","message":"Something went wrong"}"""))
        val error = repository.breakDown("x", "", null).exceptionOrNull() as AiException
        assertEquals("The server had a problem (500). Try again.", error.message)
    }

    @Test
    fun `an unreadable error body is still a readable failure`() = runTest {
        server.enqueue(json(502, "<html>bad gateway</html>"))
        val error = repository.breakDown("x", "", null).exceptionOrNull() as AiException
        assertEquals("The server had a problem (502). Try again.", error.message)
    }

    @Test
    fun `an unreachable server is explained`() = runTest {
        server.close()
        val offline = AiRepositoryImpl(
            buildRetrofit(server.url("/api/").toString(), OkHttpClient.Builder().connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS).build()).create<AiApi>(),
        )
        val error = offline.breakDown("x", "", null).exceptionOrNull() as AiException
        assertTrue(error.message!!.startsWith("Can't reach the server"))
    }

    // ---- assistant

    @Test
    fun `assist sends the question and time zone and maps every kind of suggestion`() = runTest {
        server.enqueue(
            json(
                200,
                """{"reply":"A lighter shape.","suggestions":[
                  {"kind":"MOVE","taskId":"a","taskTitle":"Review PR","reason":"Sharp hour","dueAt":"2026-11-09T03:30:00.000Z"},
                  {"kind":"SPLIT","taskId":"b","taskTitle":"Ship it","reason":"Big","steps":["One","Two"]},
                  {"kind":"PRIORITY","taskId":"c","taskTitle":"Pay bill","reason":"Due","priority":"URGENT"}]}""",
            ),
        )

        val reply = repository.assist("Plan my day", ZoneId.of("Asia/Kolkata")).getOrThrow()

        assertEquals("A lighter shape.", reply.reply)
        assertEquals(
            listOf(
                AssistantSuggestion.Move("a", "Review PR", "Sharp hour", Instant.parse("2026-11-09T03:30:00Z")),
                AssistantSuggestion.Split("b", "Ship it", "Big", listOf("One", "Two")),
                AssistantSuggestion.ChangePriority("c", "Pay bill", "Due", Priority.URGENT),
            ),
            reply.suggestions,
        )
        val request = server.takeRequest()
        assertEquals("/api/ai/assist", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Plan my day", body["message"]!!.jsonPrimitive.content)
        assertEquals("Asia/Kolkata", body["timeZone"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assist drops suggestions it cannot act on`() = runTest {
        server.enqueue(
            json(
                200,
                """{"reply":"ok","suggestions":[
                  {"kind":"MOVE","taskId":"a","taskTitle":"x","reason":""},
                  {"kind":"SPLIT","taskId":"a","taskTitle":"x","steps":[]},
                  {"kind":"PRIORITY","taskId":"a","taskTitle":"x","priority":"SOMETIMES"},
                  {"kind":"DELETE","taskId":"a","taskTitle":"x"},
                  {"kind":"PRIORITY","taskId":"a","taskTitle":"x","priority":"LOW"}]}""",
            ),
        )

        val reply = repository.assist("q", ZoneId.of("UTC")).getOrThrow()

        assertEquals(listOf<AssistantSuggestion>(AssistantSuggestion.ChangePriority("a", "x", "", Priority.LOW)), reply.suggestions)
    }

    @Test
    fun `assist without a key says how to fix it`() = runTest {
        server.enqueue(json(503, """{"error":"AI_NOT_CONFIGURED","message":"AI is not set up. Add XAI_API_KEY to backend/.env and restart the server."}"""))
        val error = repository.assist("q", ZoneId.of("UTC")).exceptionOrNull() as AiException
        assertTrue(error.notConfigured)
        assertTrue(error.message!!.contains("XAI_API_KEY"))
    }

    @Test
    fun `assist keeps the server's message when the model gave nothing usable`() = runTest {
        server.enqueue(json(502, """{"error":"AI_BAD_RESPONSE","message":"The AI did not have anything to say. Try rewording your question."}"""))
        val error = repository.assist("q", ZoneId.of("UTC")).exceptionOrNull() as AiException
        assertFalse(error.notConfigured)
        assertEquals("The AI did not have anything to say. Try rewording your question.", error.message)
    }

    @Test
    fun `assist rejected by validation gets a plain message`() = runTest {
        server.enqueue(json(400, """{"error":"VALIDATION_ERROR","message":"Request validation failed"}"""))
        assertEquals("The assistant couldn't read that request.", repository.assist("q", ZoneId.of("UTC")).exceptionOrNull()!!.message)
    }
}
