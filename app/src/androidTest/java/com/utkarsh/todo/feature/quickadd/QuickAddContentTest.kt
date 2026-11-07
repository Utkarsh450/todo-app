package com.utkarsh.todo.feature.quickadd

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.quickadd.domain.QuickAddParser
import com.utkarsh.todo.feature.quickadd.ui.QuickAddContent
import com.utkarsh.todo.feature.quickadd.ui.QuickAddUiState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickAddContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val parser = QuickAddParser(Clock.fixed(Instant.parse("2026-11-07T04:30:00Z"), ZoneId.of("UTC")))

    private var submits = 0

    private fun show(initial: QuickAddUiState = QuickAddUiState()) {
        submits = 0
        compose.setContent {
            var state by mutableStateOf(initial)
            PaperTheme {
                QuickAddContent(
                    state = state,
                    onTextChange = { text -> state = state.copy(text = text, parsed = text.takeIf { it.isNotBlank() }?.let(parser::parse)) },
                    onSubmit = { submits++ },
                )
            }
        }
    }

    @Test
    fun shows_hint_and_destination_before_typing() {
        show()

        compose.onNodeWithText("→ TODAY'S LIST").assertIsDisplayed()
        compose.onNodeWithText("What needs doing?").assertIsDisplayed()
        compose.onNodeWithText("Type “fri 3pm”, “#project”, “!high”, “every monday”").assertIsDisplayed()
    }

    @Test
    fun typing_tokens_shows_chips() {
        show()

        compose.onNodeWithTag("quick-add-input").performTextInput("Review tomorrow 9am #Design !high")

        compose.onNodeWithText("◷ tomorrow 9am").assertIsDisplayed()
        compose.onNodeWithText("■ Design").assertIsDisplayed()
        compose.onNodeWithText("⚑ High").assertIsDisplayed()
    }

    @Test
    fun add_button_submits_only_when_there_is_a_title() {
        show()
        compose.onNodeWithTag("quick-add-submit").performClick()
        assertEquals(0, submits)

        compose.onNodeWithTag("quick-add-input").performTextInput("Buy milk")
        compose.onNodeWithTag("quick-add-submit").performClick()
        assertEquals(1, submits)
    }

    @Test
    fun shows_error_message() {
        show(QuickAddUiState(text = "x", parsed = parser.parse("x"), error = "offline"))
        compose.onNodeWithText("offline").assertIsDisplayed()
    }
}
