package com.utkarsh.todo.core

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.calendar.ui.CalendarScreen
import com.utkarsh.todo.feature.calendar.ui.CalendarUiState
import com.utkarsh.todo.feature.insights.ui.InsightsScreen
import com.utkarsh.todo.feature.insights.ui.InsightsUiState
import com.utkarsh.todo.feature.reminders.ui.RemindersScreen
import com.utkarsh.todo.feature.reminders.ui.RemindersUiState
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailScreen
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailUiState
import com.utkarsh.todo.feature.today.ui.TodayScreen
import com.utkarsh.todo.feature.today.ui.TodayUiState
import org.junit.Rule
import org.junit.Test

/** While data loads every screen shows a skeleton shaped like its content, never a spinner. */
class SkeletonScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun noSpinner() =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)

    @Test
    fun today_shows_a_skeleton_while_loading() {
        compose.setContent {
            PaperTheme { TodayScreen(TodayUiState(isLoading = true), {}, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("today-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading today's tasks").assertIsDisplayed()
        noSpinner()
    }

    @Test
    fun today_drops_the_skeleton_once_loaded() {
        compose.setContent {
            PaperTheme { TodayScreen(TodayUiState(isLoading = false), {}, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("today-skeleton").assertDoesNotExist()
    }

    @Test
    fun task_page_shows_a_skeleton_while_loading() {
        compose.setContent {
            PaperTheme { TaskDetailScreen(TaskDetailUiState(isLoading = true), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("detail-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading the task").assertIsDisplayed()
        noSpinner()
    }

    @Test
    fun task_page_shows_the_error_not_the_skeleton_when_it_failed() {
        compose.setContent {
            PaperTheme { TaskDetailScreen(TaskDetailUiState(isLoading = false, error = "offline"), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("detail-skeleton").assertDoesNotExist()
    }

    @Test
    fun calendar_shows_a_skeleton_while_loading() {
        compose.setContent {
            PaperTheme { CalendarScreen(CalendarUiState(isLoading = true), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("calendar-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading the calendar").assertIsDisplayed()
        noSpinner()
    }

    @Test
    fun reminders_show_a_skeleton_while_loading() {
        compose.setContent {
            PaperTheme { RemindersScreen(RemindersUiState(isLoading = true), { _, _ -> }, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("reminders-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading reminders").assertIsDisplayed()
        noSpinner()
    }

    @Test
    fun insights_show_a_skeleton_while_loading() {
        compose.setContent {
            PaperTheme { InsightsScreen(InsightsUiState(isLoading = true), {}, {}, {}) }
        }
        compose.onNodeWithTag("insights-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading insights").assertIsDisplayed()
        noSpinner()
    }

    @Test
    fun a_skeleton_surface_is_announced_once_and_hides_its_blocks() {
        compose.setContent {
            PaperTheme {
                SkeletonSurface("Loading things", testTag = "surface") {
                    SkeletonBlock(Modifier.size(width = 120.dp, height = 16.dp))
                    SkeletonBlock(Modifier.size(width = 80.dp, height = 16.dp))
                }
            }
        }
        compose.onNodeWithTag("surface").assertIsDisplayed()
        compose.onNodeWithContentDescription("Loading things").assertIsDisplayed()
    }
}
