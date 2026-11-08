package com.utkarsh.todo.feature.braindump

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.braindump.ui.BrainDumpViewModel
import com.utkarsh.todo.feature.braindump.ui.DumpPhase
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
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
import kotlinx.serialization.json.JsonNull
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
class BrainDumpIntegrationTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-08T04:30:00Z"), zone)
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val taskJson = """
        {"id":"t","title":"t","notes":"","projectId":null,"priority":"NORMAL","status":"TODO","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-07T00:00:00.000Z","updatedAt":"2026-11-07T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request }
                    return when (request.url.encodedPath) {
                        "/api/ai/braindump" -> json(
                            200,
                            """{"tasks":[
                              {"title":"Email Priya the deck","dueAt":"2026-11-13T04:30:00.000Z","priority":"NORMAL","repeatRule":null,"projectId":"p-design","projectName":"Design"},
                              {"title":"Fix the rotation crash","dueAt":null,"priority":"URGENT","repeatRule":null,"projectId":null,"projectName":null},
                              {"title":"Renew the gym","dueAt":null,"priority":"NORMAL","repeatRule":"Every month","projectId":null,"projectName":null}]}""",
                        )
                        "/api/projects" -> json(200, """[{"id":"p-design","name":"Design","color":"#935EDF"}]""")
                        "/api/tasks" -> json(201, taskJson)
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

    private fun viewModel(): BrainDumpViewModel {
        val retrofit = buildRetrofit(server.url("/api/").toString())
        return BrainDumpViewModel(
            AiRepositoryImpl(retrofit.create<AiApi>()),
            TasksRepositoryImpl(retrofit.create<TasksApi>()),
            ProjectsRepositoryImpl(retrofit.create<ProjectsApi>()),
            clock,
        )
    }

    private fun sent() = synchronized(requests) { requests.toList() }

    private fun bodyOf(request: RecordedRequest) = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `sends the note and zone, and shows what comes back with the project colour`() = runBlocking {
        val vm = viewModel()
        vm.onTextChange("Okay so I need to email Priya...")

        vm.sort()
        val state = withTimeout(5000) { vm.uiState.first { it.phase == DumpPhase.RESULTS } }

        assertEquals(3, state.rows.size)
        assertEquals("#935EDF", state.rows[0].projectColorHex)
        val body = bodyOf(sent().single { it.url.encodedPath == "/api/ai/braindump" })
        assertEquals("Okay so I need to email Priya...", body.getValue("text").jsonPrimitive.content)
        assertEquals("Asia/Kolkata", body.getValue("timeZone").jsonPrimitive.content)
        assertTrue("no task is created before confirming", sent().none { it.method == "POST" && it.url.encodedPath == "/api/tasks" })
    }

    @Test
    fun `adding posts one task per ticked row with its time, priority, repeat and project`() = runBlocking {
        val vm = viewModel()
        vm.onTextChange("note")
        vm.sort()
        withTimeout(5000) { vm.uiState.first { it.phase == DumpPhase.RESULTS } }
        vm.toggle(1)

        vm.addSelected()
        withTimeout(5000) { vm.added.first() }

        val posts = sent().filter { it.method == "POST" && it.url.encodedPath == "/api/tasks" }.map(::bodyOf)
        assertEquals(listOf("Email Priya the deck", "Renew the gym"), posts.map { it.getValue("title").jsonPrimitive.content })
        assertEquals("2026-11-13T04:30:00Z", posts[0].getValue("dueAt").jsonPrimitive.content)
        assertEquals("p-design", posts[0].getValue("projectId").jsonPrimitive.content)
        assertEquals("Every month", posts[1].getValue("repeatRule").jsonPrimitive.content)
        assertTrue(posts[1].getValue("dueAt") is JsonNull)
    }
}
