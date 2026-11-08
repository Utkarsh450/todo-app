package com.utkarsh.todo.feature.focus

import androidx.lifecycle.SavedStateHandle
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.focus.ui.FocusPhase
import com.utkarsh.todo.feature.focus.ui.FocusViewModel
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.create

/** Focus on top of the real repository and Retrofit, with the real (system) clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusIntegrationTest {
    private lateinit var server: MockWebServer

    private fun task(status: String) = """
        {"id":"t1","title":"Write the report","notes":"","projectId":null,"priority":"NORMAL","status":"$status","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-01T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): FocusViewModel {
        val repository = TasksRepositoryImpl(buildRetrofit(server.url("/api/").toString()).create<TasksApi>())
        return FocusViewModel(SavedStateHandle(mapOf("taskId" to "t1")), repository, Clock.systemUTC())
    }

    @Test
    fun `shows the task title from the server, counts down in real time and can be paused`() = runBlocking {
        server.enqueue(json(task("TODO")))
        val vm = viewModel()
        withTimeout(5_000) { vm.uiState.first { it.taskTitle != null } }
        assertEquals("/api/tasks/t1", server.takeRequest().url.encodedPath)

        vm.start()
        val ticked = withTimeout(5_000) { vm.uiState.first { it.remainingSeconds < 25 * 60 } }
        assertEquals(FocusPhase.RUNNING, ticked.phase)

        vm.pause()
        val frozen = vm.uiState.value.remainingSeconds
        kotlinx.coroutines.delay(1_300)
        assertEquals(frozen, vm.uiState.value.remainingSeconds)
        assertEquals(FocusPhase.PAUSED, vm.uiState.value.phase)
    }

    @Test
    fun `marking the task done sends the new status to the server`() = runBlocking {
        server.enqueue(json(task("TODO")))
        val vm = viewModel()
        withTimeout(5_000) { vm.uiState.first { it.taskTitle != null } }
        server.takeRequest()

        server.enqueue(json(task("DONE")))
        vm.markTaskDone()
        withTimeout(5_000) { vm.uiState.first { it.taskDone } }

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("DONE", Json.parseToJsonElement(request.body!!.utf8()).jsonObject["status"]?.jsonPrimitive?.content)
    }
}
