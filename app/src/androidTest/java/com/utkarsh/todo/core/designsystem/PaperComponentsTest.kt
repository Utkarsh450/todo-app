package com.utkarsh.todo.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.utkarsh.todo.core.designsystem.component.NavBarItem
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.component.PaperNavBar
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.PaperSwitch
import com.utkarsh.todo.core.designsystem.component.UndoSnackbar
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaperComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun checkbox_toggles_state_when_tapped() {
        compose.setContent {
            var checked by mutableStateOf(false)
            PaperTheme { PaperCheckbox(checked, { checked = it }, label = "Done?") }
        }
        compose.onNodeWithContentDescription("Done?").assertIsOff().performClick().assertIsOn()
    }

    @Test
    fun switch_reports_new_value() {
        var latest: Boolean? = null
        compose.setContent {
            PaperTheme { PaperSwitch(checked = false, onCheckedChange = { latest = it }, label = "Reminder") }
        }
        compose.onNodeWithContentDescription("Reminder").performClick()
        assertEquals(true, latest)
    }

    @Test
    fun segmented_control_selects_tapped_option() {
        var selected = "Blocks"
        compose.setContent {
            var current by mutableStateOf(selected)
            PaperTheme {
                PaperSegmentedControl(listOf("Blocks", "Plan"), current, { current = it; selected = it })
            }
        }
        compose.onNodeWithText("Plan").performClick()
        assertEquals("Plan", selected)
    }

    @Test
    fun nav_bar_routes_selection_and_add() {
        var selectedKey = "today"
        var added = false
        val items = listOf(
            NavBarItem("today", "Today", Icons.Filled.Home),
            NavBarItem("calendar", "Calendar", Icons.Filled.DateRange),
            NavBarItem("projects", "Projects", Icons.Filled.Notifications),
            NavBarItem("insights", "Insights", Icons.Filled.Star),
        )
        compose.setContent {
            PaperTheme { PaperNavBar(items, selectedKey, { selectedKey = it }, { added = true }) }
        }
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        assertEquals("calendar", selectedKey)
        assertEquals(true, added)
    }

    @Test
    fun undo_snackbar_invokes_undo() {
        var undone = false
        compose.setContent { PaperTheme { UndoSnackbar("Archived", { undone = true }) } }
        compose.onNodeWithText("Archived").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(true, undone)
    }
}
