package com.utkarsh.todo.feature.breakdown

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.breakdown.ui.BreakdownContent
import com.utkarsh.todo.feature.breakdown.ui.BreakdownPhase
import com.utkarsh.todo.feature.breakdown.ui.BreakdownUiState
import com.utkarsh.todo.feature.breakdown.ui.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BreakdownSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: BreakdownUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                BreakdownContent(
                    state = state,
                    onToggle = { events += "toggle:$it" },
                    onAdd = { events += "add" },
                    onRetry = { events += "retry" },
                    onClose = { events += "close" },
                )
            }
        }
    }

    private val ready = BreakdownUiState(
        phase = BreakdownPhase.READY,
        suggestions = listOf(Suggestion("Book flights"), Suggestion("Find a hotel", selected = false), Suggestion("Pack")),
    )

    @Test
    fun loading_shows_progress_and_no_button() {
        show(BreakdownUiState(phase = BreakdownPhase.LOADING))

        compose.onNodeWithTag("breakdown-loading").assertIsDisplayed()
        compose.onNodeWithText("Thinking it through…").assertIsDisplayed()
        compose.onNodeWithTag("breakdown-add").assertDoesNotExist()
    }

    @Test
    fun ready_lists_steps_and_counts_the_ticked_ones() {
        show(ready)

        compose.onNodeWithText("Book flights").assertIsDisplayed()
        compose.onNodeWithText("Find a hotel").assertIsDisplayed()
        compose.onNodeWithText("Pack").assertIsDisplayed()
        compose.onNodeWithText("Add 2 subtasks").assertIsDisplayed()
        compose.onNodeWithText("Nothing is added until you tap the button.").assertIsDisplayed()
    }

    @Test
    fun tapping_a_step_toggles_it() {
        show(ready)

        compose.onNodeWithTag("suggestion-1").performClick()

        assertEquals(listOf("toggle:1"), events)
    }

    @Test
    fun add_button_confirms() {
        show(ready)

        compose.onNodeWithTag("breakdown-add").assertIsEnabled().performClick()

        assertEquals(listOf("add"), events)
    }

    @Test
    fun one_ticked_step_reads_singular() {
        show(BreakdownUiState(phase = BreakdownPhase.READY, suggestions = listOf(Suggestion("Only one"))))
        compose.onNodeWithText("Add 1 subtask").assertIsDisplayed()
    }

    @Test
    fun nothing_ticked_disables_the_button() {
        show(BreakdownUiState(phase = BreakdownPhase.READY, suggestions = listOf(Suggestion("A", selected = false))))

        compose.onNodeWithTag("breakdown-add").assertIsNotEnabled()
        compose.onNodeWithText("Pick at least one").assertIsDisplayed()
    }

    @Test
    fun adding_disables_the_button() {
        show(ready.copy(adding = true))

        compose.onNodeWithTag("breakdown-add").assertIsNotEnabled()
        compose.onNodeWithText("Adding…").assertIsDisplayed()
    }

    @Test
    fun partial_failure_message_is_shown() {
        show(ready.copy(message = "Added 1 of 3. Couldn't add the rest."))
        compose.onNodeWithTag("breakdown-message").assertIsDisplayed()
        compose.onNodeWithText("Added 1 of 3. Couldn't add the rest.").assertIsDisplayed()
    }

    @Test
    fun error_shows_message_and_retry() {
        show(BreakdownUiState(phase = BreakdownPhase.ERROR, message = "The AI service is busy or over its limit. Try again in a moment."))

        compose.onNodeWithText("Couldn't get suggestions").assertIsDisplayed()
        compose.onNodeWithText("The AI service is busy or over its limit. Try again in a moment.").assertIsDisplayed()
        compose.onNodeWithTag("breakdown-retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun not_configured_explains_the_key_and_closes() {
        show(BreakdownUiState(phase = BreakdownPhase.NOT_CONFIGURED, message = "AI is not set up."))

        compose.onNodeWithText("Grok isn't set up yet").assertIsDisplayed()
        compose.onNodeWithText("Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.").assertIsDisplayed()
        compose.onNodeWithTag("breakdown-close").performClick()
        assertEquals(listOf("close"), events)
    }
}
