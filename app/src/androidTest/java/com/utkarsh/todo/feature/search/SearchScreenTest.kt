package com.utkarsh.todo.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.search.ui.SearchPhase
import com.utkarsh.todo.feature.search.ui.SearchResultUi
import com.utkarsh.todo.feature.search.ui.SearchScreen
import com.utkarsh.todo.feature.search.ui.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val typed = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private var cleared = 0
    private var retried = 0
    private var backed = 0

    private fun show(initial: SearchUiState) {
        compose.setContent {
            var state by mutableStateOf(initial)
            PaperTheme {
                SearchScreen(
                    state = state, onBack = { backed++ },
                    onQueryChange = { typed += it; state = state.copy(query = it) },
                    onClear = { cleared++; state = SearchUiState() },
                    onRetry = { retried++ }, onOpenTask = { opened += it },
                )
            }
        }
    }

    private val results = SearchUiState(
        query = "inv", phase = SearchPhase.RESULTS,
        results = listOf(
            SearchResultUi("a", "Pay invoice", false, "Tomorrow, 9:00 AM", "Finance", "#2FA465"),
            SearchResultUi("b", "Invoice review", true, null, null, null),
        ),
    )

    @Test
    fun idle_invites_you_to_type() {
        show(SearchUiState())
        compose.onNodeWithTag("search-idle").assertIsDisplayed()
        compose.onNodeWithText("Search your tasks").assertIsDisplayed()
        compose.onNodeWithTag("search-clear").assertDoesNotExist()
    }

    @Test
    fun typing_reports_each_change() {
        show(SearchUiState())
        compose.onNodeWithTag("search-input").performTextInput("inv")
        assertEquals("inv", typed.last())
        compose.onNodeWithTag("search-clear").assertIsDisplayed()
    }

    @Test
    fun the_clear_button_empties_the_box() {
        show(results)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(1, cleared)
        compose.onNodeWithTag("search-idle").assertIsDisplayed()
    }

    @Test
    fun results_show_a_count_titles_due_times_and_projects() {
        show(results)
        compose.onNodeWithTag("search-count").assertIsDisplayed()
        compose.onNodeWithText("2 RESULTS").assertIsDisplayed()
        compose.onNodeWithText("Pay invoice").assertIsDisplayed()
        compose.onNodeWithText("TOMORROW, 9:00 AM").assertIsDisplayed()
        compose.onNodeWithText("FINANCE").assertIsDisplayed()
        compose.onNodeWithText("Invoice review").assertIsDisplayed()
    }

    @Test
    fun one_result_is_singular() {
        show(results.copy(results = results.results.take(1)))
        compose.onNodeWithText("1 RESULT").assertIsDisplayed()
    }

    @Test
    fun tapping_a_result_opens_that_task() {
        show(results)
        compose.onNodeWithTag("result-b").performClick()
        assertEquals(listOf("b"), opened)
    }

    @Test
    fun loading_shows_a_skeleton_not_a_spinner() {
        show(SearchUiState(query = "inv", phase = SearchPhase.LOADING))
        compose.onNodeWithTag("search-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Searching").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
    }

    @Test
    fun no_match_names_what_was_searched() {
        show(SearchUiState(query = " zebra ", phase = SearchPhase.EMPTY))
        compose.onNodeWithTag("search-empty").assertIsDisplayed()
        compose.onNodeWithText("Nothing matches “zebra”").assertIsDisplayed()
    }

    @Test
    fun an_error_can_be_retried() {
        show(SearchUiState(query = "a", phase = SearchPhase.ERROR, error = "Server is down"))
        compose.onNodeWithText("Server is down").assertIsDisplayed()
        compose.onNodeWithTag("search-retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun the_back_link_goes_back() {
        show(SearchUiState())
        compose.onNodeWithTag("search-back").performClick()
        assertEquals(1, backed)
    }
}
