package com.utkarsh.todo.feature.quickadd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.domain.QuickAddType
import com.utkarsh.todo.feature.quickadd.ui.QuickAddContent
import com.utkarsh.todo.feature.quickadd.ui.QuickAddUiState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The stateless sheet content for each type: wording, chips and the switch itself. */
class QuickAddTypesTest {
    @get:Rule
    val compose = createComposeRule()

    private val parser = QuickAddParser(Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneId.of("UTC")))
    private val chosen = mutableListOf<QuickAddType>()

    /** A tiny stand-in for the view-model: it switches type and re-reads the text like the real one does. */
    private fun show(initial: QuickAddType = QuickAddType.TASK, withSwitch: Boolean = true) {
        chosen.clear()
        compose.setContent {
            var state by mutableStateOf(QuickAddUiState(type = initial))
            fun parse(text: String, type: QuickAddType) = when {
                text.isBlank() -> null
                type == QuickAddType.NOTE -> com.utkarsh.todo.feature.quickadd.domain.QuickAddResult(text.trim(), null, null, null, null, emptyList())
                else -> parser.parse(text, type.kinds)
            }
            PaperTheme {
                QuickAddContent(
                    state = state,
                    onTextChange = { text -> state = state.copy(text = text, parsed = parse(text, state.type)) },
                    onSubmit = {},
                    onTypeChange = if (withSwitch) ({ type -> chosen += type; state = state.copy(type = type, parsed = parse(state.text, type)) }) else null,
                )
            }
        }
    }

    @Test
    fun the_switch_offers_task_note_and_event() {
        show()
        compose.onNodeWithTag("quick-add-type").assertIsDisplayed()
        compose.onNodeWithText("Task").assertIsDisplayed()
        compose.onNodeWithText("Note").assertIsDisplayed()
        compose.onNodeWithText("Event").assertIsDisplayed()
    }

    @Test
    fun the_switch_is_left_out_when_nothing_listens_to_it() {
        show(withSwitch = false) // how the Reminders sheet reuses this content
        compose.onNodeWithTag("quick-add-type").assertDoesNotExist()
        compose.onNodeWithText("→ TODAY'S LIST").assertIsDisplayed()
    }

    @Test
    fun task_wording_is_the_default() {
        show()
        compose.onNodeWithText("→ TODAY'S LIST").assertIsDisplayed()
        compose.onNodeWithText("What needs doing?").assertIsDisplayed()
        compose.onNodeWithText("Add task").assertIsDisplayed()
    }

    @Test
    fun choosing_note_changes_the_wording_and_reports_the_choice() {
        show()
        compose.onNodeWithText("Note").performClick()

        assertEquals(listOf(QuickAddType.NOTE), chosen)
        compose.onNodeWithText("→ TODAY'S SCRATCHPAD").assertIsDisplayed()
        compose.onNodeWithText("Jot something down…").assertIsDisplayed()
        compose.onNodeWithText("Added to the end of today's scratchpad, exactly as you type it.").assertIsDisplayed()
        compose.onNodeWithText("Add note").assertIsDisplayed()
    }

    @Test
    fun choosing_event_changes_the_wording() {
        show()
        compose.onNodeWithText("Event").performClick()

        compose.onNodeWithText("→ CALENDAR").assertIsDisplayed()
        compose.onNodeWithText("What's happening?").assertIsDisplayed()
        compose.onNodeWithText("Add event").assertIsDisplayed()
    }

    @Test
    fun a_note_shows_no_chips_even_when_it_contains_task_words() {
        show(QuickAddType.NOTE)
        compose.onNodeWithTag("quick-add-input").performTextInput("Call mom tomorrow #family !high")

        compose.onNodeWithText("◷ tomorrow").assertDoesNotExist()
        compose.onNodeWithText("■ family").assertDoesNotExist()
        compose.onNodeWithText("⚑ High").assertDoesNotExist()
    }

    @Test
    fun an_event_with_a_time_shows_its_day_chip_and_no_all_day_chip() {
        show(QuickAddType.EVENT)
        compose.onNodeWithTag("quick-add-input").performTextInput("Lunch fri 1pm")

        compose.onNodeWithText("◷ fri 1pm").assertIsDisplayed()
        compose.onNodeWithText("◷ All day").assertDoesNotExist()
    }

    @Test
    fun an_event_with_only_a_day_is_marked_all_day() {
        show(QuickAddType.EVENT)
        compose.onNodeWithTag("quick-add-input").performTextInput("Offsite tomorrow")

        compose.onNodeWithText("◷ tomorrow").assertIsDisplayed()
        compose.onNodeWithText("◷ All day").assertIsDisplayed()
    }

    @Test
    fun an_event_ignores_task_only_words() {
        show(QuickAddType.EVENT)
        compose.onNodeWithTag("quick-add-input").performTextInput("Sync !high tomorrow 3pm")

        compose.onNodeWithText("⚑ High").assertDoesNotExist()
        compose.onNodeWithText("◷ tomorrow 3pm").assertIsDisplayed()
    }

    @Test
    fun switching_type_keeps_what_was_typed_and_updates_the_chips() {
        show()
        compose.onNodeWithTag("quick-add-input").performTextInput("Plan trip tomorrow !high")
        compose.onNodeWithText("⚑ High").assertIsDisplayed()

        compose.onNodeWithText("Event").performClick()

        compose.onNodeWithText("⚑ High").assertDoesNotExist()
        compose.onNodeWithText("◷ tomorrow").assertIsDisplayed()
    }
}
