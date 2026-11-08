package com.utkarsh.todo.feature.projects

import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
import com.utkarsh.todo.feature.projects.ui.ProjectsViewModel
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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.create

/** The Projects view-model on top of the real repositories, Retrofit and JSON, talking to a fake server. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsIntegrationTest {
    private lateinit var server: MockWebServer
    private val clock = Clock.fixed(Instant.parse("2026-11-07T05:30:00Z"), ZoneId.of("Asia/Kolkata"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun project(id: String, name: String, color: String = "#935EDF") =
        """{"id":"$id","name":"$name","color":"$color","createdAt":"2026-11-01T00:00:00.000Z"}"""

    private fun task(id: String, project: String, status: String = "TODO") = """
        {"id":"$id","title":"Task $id","notes":"","projectId":"$project","priority":"NORMAL","status":"$status","dueAt":null,"completedAt":null,
         "archived":false,"pinned":false,"repeatRule":null,"createdAt":"2026-11-01T00:00:00.000Z","updatedAt":"2026-11-01T00:00:00.000Z","subtasks":[]}
    """.trimIndent()

    private fun viewModel(): ProjectsViewModel {
        val retrofit = buildRetrofit(server.url("/api/").toString())
        return ProjectsViewModel(ProjectsRepositoryImpl(retrofit.create<ProjectsApi>()), TasksRepositoryImpl(retrofit.create<TasksApi>()), clock)
    }

    private suspend fun ProjectsViewModel.loaded() = withTimeout(5_000) { uiState.first { !it.isLoading } }

    @Test
    fun `loads projects and their tasks from the server`() = runBlocking {
        server.enqueue(json("[${project("p1", "Design")},${project("p2", "Home", "#2784D5")}]"))
        server.enqueue(json("[${task("a", "p1", "DONE")},${task("b", "p1")},${task("c", "p2")}]"))

        val state = viewModel().loaded()

        assertEquals(listOf("Design", "Home"), state.rows.map { it.name })
        assertEquals(1, state.rows[0].done)
        assertEquals(2, state.rows[0].total)
        assertEquals("/api/projects", server.takeRequest().url.encodedPath)
        assertEquals("/api/tasks", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `make, rename and delete a project end to end`() = runBlocking {
        server.enqueue(json("[]"))
        server.enqueue(json("[]"))
        val vm = viewModel()
        vm.loaded()
        server.takeRequest(); server.takeRequest()

        server.enqueue(json(project("p9", "Art", "#DF911A"), 201))
        vm.openNew()
        vm.onEditorName("Art")
        vm.onEditorColor("#DF911A")
        vm.saveEditor()
        withTimeout(5_000) { vm.uiState.first { it.editor == null && it.rows.size == 1 } }
        val created = server.takeRequest()
        assertEquals("POST", created.method)
        assertEquals("""{"name":"Art","color":"#DF911A"}""", created.body?.utf8())

        server.enqueue(json(project("p9", "Art & Craft", "#DF911A")))
        vm.openRename("p9")
        vm.onEditorName("Art & Craft")
        vm.saveEditor()
        withTimeout(5_000) { vm.uiState.first { it.editor == null && it.rows.first().name == "Art & Craft" } }
        val patched = server.takeRequest()
        assertEquals("PATCH", patched.method)
        assertEquals("/api/projects/p9", patched.url.encodedPath)

        server.enqueue(MockResponse.Builder().code(204).build())
        vm.askDelete("p9")
        vm.confirmDelete()
        withTimeout(5_000) { vm.uiState.first { it.rows.isEmpty() } }
        assertEquals("DELETE", server.takeRequest().method)
        assertNull(vm.uiState.value.confirmDelete)
    }

    @Test
    fun `ticking a task sends the completion and updates the progress`() = runBlocking {
        server.enqueue(json("[${project("p1", "Design")}]"))
        server.enqueue(json("[${task("b", "p1")}]"))
        val vm = viewModel()
        vm.loaded()
        server.takeRequest(); server.takeRequest()

        server.enqueue(json(task("b", "p1", "DONE")))
        // A successful write makes the repository announce a change, so the screen quietly reloads afterwards.
        server.enqueue(json("[${project("p1", "Design")}]"))
        server.enqueue(json("[${task("b", "p1", "DONE")}]"))
        vm.toggleTask("b")
        val state = withTimeout(5_000) { vm.uiState.first { it.rows.first().done == 1 } }

        assertEquals(1f, state.rows.first().progress, 0f)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/tasks/b", request.url.encodedPath)
        assertEquals("DONE", Json.parseToJsonElement(request.body!!.utf8()).jsonObject["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a server error on delete leaves the project in the list`() = runBlocking {
        server.enqueue(json("[${project("p1", "Design")}]"))
        server.enqueue(json("[]"))
        val vm = viewModel()
        vm.loaded()

        server.enqueue(json("""{"error":"INTERNAL"}""", 500))
        vm.askDelete("p1")
        vm.confirmDelete()
        val state = withTimeout(5_000) { vm.uiState.first { it.message != null } }

        assertEquals(1, state.rows.size)
        assertEquals("Couldn't delete the project", state.message)
    }
}
