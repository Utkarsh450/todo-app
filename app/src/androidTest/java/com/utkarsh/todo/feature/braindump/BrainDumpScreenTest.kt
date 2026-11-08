package com.utkarsh.todo.feature.braindump

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.ai.domain.DumpedTask
import com.utkarsh.todo.feature.braindump.ui.BrainDumpScreen
import com.utkarsh.todo.feature.braindump.ui.BrainDumpUiState
import com.utkarsh.todo.feature.braindump.ui.DumpPhase
import com.utkarsh.todo.feature.braindump.ui.DumpRow
import com.utkarsh.todo.feature.tasks.domain.Priority
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrainDumpScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: BrainDumpUiState, withMic: Boolean = true) {
        events.clear()
        compose.setContent {
            PaperTheme {
                BrainDumpScreen(
                    state = state,
                    onBack = { events += "back" },
                    onTextChange = { events += "text:$it" },
                    onSort = { events += "sort" },
                    onStartOver = { events += "reset" },
                    onEditNote = { events += "edit" },
                    onToggle = { events += "toggle:$it" },
                    onAdd = { events += "add" },
                    onMic = if (withMic) ({ events += "mic" }) else null,
                )
            }
        }
    }

    private fun row(id: Int, title: String, due: String? = null, priority: Priority = Priority.NORMAL, repeat: String? = null, project: String? = null, selected: Boolean = true) =
        DumpRow(id, title, due, priority, repeat, project, if (project != null) "#935EDF" else null, selected, DumpedTask(title, null, priority, repeat, null, project))

    private val results = BrainDumpUiState(
        text = "Okay so... I need to email Priya by Friday morning",
        phase = DumpPhase.RESULTS,
        rows = listOf(
            row(1, "Email Priya the deck", due = "Fri 13 Nov, 10:00 AM", project = "Design"),
            row(2, "Fix crash on Android 15", priority = Priority.URGENT),
            row(3, "Renew the gym", repeat = "Every month"),
        ),
    )

    @Test
    fun input_state_invites_a_note_and_offers_the_mic() {
        show(BrainDumpUiState())

        compose.onNodeWithTag("dump-title").assertIsDisplayed()
        compose.onNodeWithText("Talk or type everything on your mind…").assertIsDisplayed()
        compose.onNodeWithTag("dump-mic").assertIsDisplayed()
        compose.onNodeWithText("TAP TO SPEAK").assertIsDisplayed()
        compose.onNodeWithText("✦ Turn into tasks").assertIsDisplayed()
    }

    @Test
    fun the_mic_is_hidden_without_a_speech_recogniser() {
        show(BrainDumpUiState(), withMic = false)
        compose.onNodeWithTag("dump-mic").assertDoesNotExist()
        compose.onNodeWithTag("dump-input").assertIsDisplayed()
    }

    @Test
    fun mic_reports_a_tap() {
        show(BrainDumpUiState(text = "something"))
        compose.onNodeWithText("TAP TO ADD MORE").assertIsDisplayed()
        compose.onNodeWithTag("dump-mic").performClick()
        assertEquals(listOf("mic"), events)
    }

    @Test
    fun typing_is_reported() {
        show(BrainDumpUiState())
        compose.onNodeWithTag("dump-input").performTextInput("buy milk")
        assertEquals(listOf("text:buy milk"), events)
    }

    @Test
    fun sort_is_disabled_for_a_blank_note() {
        show(BrainDumpUiState(text = "  "))
        compose.onNodeWithTag("dump-sort").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun sort_works_once_there_is_text() {
        show(BrainDumpUiState(text = "email priya"))
        compose.onNodeWithTag("dump-sort").performClick()
        assertEquals(listOf("sort"), events)
    }

    @Test
    fun sorting_shows_progress_and_blocks_taps() {
        show(BrainDumpUiState(text = "note", phase = DumpPhase.SORTING))
        compose.onNodeWithText("Sorting…").assertIsDisplayed()
        compose.onNodeWithTag("dump-sort").performClick()
        compose.onNodeWithTag("dump-mic").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun a_hint_or_error_is_shown_under_the_note() {
        show(BrainDumpUiState(text = "x", message = "The AI service is busy or over its limit. Try again in a moment."))
        compose.onNodeWithTag("dump-message").assertIsDisplayed()
        compose.onNodeWithText("The AI service is busy or over its limit. Try again in a moment.").assertIsDisplayed()
    }

    @Test
    fun a_missing_key_explains_how_to_set_it_up() {
        show(BrainDumpUiState(text = "x", message = "AI is not set up.", notConfigured = true))
        compose.onNodeWithText("Grok isn't set up yet").assertIsDisplayed()
        compose.onNodeWithText("Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.").assertIsDisplayed()
    }

    @Test
    fun results_list_each_task_with_its_tags() {
        show(results)

        compose.onNodeWithText("FOUND 3 TASKS · UNTICK TO DROP").assertIsDisplayed()
        compose.onNodeWithText("Email Priya the deck").assertIsDisplayed()
        compose.onNodeWithText("◷ FRI 13 NOV, 10:00 AM").assertIsDisplayed()
        compose.onNodeWithText("Design").assertIsDisplayed()
        compose.onNodeWithText("⚑ URGENT").assertIsDisplayed()
        compose.onNodeWithText("↻ EVERY MONTH").assertIsDisplayed()
        compose.onNodeWithText("Add 3 tasks").assertIsDisplayed()
        compose.onNodeWithTag("dump-mic").assertDoesNotExist()
    }

    @Test
    fun tapping_a_row_toggles_it_and_the_button_follows_the_count() {
        show(results.copy(rows = results.rows.mapIndexed { i, r -> if (i == 1) r.copy(selected = false) else r }))

        compose.onNodeWithText("Add 2 tasks").assertIsDisplayed()
        compose.onNodeWithTag("dump-row-1").performClick()
        assertEquals(listOf("toggle:1"), events)
    }

    @Test
    fun add_confirms() {
        show(results)
        compose.onNodeWithTag("dump-add").performClick()
        assertEquals(listOf("add"), events)
    }

    @Test
    fun nothing_ticked_disables_add() {
        show(results.copy(rows = results.rows.map { it.copy(selected = false) }))
        compose.onNodeWithText("Pick at least one").assertIsDisplayed()
        compose.onNodeWithTag("dump-add").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun a_partial_failure_message_is_shown_with_the_rest_still_listed() {
        show(results.copy(rows = results.rows.drop(1), message = "Added 1 of 3. Couldn't add the rest."))
        compose.onNodeWithText("Added 1 of 3. Couldn't add the rest.").assertIsDisplayed()
        compose.onNodeWithText("FOUND 2 TASKS · UNTICK TO DROP").assertIsDisplayed()
    }

    @Test
    fun edit_note_start_over_and_back_are_reported() {
        show(results)
        compose.onNodeWithTag("dump-edit").performClick()
        compose.onNodeWithTag("dump-start-over").performClick()
        compose.onNodeWithTag("dump-back").performClick()
        assertEquals(listOf("edit", "reset", "back"), events)
    }

    @Test
    fun the_note_is_kept_visible_beside_the_results() {
        show(results)
        compose.onNodeWithTag("dump-note").assertIsDisplayed()
    }
}
