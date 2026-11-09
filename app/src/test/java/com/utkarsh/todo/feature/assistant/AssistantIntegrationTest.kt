package com.utkarsh.todo.feature.assistant

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.ai.data.AiApi
import com.utkarsh.todo.feature.ai.data.AiRepositoryImpl
import com.utkarsh.todo.feature.assistant.ui.AssistantViewModel
import com.utkarsh.todo.feature.assistant.ui.SuggestionStatus
import com.utkarsh.todo.feature.calendar.data.CalendarApi
import com.utkarsh.todo.feature.calendar.data.CalendarRepositoryImpl
import com.utkarsh.todo.feature.reminders.data.RemindersApi
import com.utkarsh.todo.feature.reminders.data.RemindersRepositoryImpl
import com.utkarsh.todo.feature.reminders.domain.ReminderAlarmStore
import com.utkarsh.todo.feature.reminders.domain.ReminderScheduler
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
import io.mockk.mockk
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
    @Volatile private var assistReply = """{"reply":"A lighter shape.","suggestions":[
                              {"kind":"MOVE","taskId":"a","taskTitle":"Review PR","reason":"Sharp hour","dueAt":"2026-11-09T03:30:00.000Z"},
                              {"kind":"SPLIT","taskId":"b","taskTitle":"Ship it","reason":"Big","steps":["One","Two"]},
                              {"kind":"PRIORITY","taskId":"c","taskTitle":"Pay bill","reason":"Due","priority":"URGENT"}]}"""
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)

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
                        path == "/api/ai/assist" -> json(200, assistReply)
                        path == "/api/events" && request.method == "POST" -> json(201, """{"id":"e1","title":"Dentist"}""")
                        path == "/api/reminders" && request.method == "POST" -> {
                            val body = bodyOf(request)
                            val title = body.getValue("title").jsonPrimitive.content
                            val remindAt = body.getValue("remindAt").jsonPrimitive.content
                            json(201, """{"id":"r-$title","taskId":null,"title":"$title","remindAt":"$remindAt","repeatRule":null,"locationTrigger":null,"enabled":true,"snoozedUntil":null}""")
                        }
                        path == "/api/tasks" && request.method == "POST" -> json(201, taskJson("new"))
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
        val reminders = RemindersRepositoryImpl(retrofit.create<RemindersApi>(), scheduler, clock, mockk<ReminderAlarmStore>(relaxed = true))
        return AssistantViewModel(
            AiRepositoryImpl(retrofit.create<AiApi>()), TasksRepositoryImpl(retrofit.create<TasksApi>()),
            CalendarRepositoryImpl(retrofit.create<CalendarApi>(), reminders, clock), reminders, clock,
        )
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

    @Test
    fun `accepting new things posts a task, an event and a reminder, and rings the reminder`() = runBlocking {
        assistReply = """{"reply":"Added.","suggestions":[
          {"kind":"CREATE_TASK","title":"Buy milk","reason":"","dueAt":"2026-11-09T03:30:00.000Z","priority":"HIGH","repeatRule":"Every week","projectId":null,"projectName":null},
          {"kind":"CREATE_EVENT","title":"Dentist","reason":"","startAt":"2026-11-10T09:30:00.000Z","endAt":"2026-11-10T10:30:00.000Z","allDay":false},
          {"kind":"CREATE_REMINDER","title":"Stretch","reason":"","remindAt":"2026-11-08T08:30:00.000Z","repeatRule":"Every day"}]}"""
        val vm = viewModel()
        vm.ask("add things")
        val turn = withTimeout(5000) { vm.uiState.first { it.turns.isNotEmpty() && !it.turns.last().loading } }.turns.single()

        vm.acceptAll(turn.id)
        withTimeout(5000) { vm.uiState.first { s -> s.turns.single().suggestions.all { it.status == SuggestionStatus.ACCEPTED } } }

        val task = bodyOf(sent().single { it.url.encodedPath == "/api/tasks" && it.method == "POST" })
        assertEquals("Buy milk", task.getValue("title").jsonPrimitive.content)
        assertEquals("HIGH", task.getValue("priority").jsonPrimitive.content)
        assertEquals("Every week", task.getValue("repeatRule").jsonPrimitive.content)
        val event = bodyOf(sent().single { it.url.encodedPath == "/api/events" })
        assertEquals("2026-11-10T09:30:00Z", event.getValue("startAt").jsonPrimitive.content)
        assertEquals("2026-11-10T10:30:00Z", event.getValue("endAt").jsonPrimitive.content)
        val posted = sent().filter { it.url.encodedPath == "/api/reminders" }.map(::bodyOf)
        val reminder = posted.single { it.getValue("title").jsonPrimitive.content == "Stretch" }
        assertEquals("Every day", reminder.getValue("repeatRule").jsonPrimitive.content)
        val alert = posted.single { it.getValue("title").jsonPrimitive.content == "Dentist" }
        assertEquals("2026-11-10T09:00:00Z", alert.getValue("remindAt").jsonPrimitive.content) // 30 min before the event
        assertEquals("e1", alert.getValue("eventId").jsonPrimitive.content)
        io.mockk.verify(exactly = 1) { scheduler.schedule(match { it.title == "Stretch" }) }
    }
}
