package com.utkarsh.todo.feature.ai

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.ai.domain.AiException
import java.time.Instant
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
}
