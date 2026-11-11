package com.utkarsh.todo.feature.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.notes.ui.NoteBlockContent
import com.utkarsh.todo.feature.notes.ui.NoteUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoteBlockTest {
    @get:Rule
    val compose = createComposeRule()

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
