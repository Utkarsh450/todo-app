package com.utkarsh.todo.feature.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.ai.domain.AssistantSuggestion
import com.utkarsh.todo.feature.assistant.ui.AssistantScreen
import com.utkarsh.todo.feature.assistant.ui.AssistantUiState
import com.utkarsh.todo.feature.assistant.ui.SuggestionKind
import com.utkarsh.todo.feature.assistant.ui.SuggestionStatus
import com.utkarsh.todo.feature.assistant.ui.SuggestionUi
import com.utkarsh.todo.feature.assistant.ui.TurnUi
import com.utkarsh.todo.feature.tasks.domain.Priority
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AssistantScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: AssistantUiState, withMic: Boolean = false) {
        events.clear()
        compose.setContent {
            PaperTheme {
                AssistantScreen(
                    state = state,
                    onBack = { events += "back" },
                    onInputChange = { events += "input:$it" },
                    onSend = { events += "send" },
                    onPrompt = { events += "prompt:$it" },
                    onAccept = { events += "accept:$it" },
                    onSkip = { events += "skip:$it" },
                    onAcceptAll = { events += "all:$it" },
                    onRetry = { events += "retry:$it" },
                    onMic = if (withMic) ({ events += "mic" }) else null,
                )
            }
        }
    }

    private fun suggestion(id: Int, kind: SuggestionKind, title: String, status: SuggestionStatus = SuggestionStatus.PENDING, steps: List<String> = emptyList()) =
        SuggestionUi(id, kind, title, "Because reasons $id", steps, status, AssistantSuggestion.ChangePriority("t", "t", "", Priority.LOW))

    private val answered = AssistantUiState(
        turns = listOf(
            TurnUi(
                id = 1, question = "Low-energy afternoon. Plan the rest of my day?", loading = false, reply = "Here's a lighter shape for today.",
                suggestions = listOf(
                    suggestion(1, SuggestionKind.MOVE, "Review PR #482 → Tomorrow, 9:00 AM"),
                    suggestion(2, SuggestionKind.SPLIT, "Ship onboarding redesign", steps = listOf("Outline", "Draft")),
                ),
            ),
        ),
    )

    @Test
    fun empty_state_invites_a_question_and_offers_prompts() {
        show(AssistantUiState())

        compose.onNodeWithTag("assistant-title").assertIsDisplayed()
        compose.onNodeWithTag("assistant-empty").assertIsDisplayed()
        compose.onNodeWithText("Plan my day").assertIsDisplayed()
        compose.onNodeWithText("What's slipping?").assertIsDisplayed()
        compose.onNodeWithText("Ask Grok, or tap the mic").assertIsDisplayed()
    }

    @Test
    fun the_mic_shows_only_when_the_device_can_listen_and_reports_a_tap() {
        show(AssistantUiState())
        compose.onNodeWithTag("assistant-mic").assertDoesNotExist()
    }

    @Test
    fun tapping_the_mic_starts_listening() {
        show(AssistantUiState(), withMic = true)
        compose.onNodeWithTag("assistant-mic").performClick()
        assertEquals(listOf("mic"), events)
    }

    @Test
    fun suggestions_are_compact_rows_with_round_accept_and_skip_buttons() {
        show(answered)
        compose.onNodeWithTag("accept-1").assertIsDisplayed()
        compose.onNodeWithTag("skip-1").assertIsDisplayed()
        compose.onNodeWithText("Accept").assertDoesNotExist() // no wide text buttons any more
        compose.onNodeWithTag("accept-2").performClick()
        compose.onNodeWithTag("skip-1").performClick()
        assertEquals(listOf("accept:2", "skip:1"), events)
    }

    @Test
    fun a_quick_prompt_is_sent_as_text() {
        show(AssistantUiState())
        compose.onNodeWithTag("prompt-1").performClick()
        assertEquals(listOf("prompt:What's slipping?"), events)
    }

    @Test
    fun typing_and_sending_are_reported() {
        show(AssistantUiState(input = "hello"))
        compose.onNodeWithTag("assistant-input").performTextInput("!")
        compose.onNodeWithTag("assistant-send").performClick()
        assertEquals(listOf("input:!hello", "send"), events)
    }

    @Test
    fun send_is_disabled_without_text() {
        show(AssistantUiState(input = "  "))
        compose.onNodeWithTag("assistant-send").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun shows_the_question_the_reply_and_the_suggestions() {
        show(answered)

        compose.onNodeWithText("Low-energy afternoon. Plan the rest of my day?").assertIsDisplayed()
        compose.onNodeWithTag("assistant-reply").assertIsDisplayed()
        compose.onNodeWithText("2 SUGGESTIONS").assertIsDisplayed()
        compose.onNodeWithText("MOVE").assertIsDisplayed()
        compose.onNodeWithText("Review PR #482 → Tomorrow, 9:00 AM").assertIsDisplayed()
        compose.onNodeWithText("SPLIT").assertIsDisplayed()
        compose.onNodeWithText("Outline  ·  Draft").assertIsDisplayed()
    }

    @Test
    fun accept_skip_and_accept_all_are_reported() {
        show(answered)

        compose.onNodeWithTag("accept-1").performClick()
        compose.onNodeWithTag("skip-2").performClick()
        compose.onNodeWithTag("accept-all-1").performClick()

        assertEquals(listOf("accept:1", "skip:2", "all:1"), events)
    }

    @Test
    fun accept_all_is_hidden_when_only_one_suggestion_is_left() {
        val turn = answered.turns.single()
        show(answered.copy(turns = listOf(turn.copy(suggestions = listOf(turn.suggestions[0], turn.suggestions[1].copy(status = SuggestionStatus.SKIPPED))))))
        compose.onNodeWithTag("accept-all-1").assertDoesNotExist()
    }

    @Test
    fun finished_suggestions_show_their_outcome_and_no_buttons() {
        val turn = answered.turns.single()
        show(
            answered.copy(
                turns = listOf(
                    turn.copy(
                        suggestions = listOf(
                            turn.suggestions[0].copy(status = SuggestionStatus.ACCEPTED),
                            turn.suggestions[1].copy(status = SuggestionStatus.SKIPPED),
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithTag("status-1").assertIsDisplayed()
        compose.onNodeWithText("✓ DONE").assertIsDisplayed()
        compose.onNodeWithText("SKIPPED").assertIsDisplayed()
        compose.onNodeWithTag("accept-1").assertDoesNotExist()
        compose.onNodeWithTag("skip-2").assertDoesNotExist()
    }

    @Test
    fun a_failed_change_can_be_accepted_again() {
        val turn = answered.turns.single()
        show(answered.copy(turns = listOf(turn.copy(suggestions = listOf(turn.suggestions[0].copy(status = SuggestionStatus.FAILED))))))

        compose.onNodeWithTag("failed-1").assertIsDisplayed()
        compose.onNodeWithTag("accept-1").performClick()
        assertEquals(listOf("accept:1"), events)
    }

    @Test
    fun loading_shows_a_skeleton_answer_and_blocks_new_questions() {
        show(AssistantUiState(turns = listOf(TurnUi(id = 1, question = "Plan my day", loading = true))))

        compose.onNodeWithTag("assistant-loading").assertIsDisplayed()
        compose.onNodeWithContentDescription("Grok is thinking").assertIsDisplayed()
        compose.onNodeWithTag("prompt-0").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun an_error_shows_its_message_and_retry() {
        show(AssistantUiState(turns = listOf(TurnUi(id = 3, question = "q", loading = false, error = "The AI service is busy or over its limit. Try again in a moment."))))

        compose.onNodeWithText("The AI service is busy or over its limit. Try again in a moment.").assertIsDisplayed()
        compose.onNodeWithTag("assistant-retry").performClick()
        assertEquals(listOf("retry:3"), events)
    }

    @Test
    fun missing_key_explains_how_to_set_it_up() {
        show(AssistantUiState(turns = listOf(TurnUi(id = 1, question = "q", loading = false, error = "AI is not set up.", notConfigured = true))))

        compose.onNodeWithText("Grok isn't set up yet").assertIsDisplayed()
        compose.onNodeWithText("Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.").assertIsDisplayed()
        compose.onNodeWithTag("assistant-retry").assertDoesNotExist()
    }

    @Test
    fun back_goes_back() {
        show(AssistantUiState())
        compose.onNodeWithTag("assistant-back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun new_things_show_their_kind_and_can_be_accepted() {
        show(
            AssistantUiState(
                turns = listOf(
                    TurnUi(
                        id = 1, question = "add", loading = false, reply = "Added.",
                        suggestions = listOf(
                            suggestion(1, SuggestionKind.TASK, "Buy milk — Fri, 9:00 AM"),
                            suggestion(2, SuggestionKind.EVENT, "Dentist — Tue, 3:00 PM"),
                            suggestion(3, SuggestionKind.REMINDER, "Stretch — Today, 6:00 PM · Every Day"),
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("TASK").assertExists()
        compose.onNodeWithText("EVENT").assertExists()
        compose.onNodeWithText("REMINDER").assertExists()
        compose.onNodeWithText("Dentist — Tue, 3:00 PM").assertIsDisplayed()
        compose.onNodeWithTag("accept-2").performClick()
        assertEquals(listOf("accept:2"), events)
    }

    @Test
    fun the_remind_me_chip_fills_the_input_instead_of_sending() {
        show(AssistantUiState())
        compose.onNodeWithText("Remind me to…").performClick()
        compose.onNodeWithText("Remind me to…").assertExists()
        assertEquals(listOf("input:Remind me to "), events)
    }
}
