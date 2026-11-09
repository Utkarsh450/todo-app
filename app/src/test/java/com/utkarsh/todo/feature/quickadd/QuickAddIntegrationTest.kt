package com.utkarsh.todo.feature.quickadd

import app.cash.turbine.test
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.calendar.data.CalendarApi
import com.utkarsh.todo.feature.calendar.data.CalendarRepositoryImpl
import com.utkarsh.todo.feature.notes.data.NotesApi
import com.utkarsh.todo.feature.notes.data.NotesRepositoryImpl
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.QuickAddType
import com.utkarsh.todo.feature.quickadd.ui.QuickAddViewModel
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.create

/**
 * The real view-model, parser, repositories and Retrofit stack talking to a fake HTTP server, so the actual
 * requests each quick-add type sends are checked, not just calls to mocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddIntegrationTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), zone) // Sat 7 Nov 2026, 10:00 IST

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var noteOnServer = "Ask Sam"
    private var failEvents = false

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/projects" -> json("""[{"id":"p-design","name":"Design","color":"#935EDF"}]""")
                        path == "/api/events" && failEvents -> json("""{"error":"INTERNAL_ERROR"}""", 500)
                        path == "/api/events" -> json("""{"id":"e1","title":"x"}""", 201)
                        path.startsWith("/api/notes/") && request.method == "PUT" -> {
                            noteOnServer = Json.parseToJsonElement(request.body!!.utf8()).jsonObject.getValue("text").jsonPrimitive.content
                            json("""{"date":"2026-11-07","text":${kotlinx.serialization.json.JsonPrimitive(noteOnServer)}}""")
                        }
                        path.startsWith("/api/notes/") -> json("""{"date":"2026-11-07","text":${kotlinx.serialization.json.JsonPrimitive(noteOnServer)}}""")
                        path == "/api/tasks" -> json(taskJson(), 201)
                        else -> json("{}", 404)
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

    private fun taskJson() = """
        {"id":"t1","title":"x","notes":"","projectId":null,"priority":"NORMAL","status":"TODO","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-07T00:00:00.000Z","updatedAt":"2026-11-07T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    private val alertReminders = io.mockk.mockk<com.utkarsh.todo.feature.reminders.domain.RemindersRepository>().also {
        io.mockk.coEvery { it.create(any()) } returns Result.failure(RuntimeException("not under test"))
    }
    private val retrofit by lazy { buildRetrofit(server.url("/api/").toString()) }
    private val notes by lazy { NotesRepositoryImpl(retrofit.create<NotesApi>()) }
    private val calendar by lazy { CalendarRepositoryImpl(retrofit.create<CalendarApi>(), alertReminders, clock) }

    /** Completes once the view-model's background project lookup has finished, so `#design` can be resolved. */
    private val projectsLoaded = CompletableDeferred<Unit>()

    private fun viewModel(): QuickAddViewModel {
        val realProjects = ProjectsRepositoryImpl(retrofit.create<ProjectsApi>())
        val signalling = object : ProjectsRepository by realProjects {
            override suspend fun projects() = realProjects.projects().also { projectsLoaded.complete(Unit) }
        }
        return QuickAddViewModel(TasksRepositoryImpl(retrofit.create<TasksApi>()), signalling, calendar, notes, QuickAddParser(clock), clock)
    }

    /** Real network calls finish on other threads, so wait (with a limit) instead of asserting straight away. */
    private suspend fun QuickAddViewModel.ready() = withTimeout(5_000) { projectsLoaded.await() }

    private suspend fun QuickAddViewModel.settled() = withTimeout(5_000) { uiState.first { !it.isSaving } }

    private fun body(request: RecordedRequest) = Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `a timed event reaches the server with the right times and project`() = runBlocking {
        val vm = viewModel()
        vm.ready()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Lunch with Sam fri 1pm #design")

        vm.added.test {
            vm.submit()
            awaitItem()
        }

        val post = requests.single { it.url.encodedPath == "/api/events" }
        val b = body(post)
        assertEquals("Lunch with Sam", b.getValue("title").jsonPrimitive.content)
        assertEquals("2026-11-13T07:30:00Z", b.getValue("startAt").jsonPrimitive.content)
        assertEquals("2026-11-13T08:30:00Z", b.getValue("endAt").jsonPrimitive.content)
        assertEquals("false", b.getValue("allDay").jsonPrimitive.content)
        assertEquals("p-design", b.getValue("projectId").jsonPrimitive.content)
    }

    @Test
    fun `an all-day event is sent as one`() = runBlocking {
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Team offsite tomorrow")

        vm.submit()
        vm.settled()

        val b = body(requests.single { it.url.encodedPath == "/api/events" })
        assertEquals("true", b.getValue("allDay").jsonPrimitive.content)
        assertEquals("2026-11-07T18:30:00Z", b.getValue("startAt").jsonPrimitive.content)
    }

    @Test
    fun `a note is added after the existing text and the existing text survives`() = runBlocking {
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.NOTE)
        vm.onTextChange("Buy milk tomorrow #errands")

        notes.changes.test {
            vm.submit()
            awaitItem() // the open scratchpad is told to reload
        }

        assertEquals("Ask Sam\nBuy milk tomorrow #errands", noteOnServer)
        val noteCalls = requests.filter { it.url.encodedPath.startsWith("/api/notes/") }
        assertEquals(listOf("GET", "PUT"), noteCalls.map { it.method }) // read first, so nothing is overwritten blind
        assertEquals("/api/notes/2026-11-07", noteCalls.first().url.encodedPath)
        assertTrue(requests.none { it.url.encodedPath == "/api/tasks" || it.url.encodedPath == "/api/events" })
    }

    @Test
    fun `a task still goes to the tasks endpoint`() = runBlocking {
        val vm = viewModel()
        vm.onTextChange("Buy oat milk tomorrow 9am !high")

        vm.submit()
        vm.settled()

        val post = requests.single { it.url.encodedPath == "/api/tasks" }
        assertEquals("Buy oat milk", body(post).getValue("title").jsonPrimitive.content)
        assertEquals("HIGH", body(post).getValue("priority").jsonPrimitive.content)
        assertTrue(requests.none { it.url.encodedPath == "/api/events" || it.url.encodedPath.startsWith("/api/notes/") })
    }

    @Test
    fun `a server error leaves the sheet as it was so nothing typed is lost`() = runBlocking {
        failEvents = true
        val vm = viewModel()
        vm.onTypeChange(QuickAddType.EVENT)
        vm.onTextChange("Dentist tomorrow 9am")

        vm.submit()
        vm.settled()

        assertTrue(vm.uiState.value.error != null)
        assertEquals("Dentist tomorrow 9am", vm.uiState.value.text)
        assertEquals(QuickAddType.EVENT, vm.uiState.value.type)
        assertFalse(vm.uiState.value.isSaving)
    }
}
