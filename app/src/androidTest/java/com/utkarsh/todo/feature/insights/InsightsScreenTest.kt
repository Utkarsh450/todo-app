package com.utkarsh.todo.feature.insights

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.insights.ui.HEATMAP_WEEKS
import com.utkarsh.todo.feature.insights.ui.HeatCellUi
import com.utkarsh.todo.feature.insights.ui.InsightsScreen
import com.utkarsh.todo.feature.insights.ui.InsightsUiState
import com.utkarsh.todo.feature.insights.ui.RhythmBarUi
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InsightsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val first = LocalDate.of(2026, 7, 5)
    private val today = LocalDate.of(2026, 11, 7)

    private val weeks = (0 until HEATMAP_WEEKS).map { w ->
        (0 until 7).map { d ->
            val date = first.plusDays((w * 7 + d).toLong())
            HeatCellUi(date, count = d, level = d.coerceAtMost(4), isSelected = date == today, isFuture = date.isAfter(LocalDate.of(2026, 11, 5)))
        }
    }

    private fun state() = InsightsUiState(
        isLoading = false, weeks = weeks, monthLabels = weeks.map { if (it.first().date.dayOfMonth <= 7) "Nov" else "" },
        selectedLabel = "Sat 7 Nov", selectedValue = "3 tasks done", total = 120, streak = 5, best = 9,
        rhythm = "SMTWTFS".mapIndexed { i, c -> RhythmBarUi(c.toString(), (i + 1) / 7f, isPeak = i == 6) },
    )

    private fun show(state: InsightsUiState) {
        events.clear()
        compose.setContent { PaperTheme { InsightsScreen(state, { events += "select:$it" }, { events += "retry" }) } }
    }

    @Test
    fun shows_stats_heatmap_selection_and_rhythm() {
        show(state())

        compose.onNodeWithTag("insights-title").assertIsDisplayed()
        compose.onNodeWithTag("stat-total").assertIsDisplayed()
        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.onNodeWithText("DAY STREAK").assertIsDisplayed()
        compose.onNodeWithTag("heatmap").assertIsDisplayed()
        compose.onNodeWithText("Sat 7 Nov").assertIsDisplayed()
        compose.onNodeWithTag("selected-value").assertIsDisplayed()
        compose.onNodeWithText("WEEKLY RHYTHM").assertIsDisplayed()
    }

    @Test
    fun tapping_a_heat_cell_reports_its_date_but_future_cells_are_inert() {
        show(state())

        compose.onNodeWithTag("heat-2026-11-03").performClick()
        compose.onNodeWithTag("heat-2026-11-06").assertHasNoClickAction() // marked future in this fixture
        compose.onNodeWithTag("heat-2026-11-03").assertHasClickAction()

        assertEquals(listOf("select:2026-11-03"), events)
    }

    @Test
    fun there_is_no_habits_switch_and_the_total_is_labelled_with_the_weeks() {
        show(state())
        compose.onNodeWithText("Habits").assertDoesNotExist()
        compose.onNodeWithText("18 WEEKS").assertIsDisplayed()
    }

    @Test
    fun error_state_offers_retry() {
        show(InsightsUiState(isLoading = false, error = "offline"))
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }
}
