package com.utkarsh.todo.feature.breakdown

import androidx.lifecycle.SavedStateHandle
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.breakdown.ui.BreakdownPhase
import com.utkarsh.todo.feature.breakdown.ui.BreakdownViewModel
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
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

/**
 * The real view-model and repositories over real Retrofit, against a fake backend: checks what is actually sent
 * to /ai/breakdown and which subtask requests follow, in order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BreakdownIntegrationTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var aiResponse: MockResponse = json(200, """{"subtasks":["Book flights","Find a hotel","Pack"]}""")
    private var failSubtaskTitle: String? = null

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val taskJson = """
        {"id":"t1","title":"Plan trip","notes":"Goa, 4 days","projectId":null,"priority":"NORMAL","status":"TODO","dueAt":"2026-11-10T09:00:00.000Z",
         "completedAt":null,"archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-07T00:00:00.000Z","updatedAt":"2026-11-07T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request }
                    return when (request.url.encodedPath) {
                        "/api/tasks/t1" -> json(200, taskJson)
                        "/api/ai/breakdown" -> aiResponse
                        "/api/tasks/t1/subtasks" -> {
                            val title = Json.parseToJsonElement(request.body!!.utf8()).jsonObject.getValue("title").jsonPrimitive.content
                            if (title == failSubtaskTitle) json(500, """{"error":"INTERNAL_ERROR"}""")
                            else json(201, """{"id":"s-$title","taskId":"t1","title":"$title","done":false,"position":0}""")
                        }
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

    private fun viewModel(): BreakdownViewModel {
        val retrofit = buildRetrofit(server.url("/api/").toString())
        return BreakdownViewModel(
            SavedStateHandle(mapOf("taskId" to "t1")),
            TasksRepositoryImpl(retrofit.create<TasksApi>()),
            AiRepositoryImpl(retrofit.create<AiApi>()),
        )
    }

    private fun paths() = synchronized(requests) { requests.map { "${it.method} ${it.url.encodedPath}" } }

    private fun bodyOf(path: String) =
        Json.parseToJsonElement(synchronized(requests) { requests.first { it.url.encodedPath == path } }.body!!.utf8()).jsonObject

    @Test
    fun `sends the real task to the assistant and shows its steps`() = runBlocking {
        val vm = viewModel()

        val state = withTimeout(5000) { vm.uiState.first { it.phase != BreakdownPhase.LOADING } }

        assertEquals(BreakdownPhase.READY, state.phase)
        assertEquals(listOf("Book flights", "Find a hotel", "Pack"), state.suggestions.map { it.title })
        val body = bodyOf("/api/ai/breakdown")
        assertEquals("Plan trip", body.getValue("title").jsonPrimitive.content)
        assertEquals("Goa, 4 days", body.getValue("notes").jsonPrimitive.content)
        assertEquals("2026-11-10T09:00:00Z", body.getValue("dueAt").jsonPrimitive.content)
        assertTrue("no subtask is created before confirming", paths().none { it.endsWith("/subtasks") })
    }

    @Test
    fun `adding creates one subtask per ticked step, in order`() = runBlocking {
        val vm = viewModel()
        withTimeout(5000) { vm.uiState.first { it.phase == BreakdownPhase.READY } }
        vm.toggle(1)

        vm.addSelected()
        withTimeout(5000) { vm.added.first() }

        val posts = paths().filter { it == "POST /api/tasks/t1/subtasks" }
        assertEquals(2, posts.size)
        val titles = synchronized(requests) { requests.filter { it.url.encodedPath.endsWith("/subtasks") } }
            .map { Json.parseToJsonElement(it.body!!.utf8()).jsonObject.getValue("title").jsonPrimitive.content }
        assertEquals(listOf("Book flights", "Pack"), titles)
    }

    @Test
    fun `a server without a key leads to the setup state`() = runBlocking {
        aiResponse = json(503, """{"error":"AI_NOT_CONFIGURED","message":"AI is not set up. Add XAI_API_KEY to backend/.env and restart the server."}""")
        val vm = viewModel()

        val state = withTimeout(5000) { vm.uiState.first { it.phase != BreakdownPhase.LOADING } }

        assertEquals(BreakdownPhase.NOT_CONFIGURED, state.phase)
        assertTrue(state.message!!.contains("XAI_API_KEY"))
    }

    @Test
    fun `a failing subtask request leaves the rest to retry`() = runBlocking {
        failSubtaskTitle = "Find a hotel"
        val vm = viewModel()
        withTimeout(5000) { vm.uiState.first { it.phase == BreakdownPhase.READY } }

        vm.addSelected()
        val state = withTimeout(5000) { vm.uiState.first { !it.adding && it.message != null } }

        assertEquals(listOf("Find a hotel", "Pack"), state.suggestions.map { it.title })
        assertEquals("Added 1 of 3. Couldn't add the rest.", state.message)
    }
}
