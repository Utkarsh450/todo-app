package com.utkarsh.todo.feature.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.habits.ui.HabitRowUi
import com.utkarsh.todo.feature.habits.ui.HabitsBlockContent
import com.utkarsh.todo.feature.habits.ui.HabitsUiState
import com.utkarsh.todo.feature.notes.ui.NoteBlockContent
import com.utkarsh.todo.feature.notes.ui.NoteUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HabitsAndNoteBlocksTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val habits = HabitsUiState(
        isLoading = false, todayIndex = 6,
        rows = listOf(
            HabitRowUi("h1", "Read 20 min", listOf(true, true, false, true, true, true, false), 5),
            HabitRowUi("h2", "Drink 2L water", listOf(true, true, true, true, false, true, true), 6),
        ),
    )

    @Test
    fun habits_show_rows_summary_and_toggle_days() {
        events.clear()
        compose.setContent { PaperTheme { HabitsBlockContent(habits, { id, i -> events += "$id:$i" }, { events += "add:$it" }) } }

        compose.onNodeWithText("1/2 TODAY").assertIsDisplayed()
        compose.onNodeWithText("Read 20 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Read 20 min day 1").assertIsOn()
        compose.onNodeWithContentDescription("Read 20 min day 7").performClick()

        assertEquals(listOf("h1:6"), events)
    }

    @Test
    fun add_habit_submits_on_ime_done() {
        events.clear()
        compose.setContent { PaperTheme { HabitsBlockContent(habits, { _, _ -> }, { events += "add:$it" }) } }

        compose.onNodeWithTag("add-habit-input").performTextInput("No phone after 11")
        compose.onNodeWithTag("add-habit-input").performImeAction()

        assertEquals(listOf("add:No phone after 11"), events)
    }

    @Test
    fun note_shows_placeholder_then_reports_typing() {
        var latest = ""
        compose.setContent {
            var state by mutableStateOf(NoteUiState())
            PaperTheme { NoteBlockContent(state, { latest = it; state = state.copy(text = it) }) }
        }

        compose.onNodeWithText("Jot something down…").assertIsDisplayed()
        compose.onNodeWithTag("note-input").performTextInput("Ask Sam about Room")

        assertEquals("Ask Sam about Room", latest)
    }
}
