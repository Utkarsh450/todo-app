package com.utkarsh.todo.feature.memory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.ai.domain.MemoryFact
import com.utkarsh.todo.feature.memory.ui.MemoryScreen
import com.utkarsh.todo.feature.memory.ui.MemoryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MemoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val wakes = MemoryFact("f1", "Wakes at 7", "ROUTINE")
    private val tea = MemoryFact("f2", "Likes tea", "PREFERENCE")

    private fun show(state: MemoryUiState) {
        events.clear()
        compose.setContent {
            PaperTheme {
                MemoryScreen(
                    state = state,
                    onBack = { events += "back" },
                    onAdd = { events += "add:$it" },
                    onStartEdit = { events += "start-edit:$it" },
                    onSaveEdit = { id, text -> events += "save:$id:$text" },
                    onCancelEdit = { events += "cancel-edit" },
                    onDelete = { events += "delete:$it" },
                    onAskForgetAll = { events += "ask-forget-all" },
                    onConfirmForgetAll = { events += "confirm-forget-all" },
                    onCancelForgetAll = { events += "cancel-forget-all" },
                    onDismissMessage = { events += "dismiss" },
                    onRetry = { events += "retry" },
                )
            }
        }
    }

    @Test
    fun an_empty_memory_explains_how_it_fills_up() {
        show(MemoryUiState(isLoading = false))

        compose.onNodeWithTag("memory-empty").assertIsDisplayed()
        compose.onNodeWithTag("forget-all").assertDoesNotExist()
    }

    @Test
    fun facts_are_listed_under_their_category_headings() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes, tea)))

        compose.onNodeWithText("ROUTINES").assertIsDisplayed()
        compose.onNodeWithText("PREFERENCES").assertIsDisplayed()
        compose.onNodeWithText("Wakes at 7").assertIsDisplayed()
        compose.onNodeWithText("Likes tea").assertIsDisplayed()
    }

    @Test
    fun the_skeleton_shows_while_loading() {
        show(MemoryUiState(isLoading = true))
        compose.onNodeWithTag("memory-skeleton").assertIsDisplayed()
    }

    @Test
    fun a_load_error_with_nothing_to_show_offers_retry() {
        show(MemoryUiState(isLoading = false, error = "Can't reach the server."))

        compose.onNodeWithText("Can't reach the server.").assertIsDisplayed()
        compose.onNodeWithTag("memory-retry").performClick()

        assertEquals(listOf("retry"), events)
    }

    @Test
    fun adding_a_fact_sends_the_typed_text() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes)))

        compose.onNodeWithTag("memory-input").performTextInput("Pays rent on the 1st")
        compose.onNodeWithTag("memory-add").performClick()

        assertEquals(listOf("add:Pays rent on the 1st"), events)
    }

    @Test
    fun delete_and_edit_report_which_fact() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes)))

        compose.onNodeWithTag("memory-edit-f1").performClick()
        compose.onNodeWithTag("memory-delete-f1").performClick()

        assertEquals(listOf("start-edit:f1", "delete:f1"), events)
    }

    @Test
    fun editing_shows_a_field_with_save_and_cancel() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes), editing = "f1"))

        compose.onNodeWithTag("memory-edit-input-f1").assertIsDisplayed()
        compose.onNodeWithTag("memory-save-f1").performClick()
        compose.onNodeWithTag("memory-cancel-edit").performClick()

        assertEquals(listOf("save:f1:Wakes at 7", "cancel-edit"), events)
    }

    @Test
    fun forget_everything_only_asks_at_first() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes, tea)))

        compose.onNodeWithTag("forget-all").performClick()

        assertEquals(listOf("ask-forget-all"), events)
    }

    @Test
    fun the_confirmation_dialog_forgets_on_confirm() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes, tea), confirmingForgetAll = true))

        compose.onNodeWithTag("confirm-forget-all").performClick()

        assertEquals(listOf("confirm-forget-all"), events)
    }

    @Test
    fun a_message_shows_and_a_tap_dismisses_it() {
        show(MemoryUiState(isLoading = false, facts = listOf(wakes), message = "I do not keep passwords, keys, tokens or card and ID numbers."))

        compose.onNodeWithTag("memory-message").performClick()

        assertEquals(listOf("dismiss"), events)
    }
}
