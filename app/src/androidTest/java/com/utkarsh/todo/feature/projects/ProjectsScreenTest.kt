package com.utkarsh.todo.feature.projects

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.projects.domain.ProjectsRepository
import com.utkarsh.todo.feature.projects.ui.ProjectsScreen
import com.utkarsh.todo.feature.projects.ui.ProjectsUiState
import com.utkarsh.todo.feature.projects.ui.ProjectsViewModel
import com.utkarsh.todo.feature.tasks.domain.NewTask
import com.utkarsh.todo.feature.tasks.domain.Priority
import com.utkarsh.todo.feature.tasks.domain.Subtask
import com.utkarsh.todo.feature.tasks.domain.Task
import com.utkarsh.todo.feature.tasks.domain.TaskPatch
import com.utkarsh.todo.feature.tasks.domain.TaskStatus
import com.utkarsh.todo.feature.tasks.domain.TasksRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Projects screen, driven by the real view-model over in-memory repositories. */
class ProjectsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-07T05:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private fun task(id: String, project: String?, done: Boolean = false) = Task(
        id, "Task $id", "", project, Priority.NORMAL, if (done) TaskStatus.DONE else TaskStatus.TODO, null, null, false, false, null, emptyList(),
    )

    private val projectList = mutableListOf(Project("p1", "Design", "#935EDF"), Project("p2", "Home", "#2784D5"))
    private val taskList = mutableListOf(task("a", "p1", done = true), task("b", "p1"), task("c", "p2"))
    private var failDelete = false
    private var failLoad = false
    private val opened = mutableListOf<String>()

    private val projects = object : ProjectsRepository {
        override suspend fun projects() = if (failLoad) Result.failure(RuntimeException("Server is down")) else Result.success(projectList.toList())
        override suspend fun create(name: String, colorHex: String): Result<Project> =
            Result.success(Project("p${projectList.size + 1}", name, colorHex).also { projectList += it })
        override suspend fun update(id: String, name: String?, colorHex: String?): Result<Project> {
            val index = projectList.indexOfFirst { it.id == id }
            val updated = projectList[index].let { it.copy(name = name ?: it.name, colorHex = colorHex ?: it.colorHex) }
            projectList[index] = updated
            return Result.success(updated)
        }
        override suspend fun delete(id: String): Result<Unit> {
            if (failDelete) return Result.failure(RuntimeException("x"))
            projectList.removeAll { it.id == id }
            return Result.success(Unit)
        }
    }

    private val tasks = object : TasksRepository {
        override val changes: Flow<Unit> = MutableSharedFlow()
        override suspend fun all() = Result.success(taskList.toList())
        override suspend fun setDone(id: String, done: Boolean): Result<Task> {
            val index = taskList.indexOfFirst { it.id == id }
            taskList[index] = taskList[index].copy(status = if (done) TaskStatus.DONE else TaskStatus.TODO)
            return Result.success(taskList[index])
        }
        override suspend fun dueBetween(from: Instant, to: Instant): Result<List<Task>> = error("unused")
        override suspend fun search(query: String): Result<List<Task>> = error("unused")
        override suspend fun get(id: String): Result<Task> = error("unused")
        override suspend fun create(task: NewTask): Result<Task> = error("unused")
        override suspend fun update(id: String, patch: TaskPatch): Result<Task> = error("unused")
        override suspend fun delete(id: String): Result<Unit> = error("unused")
        override suspend fun addSubtask(taskId: String, title: String): Result<Subtask> = error("unused")
        override suspend fun setSubtaskDone(id: String, done: Boolean): Result<Subtask> = error("unused")
    }

    private var backed = 0

    private fun show() {
        val vm = ProjectsViewModel(projects, tasks, clock)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                ProjectsScreen(
                    state = state, onBack = { backed++ }, onToggleExpanded = vm::toggleExpanded, onToggleTask = vm::toggleTask,
                    onOpenTask = { opened += it }, onNew = vm::openNew, onRename = vm::openRename, onAskDelete = vm::askDelete,
                    onEditorName = vm::onEditorName, onEditorColor = vm::onEditorColor, onEditorSave = vm::saveEditor, onEditorClose = vm::closeEditor,
                    onConfirmDelete = vm::confirmDelete, onCancelDelete = vm::cancelDelete, onRetry = { vm.load() }, onDismissMessage = vm::dismissMessage,
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("projects-skeleton")).fetchSemanticsNodes().isEmpty() }
    }

    private fun showState(state: ProjectsUiState) {
        compose.setContent {
            PaperTheme {
                ProjectsScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun shows_a_skeleton_not_a_spinner_while_loading() {
        showState(ProjectsUiState(isLoading = true))
        compose.onNodeWithTag("projects-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading projects").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
    }

    @Test
    fun lists_each_project_with_its_progress_and_a_summary() {
        show()
        compose.onNodeWithTag("project-p1").assertIsDisplayed()
        compose.onNodeWithTag("project-p2").assertIsDisplayed()
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("0/1").assertIsDisplayed()
        compose.onNodeWithText("2 PROJECTS · 1 OF 3 TASKS DONE").assertIsDisplayed()
    }

    @Test
    fun tapping_a_project_opens_its_tasks_and_tapping_again_closes_them() {
        show()
        compose.onNodeWithTag("project-task-b").assertDoesNotExist()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("project-task-a").assertIsDisplayed()
        compose.onNodeWithTag("project-task-b").assertIsDisplayed()
        compose.onNodeWithTag("project-task-c").assertDoesNotExist()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("project-task-b").assertDoesNotExist()
    }

    @Test
    fun ticking_a_task_moves_the_progress_without_leaving_the_screen() {
        show()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithContentDescription("Complete Task b").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("2/2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 PROJECTS · 2 OF 3 TASKS DONE").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reopen Task b").assertIsDisplayed()
    }

    @Test
    fun tapping_a_task_title_opens_the_task() {
        show()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Task c").performClick()
        assertEquals(listOf("c"), opened)
    }

    @Test
    fun an_empty_project_says_how_to_fill_it() {
        taskList.clear()
        show()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("No tasks yet. Add one with + and #Home.").assertIsDisplayed()
    }

    @Test
    fun making_a_project_needs_a_name_then_it_appears_and_opens() {
        show()
        compose.onNodeWithTag("new-project").performClick()
        compose.onNodeWithTag("project-save").assertIsNotEnabled()
        compose.onNodeWithTag("project-name").performTextInput("Garden")
        compose.onNodeWithTag("colour-#2FA465").performClick()
        compose.onNodeWithTag("project-save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("project-p3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Garden").assertIsDisplayed()
        compose.onNodeWithText("3 PROJECTS · 1 OF 3 TASKS DONE").assertIsDisplayed()
        assertEquals("#2FA465", projectList.last().colorHex)
        compose.onNodeWithTag("project-name").assertDoesNotExist() // the sheet closed
    }

    @Test
    fun a_name_that_is_already_taken_is_refused_with_a_reason() {
        show()
        compose.onNodeWithTag("new-project").performClick()
        compose.onNodeWithTag("project-name").performTextInput("home")
        compose.onNodeWithTag("project-save").performClick()
        compose.onNodeWithTag("project-error").assertIsDisplayed()
        compose.onNodeWithText("A project with that name already exists").assertIsDisplayed()
        assertEquals(2, projectList.size)
    }

    @Test
    fun renaming_a_project_changes_its_name_and_keeps_its_progress() {
        show()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("rename-p1").performClick()
        compose.onNodeWithTag("project-name").performTextReplacement("Design 2")
        compose.onNodeWithTag("project-save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Design 2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        assertEquals("Design 2", projectList.first { it.id == "p1" }.name)
    }

    @Test
    fun deleting_asks_first_and_cancel_keeps_the_project() {
        show()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("delete-p1").performClick()
        compose.onNodeWithText("Delete Design?").assertIsDisplayed()
        compose.onNodeWithTag("cancel-delete-project").performClick()
        compose.onNodeWithText("Delete Design?").assertDoesNotExist()
        compose.onNodeWithTag("project-p1").assertIsDisplayed()
        assertEquals(2, projectList.size)
    }

    @Test
    fun confirming_a_delete_removes_the_project() {
        show()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("delete-p1").performClick()
        compose.onNodeWithTag("confirm-delete-project").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("project-p1")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("project-p2").assertIsDisplayed()
        assertEquals(listOf("p2"), projectList.map { it.id })
    }

    @Test
    fun a_failed_delete_keeps_the_project_and_shows_a_message() {
        failDelete = true
        show()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithTag("delete-p1").performClick()
        compose.onNodeWithTag("confirm-delete-project").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("projects-message")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Couldn't delete the project").assertIsDisplayed()
        compose.onNodeWithTag("project-p1").assertIsDisplayed()
    }

    @Test
    fun with_no_projects_it_invites_you_to_make_one() {
        projectList.clear()
        show()
        compose.onNodeWithTag("projects-empty").assertIsDisplayed()
        compose.onNodeWithText("Make the first one").performClick()
        compose.onNodeWithTag("project-name").assertIsDisplayed()
    }

    @Test
    fun when_the_server_is_unreachable_it_says_so_and_can_retry() {
        failLoad = true
        show()
        compose.onNodeWithTag("projects-error").assertIsDisplayed()
        compose.onNodeWithText("Server is down").assertIsDisplayed()
        failLoad = false
        compose.onNodeWithTag("projects-retry").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("project-p1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun the_back_link_goes_back() {
        show()
        compose.onNodeWithTag("projects-back").performClick()
        assertEquals(1, backed)
    }
}
