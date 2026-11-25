package com.utkarsh.todo.e2e

import com.utkarsh.todo.feature.focus.domain.FocusAlarm
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.core.network.buildRetrofit
import com.utkarsh.todo.feature.focus.ui.FocusScreen
import com.utkarsh.todo.feature.focus.ui.FocusViewModel
import com.utkarsh.todo.feature.projects.data.ProjectsApi
import com.utkarsh.todo.feature.projects.data.ProjectsRepositoryImpl
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.ui.ProjectsScreen
import com.utkarsh.todo.feature.projects.ui.ProjectsViewModel
import com.utkarsh.todo.feature.search.ui.SearchScreen
import com.utkarsh.todo.feature.search.ui.SearchViewModel
import com.utkarsh.todo.feature.tasks.data.TasksApi
import com.utkarsh.todo.feature.tasks.data.TasksRepositoryImpl
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Task
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.create

/**
 * The real screens and view-models, the real repositories, Retrofit and JSON, talking to a real running backend.
 *
 * Start a backend first, for example against the test database (from `backend/`):
 *   `set -a; . ./.env.test; set +a; PORT=3001 HOST=0.0.0.0 pnpm exec tsx src/server.ts`
 * The emulator reaches it at http://10.0.2.2:3001/api/ (override with `-Pandroid.testInstrumentationRunnerArguments.e2eBaseUrl=...`).
 * Without a reachable backend every test here is skipped. Each test makes uniquely named data and removes only that.
 */
class EndToEndTest {
    @get:Rule
    val compose = createComposeRule()

    private val baseUrl: String = InstrumentationRegistry.getArguments().getString("e2eBaseUrl") ?: "http://10.0.2.2:3001/api/"
    private val retrofit by lazy { buildRetrofit(baseUrl) }
    private val projects by lazy { ProjectsRepositoryImpl(retrofit.create<ProjectsApi>()) }
    private val tasks by lazy { TasksRepositoryImpl(retrofit.create<TasksApi>()) }
    private val clock = Clock.systemDefaultZone()

    private val token = "e2e${System.nanoTime().toString().takeLast(8)}"
    private lateinit var project: Project
    private lateinit var task: Task

    @Before
    fun setUp() {
        assumeTrue("No backend at $baseUrl: start one to run the end-to-end tests", backendIsUp())
        project = runBlocking { projects.create("P $token", "#2FA465").getOrThrow() }
        task = runBlocking {
            tasks.create(NewTask(title = "Task $token", projectId = project.id, dueAt = Instant.now().plus(1, ChronoUnit.DAYS))).getOrThrow()
        }
    }

    @After
    fun tearDown() {
        if (!::project.isInitialized) return
        runBlocking {
            if (::task.isInitialized) tasks.delete(task.id)
            projects.delete(project.id)
        }
    }

    private fun backendIsUp(): Boolean = runCatching {
        (URL(baseUrl + "health").openConnection() as HttpURLConnection).run {
            connectTimeout = 2_000
            readTimeout = 2_000
            responseCode == 200
        }
    }.getOrDefault(false)

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun serverTask(): Task = runBlocking { tasks.get(task.id).getOrThrow() }

    private fun serverProjects(): List<Project> = runBlocking { projects.projects().getOrThrow() }

    @Test
    fun projects_screen_shows_real_data_and_ticking_a_task_reaches_the_server() {
        val vm = ProjectsViewModel(projects, tasks, clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                ProjectsScreen(
                    state, {}, vm::toggleExpanded, vm::toggleTask, {}, vm::openNew, vm::openRename, vm::askDelete,
                    vm::onEditorName, vm::onEditorColor, vm::saveEditor, vm::closeEditor, vm::confirmDelete, vm::cancelDelete, { vm.load() }, vm::dismissMessage,
                )
            }
        }
        waitFor(hasTestTag("project-${project.id}"))
        compose.onNodeWithText("0/1").assertExists()

        compose.onNodeWithText("P $token").performClick()
        compose.onNodeWithContentDescription("Complete Task $token").performClick()
        compose.waitUntil(10_000) { serverTask().isDone }
        waitFor(hasText("1/1"))
        assertTrue(serverTask().completedAt != null)
    }

    @Test
    fun a_project_can_be_renamed_and_deleted_from_the_screen_and_the_server_agrees() {
        val vm = ProjectsViewModel(projects, tasks, clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                ProjectsScreen(
                    state, {}, vm::toggleExpanded, vm::toggleTask, {}, vm::openNew, vm::openRename, vm::askDelete,
                    vm::onEditorName, vm::onEditorColor, vm::saveEditor, vm::closeEditor, vm::confirmDelete, vm::cancelDelete, { vm.load() }, vm::dismissMessage,
                )
            }
        }
        waitFor(hasTestTag("project-${project.id}"))
        compose.onNodeWithText("P $token").performClick()

        compose.onNodeWithTag("rename-${project.id}").performClick()
        compose.onNodeWithTag("project-name").performTextReplacement("Renamed $token")
        compose.onNodeWithTag("project-save").performClick()
        compose.waitUntil(10_000) { serverProjects().any { it.id == project.id && it.name == "Renamed $token" } }
        waitFor(hasText("Renamed $token"))

        compose.onNodeWithTag("delete-${project.id}").performClick()
        compose.onNodeWithTag("confirm-delete-project").performClick()
        compose.waitUntil(10_000) { serverProjects().none { it.id == project.id } }
        // Deleting a project keeps its tasks, just without the project.
        assertEquals(null, serverTask().projectId)
    }

    @Test
    fun a_new_project_made_on_screen_exists_on_the_server() {
        val vm = ProjectsViewModel(projects, tasks, clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                ProjectsScreen(
                    state, {}, vm::toggleExpanded, vm::toggleTask, {}, vm::openNew, vm::openRename, vm::askDelete,
                    vm::onEditorName, vm::onEditorColor, vm::saveEditor, vm::closeEditor, vm::confirmDelete, vm::cancelDelete, { vm.load() }, vm::dismissMessage,
                )
            }
        }
        waitFor(hasTestTag("new-project"))
        compose.onNodeWithTag("new-project").performClick()
        compose.onNodeWithTag("project-name").performTextInput("Made $token")
        compose.onNodeWithTag("project-save").performClick()
        compose.waitUntil(10_000) { serverProjects().any { it.name == "Made $token" } }
        val made = serverProjects().first { it.name == "Made $token" }
        runBlocking { projects.delete(made.id) }
    }

    @Test
    fun search_finds_the_task_by_a_word_in_its_title() {
        val vm = SearchViewModel(tasks, projects, clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                SearchScreen(state, {}, vm::onQueryChange, vm::clear, vm::retry, {})
            }
        }
        compose.onNodeWithTag("search-input").performTextInput(token)
        waitFor(hasTestTag("result-${task.id}"))
        compose.onNodeWithText("Task $token").assertExists()
        compose.onNodeWithText("P ${token}".uppercase()).assertExists() // the project tag, from the real project list

        compose.onNodeWithTag("search-input").performTextReplacement("zzzz$token")
        waitFor(hasTestTag("search-empty"))
    }

    @Test
    fun focus_marks_the_task_done_on_the_server() {
        val vm = FocusViewModel(SavedStateHandle(mapOf("taskId" to task.id)), tasks, clock, object : FocusAlarm { override fun schedule(at: Instant, title: String) = Unit; override fun cancel() = Unit })
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                FocusScreen(state, {}, vm::setMinutes, vm::start, vm::pause, vm::reset, vm::markTaskDone)
            }
        }
        waitFor(hasText("Task $token"))
        assertFalse(serverTask().isDone)
        compose.onNodeWithTag("focus-done").performClick()
        compose.waitUntil(10_000) { serverTask().isDone }
        waitFor(hasTestTag("focus-task-done"))
        assertNotNull(serverTask().completedAt)
    }
}
