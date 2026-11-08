package com.utkarsh.todo.feature.assistant

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.assistant.ui.AssistantViewModel
import com.utkarsh.todo.feature.assistant.ui.SuggestionStatus
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

/** The real view-model and repositories over real Retrofit, against a fake backend. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantIntegrationTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-08T04:30:00Z"), zone)
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun taskJson(id: String) = """
        {"id":"$id","title":"t","notes":"","projectId":null,"priority":"NORMAL","status":"TODO","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-07T00:00:00.000Z","updatedAt":"2026-11-07T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request }
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/ai/assist" -> json(
                            200,
                            """{"reply":"A lighter shape.","suggestions":[
                              {"kind":"MOVE","taskId":"a","taskTitle":"Review PR","reason":"Sharp hour","dueAt":"2026-11-09T03:30:00.000Z"},
                              {"kind":"SPLIT","taskId":"b","taskTitle":"Ship it","reason":"Big","steps":["One","Two"]},
                              {"kind":"PRIORITY","taskId":"c","taskTitle":"Pay bill","reason":"Due","priority":"URGENT"}]}""",
                        )
                        path.endsWith("/subtasks") -> {
                            val title = Json.parseToJsonElement(request.body!!.utf8()).jsonObject.getValue("title").jsonPrimitive.content
                            json(201, """{"id":"s-$title","taskId":"b","title":"$title","done":false,"position":0}""")
                        }
                        path.startsWith("/api/tasks/") && request.method == "PATCH" -> json(200, taskJson(path.substringAfterLast('/')))
                        else -> json(404, "{}")
                    }
                }
            }
            start()
        }
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): AssistantViewModel {
        val retrofit = buildRetrofit(server.url("/api/").toString())
        return AssistantViewModel(AiRepositoryImpl(retrofit.create<AiApi>()), TasksRepositoryImpl(retrofit.create<TasksApi>()), clock)
    }

    private fun sent() = synchronized(requests) { requests.toList() }

    private fun bodyOf(request: RecordedRequest) = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `asks the backend with the question and time zone and shows what comes back`() = runBlocking {
        val vm = viewModel()

        vm.ask("Plan my day")
        val turn = withTimeout(5000) { vm.uiState.first { it.turns.isNotEmpty() && !it.turns.last().loading } }.turns.single()

        assertEquals("A lighter shape.", turn.reply)
        assertEquals(3, turn.suggestions.size)
        val body = bodyOf(sent().single { it.url.encodedPath == "/api/ai/assist" })
        assertEquals("Plan my day", body.getValue("message").jsonPrimitive.content)
        assertEquals("Asia/Kolkata", body.getValue("timeZone").jsonPrimitive.content)
        assertTrue("nothing changes before accepting", sent().none { it.method == "PATCH" || it.url.encodedPath.endsWith("/subtasks") })
    }

    @Test
    fun `accepting sends exactly the matching task requests`() = runBlocking {
        val vm = viewModel()
        vm.ask("Plan my day")
        val turn = withTimeout(5000) { vm.uiState.first { it.turns.isNotEmpty() && !it.turns.last().loading } }.turns.single()

        vm.acceptAll(turn.id)
        withTimeout(5000) { vm.uiState.first { s -> s.turns.single().suggestions.all { it.status == SuggestionStatus.ACCEPTED } } }

        val patches = sent().filter { it.method == "PATCH" }
        assertEquals(listOf("/api/tasks/a", "/api/tasks/c"), patches.map { it.url.encodedPath })
        assertEquals("2026-11-09T03:30:00Z", bodyOf(patches[0]).getValue("dueAt").jsonPrimitive.content)
        assertEquals(setOf("dueAt"), bodyOf(patches[0]).keys)
        assertEquals("URGENT", bodyOf(patches[1]).getValue("priority").jsonPrimitive.content)
        assertEquals(setOf("priority"), bodyOf(patches[1]).keys)
        val subtasks = sent().filter { it.url.encodedPath == "/api/tasks/b/subtasks" }.map { bodyOf(it).getValue("title").jsonPrimitive.content }
        assertEquals(listOf("One", "Two"), subtasks)
    }
}
