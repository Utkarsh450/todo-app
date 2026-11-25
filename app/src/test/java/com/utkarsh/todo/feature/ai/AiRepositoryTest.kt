package com.utkarsh.todo.feature.ai

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.ai.domain.AiException
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.ai.domain.MemoryFact
import com.utkarsh.todo.feature.ai.domain.StoredMessage
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
    fun `assist reads tasks, events, reminders and completions the assistant wants to create`() = runTest {
        server.enqueue(
            json(
                200,
                """{"reply":"ok","suggestions":[
                  {"kind":"CREATE_TASK","title":"Buy milk","reason":"r","dueAt":"2026-11-09T03:30:00.000Z","priority":"HIGH","repeatRule":"Every week","projectId":"p1","projectName":"Home"},
                  {"kind":"CREATE_TASK","title":"Think","reason":"","dueAt":null,"priority":"NORMAL","repeatRule":null,"projectId":null,"projectName":null},
                  {"kind":"CREATE_EVENT","title":"Dentist","reason":"","startAt":"2026-11-10T09:30:00.000Z","endAt":"2026-11-10T10:30:00.000Z","allDay":false},
                  {"kind":"CREATE_EVENT","title":"No end","reason":"","startAt":"2026-11-10T09:30:00.000Z","endAt":"2026-11-10T09:00:00.000Z","allDay":false},
                  {"kind":"CREATE_EVENT","title":"No start","reason":""},
                  {"kind":"CREATE_REMINDER","title":"Stretch","reason":"","remindAt":"2026-11-08T08:30:00.000Z","repeatRule":"Every day"},
                  {"kind":"CREATE_REMINDER","title":"No time","reason":""},
                  {"kind":"CREATE_TASK","title":"  ","reason":""},
                  {"kind":"COMPLETE","taskId":"a","taskTitle":"Pay bill","reason":""}]}""",
            ),
        )

        val s = repository.assist("q", ZoneId.of("UTC")).getOrThrow().suggestions

        assertEquals(
            listOf<AssistantSuggestion>(
                AssistantSuggestion.CreateTask("Buy milk", "r", Instant.parse("2026-11-09T03:30:00Z"), Priority.HIGH, "Every week", "p1", "Home"),
                AssistantSuggestion.CreateTask("Think", "", null, Priority.NORMAL, null, null, null),
                AssistantSuggestion.CreateEvent("Dentist", "", Instant.parse("2026-11-10T09:30:00Z"), Instant.parse("2026-11-10T10:30:00Z"), false),
                AssistantSuggestion.CreateEvent("No end", "", Instant.parse("2026-11-10T09:30:00Z"), Instant.parse("2026-11-10T10:30:00Z"), false),
                AssistantSuggestion.CreateReminder("Stretch", "", Instant.parse("2026-11-08T08:30:00Z"), "Every day"),
                AssistantSuggestion.Complete("a", "Pay bill", ""),
            ),
            s,
        )
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

    // ---- brain dump

    @Test
    fun `brain dump sends the note and time zone and maps every field`() = runTest {
        server.enqueue(
            json(
                200,
                """{"tasks":[
                  {"title":"Email Priya","dueAt":"2026-11-13T04:30:00.000Z","priority":"HIGH","repeatRule":"Every month","projectId":"p1","projectName":"Design"},
                  {"title":"Book flights","dueAt":null,"priority":"NORMAL","repeatRule":null,"projectId":null,"projectName":null}]}""",
            ),
        )

        val tasks = repository.brainDump("Okay so I need to email Priya", ZoneId.of("Asia/Kolkata")).getOrThrow()

        assertEquals(
            listOf(
                com.utkarsh.todo.feature.ai.domain.DumpedTask("Email Priya", Instant.parse("2026-11-13T04:30:00Z"), Priority.HIGH, "Every month", "p1", "Design"),
                com.utkarsh.todo.feature.ai.domain.DumpedTask("Book flights", null, Priority.NORMAL, null, null, null),
            ),
            tasks,
        )
        val request = server.takeRequest()
        assertEquals("/api/ai/braindump", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Okay so I need to email Priya", body["text"]!!.jsonPrimitive.content)
        assertEquals("Asia/Kolkata", body["timeZone"]!!.jsonPrimitive.content)
    }

    @Test
    fun `brain dump of a note with no tasks is an empty list`() = runTest {
        server.enqueue(json(200, """{"tasks":[]}"""))
        assertEquals(emptyList<com.utkarsh.todo.feature.ai.domain.DumpedTask>(), repository.brainDump("hmm", ZoneId.of("UTC")).getOrThrow())
    }

    @Test
    fun `brain dump without a key says how to fix it`() = runTest {
        server.enqueue(json(503, """{"error":"AI_NOT_CONFIGURED","message":"AI is not set up. Add XAI_API_KEY to backend/.env and restart the server."}"""))
        val error = repository.brainDump("x", ZoneId.of("UTC")).exceptionOrNull() as AiException
        assertTrue(error.notConfigured)
    }

    @Test
    fun `brain dump keeps the server's message when the AI answer was unusable`() = runTest {
        server.enqueue(json(502, """{"error":"AI_BAD_RESPONSE","message":"The AI sent a reply in a shape that could not be understood. Try again."}"""))
        assertEquals(
            "The AI sent a reply in a shape that could not be understood. Try again.",
            repository.brainDump("x", ZoneId.of("UTC")).exceptionOrNull()!!.message,
        )
    }

    // --- memory and history ------------------------------------------------------------------------------------------

    private val messageId = "11111111-1111-4111-8111-111111111111"
    private val factId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `assist reads the saved message id, the facts it noted and the ones it forgot`() = runTest {
        server.enqueue(
            json(
                200,
                """{"messageId":"$messageId","reply":"Noted.","suggestions":[],
                   "noted":[{"id":"$factId","text":"Wakes at 7","category":"ROUTINE"}],"forgotten":["33333333-3333-4333-8333-333333333333"]}""",
            ),
        )

        val reply = repository.assist("I wake at 7", ZoneId.of("UTC")).getOrThrow()

        assertEquals(messageId, reply.messageId)
        assertEquals(listOf(MemoryFact(factId, "Wakes at 7", "ROUTINE")), reply.noted)
        assertEquals(listOf("33333333-3333-4333-8333-333333333333"), reply.forgotten)
    }

    @Test
    fun `an answer from an older server without those fields still works`() = runTest {
        server.enqueue(json(200, """{"reply":"Hi","suggestions":[]}"""))
        val reply = repository.assist("hi", ZoneId.of("UTC")).getOrThrow()
        assertEquals(null, reply.messageId)
        assertEquals(emptyList<MemoryFact>(), reply.noted)
        assertEquals(emptyList<String>(), reply.forgotten)
    }

    @Test
    fun `suggestions keep the server's positions even when one cannot be read`() = runTest {
        server.enqueue(
            json(
                200,
                """{"messageId":"$messageId","reply":"ok","suggestions":[
                   {"kind":"MOVE","taskId":"t1","taskTitle":"A"},
                   {"kind":"COMPLETE","taskId":"t2","taskTitle":"B","reason":"done"},
                   {"kind":"CREATE_TASK","title":"C"}]}""",
            ),
        )

        val reply = repository.assist("q", ZoneId.of("UTC")).getOrThrow()

        assertEquals(2, reply.suggestions.size) // the move has no date, so it was dropped
        assertEquals(listOf(1, 2), reply.suggestionIndexes)
    }

    @Test
    fun `history parses messages newest first with their suggestions, outcomes and the cursor`() = runTest {
        server.enqueue(
            json(
                200,
                """{"messages":[
                     {"id":"$messageId","role":"ASSISTANT","content":"Done.","createdAt":"2026-11-25T10:00:00.000Z",
                      "suggestions":[{"kind":"COMPLETE","taskId":"t2","taskTitle":"B","reason":"done"}],"outcomes":{"0":"accepted"}},
                     {"id":"44444444-4444-4444-8444-444444444444","role":"USER","content":"Tick B","suggestions":null,"outcomes":null,"createdAt":"2026-11-25T09:59:00.000Z"}],
                   "nextBefore":"44444444-4444-4444-8444-444444444444"}""",
            ),
        )

        val page = repository.history(before = "55555555-5555-4555-8555-555555555555", limit = 2).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/ai/history", request.url.encodedPath)
        assertEquals("55555555-5555-4555-8555-555555555555", request.url.queryParameter("before"))
        assertEquals("2", request.url.queryParameter("limit"))
        assertEquals(listOf(false, true), page.messages.map { it.fromOwner })
        assertEquals("Done.", page.messages[0].text)
        assertEquals(1, page.messages[0].suggestions.size)
        assertEquals(mapOf(0 to "accepted"), page.messages[0].outcomes)
        assertEquals(emptyList<Int>(), page.messages[1].suggestionIndexes)
        assertEquals("44444444-4444-4444-8444-444444444444", page.nextBefore)
    }

    @Test
    fun `history of a first run is empty with no cursor`() = runTest {
        server.enqueue(json(200, """{"messages":[],"nextBefore":null}"""))
        val page = repository.history().getOrThrow()
        assertEquals(emptyList<StoredMessage>(), page.messages)
        assertEquals(null, page.nextBefore)
        assertEquals("20", server.takeRequest().url.queryParameter("limit"))
    }

    @Test
    fun `clearing history sends a DELETE`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertTrue(repository.clearHistory().isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/ai/history", request.url.encodedPath)
    }

    @Test
    fun `reporting an outcome sends the index and accepted or skipped`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        assertTrue(repository.reportOutcome(messageId, 2, accepted = true).isSuccess)
        assertTrue(repository.reportOutcome(messageId, 0, accepted = false).isSuccess)

        val first = server.takeRequest()
        assertEquals("/api/ai/assist/$messageId/outcome", first.url.encodedPath)
        val firstBody = Json.parseToJsonElement(first.body!!.utf8()).jsonObject
        assertEquals("2", firstBody["index"]!!.jsonPrimitive.content)
        assertEquals("accepted", firstBody["outcome"]!!.jsonPrimitive.content)
        assertEquals("skipped", Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["outcome"]!!.jsonPrimitive.content)
    }

    @Test
    fun `memory calls use the right verbs and paths`() = runTest {
        server.enqueue(json(200, """{"memories":[{"id":"$factId","text":"Wakes at 7","category":"ROUTINE","source":"AUTO","updatedAt":"2026-11-25T10:00:00.000Z"}]}"""))
        server.enqueue(json(201, """{"id":"$factId","text":"Likes tea","category":"OTHER"}"""))
        server.enqueue(json(200, """{"id":"$factId","text":"Likes green tea","category":"OTHER"}"""))
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(listOf(MemoryFact(factId, "Wakes at 7", "ROUTINE")), repository.memories().getOrThrow())
        assertEquals("Likes tea", repository.addMemory("Likes tea").getOrThrow().text)
        assertEquals("Likes green tea", repository.updateMemory(factId, "Likes green tea").getOrThrow().text)
        assertTrue(repository.deleteMemory(factId).isSuccess)
        assertTrue(repository.deleteAllMemories().isSuccess)

        val seen = List(5) { server.takeRequest() }.map { it.method to it.url.encodedPath }
        assertEquals(
            listOf(
                "GET" to "/api/ai/memories",
                "POST" to "/api/ai/memories",
                "PATCH" to "/api/ai/memories/$factId",
                "DELETE" to "/api/ai/memories/$factId",
                "DELETE" to "/api/ai/memories",
            ),
            seen,
        )
    }

    @Test
    fun `deleting a memory that is already gone says so with the notFound flag`() = runTest {
        server.enqueue(json(404, """{"error":"NOT_FOUND","message":"Memory not found"}"""))

        val error = repository.deleteMemory(factId).exceptionOrNull() as AiException

        assertTrue(error.notFound)
        assertFalse(error.notConfigured)
    }

    @Test
    fun `a refused memory becomes a failure with the server's own reason`() = runTest {
        server.enqueue(json(422, """{"error":"MEMORY_REFUSED","message":"I do not keep passwords, keys, tokens or card and ID numbers."}"""))

        val error = repository.addMemory("my password is x").exceptionOrNull() as AiException

        assertEquals("I do not keep passwords, keys, tokens or card and ID numbers.", error.message)
    }
}
