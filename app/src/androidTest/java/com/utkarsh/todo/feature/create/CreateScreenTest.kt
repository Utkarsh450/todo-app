package com.utkarsh.todo.feature.create

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.create.domain.CreateForm
import com.utkarsh.todo.feature.create.domain.CreateType
import com.utkarsh.todo.feature.create.ui.CreateActions
import com.utkarsh.todo.feature.create.ui.CreateScreen
import com.utkarsh.todo.feature.create.ui.CreateUiState
import com.utkarsh.todo.feature.projects.domain.Project
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val events = mutableListOf<String>()
    private val projects = listOf(Project("p1", "Design", "#935EDF"), Project("p2", "Work", "#E05D3D"))

    private fun task() = CreateForm.start(CreateType.TASK, today, null, LocalDateTime.of(2026, 11, 12, 10, 20))
    private fun event() = CreateForm.start(CreateType.EVENT, today, null, LocalDateTime.of(2026, 11, 12, 10, 20))

    private fun show(form: CreateForm = task(), error: String? = null, saving: Boolean = false) {
        events.clear()
        compose.setContent {
            PaperTheme {
                CreateScreen(
                    state = CreateUiState(form, projects, saving, error),
                    actions = CreateActions(
                        onClose = { events += "close" },
                        onType = { events += "type:$it" },
                        onTitle = { events += "title:$it" },
                        onDate = { events += "date:$it" },
                        onTime = { events += "time:$it" },
                        onDuration = { events += "duration:$it" },
                        onAllDay = { events += "allday" },
                        onToggleAlert = { events += "alert:$it" },
                        onAddAlert = { events += "addalert:$it" },
                        onRepeat = { events += "repeat:$it" },
                        onAlertStyle = { events += "style:$it" },
                        onNotes = { events += "notes:$it" },
                        onPriority = { events += "priority:$it" },
                        onProject = { events += "project:$it" },
                        onSave = { events += "save" },
                    ),
                    today = today,
                )
            }
        }
    }

    private fun tap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test
    fun a_task_offers_day_time_alerts_repeat_priority_and_project_chips() {
        show()
        listOf("day-today", "day-tomorrow", "day-pick", "time-none", "time-9", "time-pick", "alert-0", "alert-1440", "alert-custom",
            "repeat-never", "repeat-day", "repeat-week", "repeat-month", "repeat-custom-open", "priority-urgent", "priority-low", "project-p1", "project-p2",
        ).forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("duration-60").assertDoesNotExist()
    }

    @Test
    fun an_event_has_a_length_and_no_repeat_or_priority() {
        show(event())
        compose.onNodeWithTag("duration-60").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("duration-allday").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("time-none").assertDoesNotExist()
        compose.onNodeWithTag("repeat-never").assertDoesNotExist()
        compose.onNodeWithTag("priority-high").assertDoesNotExist()
    }

    @Test
    fun the_type_switch_and_close_report() {
        show()
        compose.onNodeWithText("Event").performClick()
        compose.onNodeWithTag("create-close").performClick()
        assertEquals(listOf("type:EVENT", "close"), events)
    }

    @Test
    fun chips_report_what_was_tapped() {
        show()
        tap("day-tomorrow")
        tap("time-15")
        tap("time-none")
        tap("alert-30")
        tap("repeat-week")
        tap("priority-urgent")
        tap("project-p2")
        assertEquals(
            listOf("date:2026-11-13", "time:15:00", "time:null", "alert:30", "repeat:Every Week", "priority:URGENT", "project:p2"),
            events,
        )
    }

    @Test
    fun the_event_length_chips_report() {
        show(event())
        tap("duration-120")
        tap("duration-allday")
        assertEquals(listOf("duration:120", "allday"), events)
    }

    @Test
    fun typing_a_title_and_saving_report() {
        show()
        compose.onNodeWithTag("create-title").performTextInput("Pay rent")
        compose.onNodeWithTag("create-save").performClick()
        assertEquals(listOf("title:Pay rent", "save"), events)
    }

    @Test
    fun picking_a_date_opens_the_full_date_picker() {
        show()
        tap("day-pick")
        compose.onNodeWithTag("date-ok").assertIsDisplayed()
        compose.onNodeWithTag("date-ok").performClick()
        assertEquals(listOf("date:2026-11-12"), events) // OK keeps the selected day
    }

    @Test
    fun picking_a_time_opens_the_full_time_picker() {
        show(task().copy(time = LocalTime.of(14, 30)))
        tap("time-pick")
        compose.onNodeWithTag("time-ok").assertIsDisplayed()
        compose.onNodeWithTag("time-ok").performClick()
        assertEquals(listOf("time:14:30"), events)
    }

    @Test
    fun a_custom_alert_is_built_from_a_number_and_a_unit() {
        show()
        tap("alert-custom")
        compose.onNodeWithTag("alerts-sheet").assertIsDisplayed()
        compose.onNodeWithTag("alert-amount").performTextClearance()
        compose.onNodeWithTag("alert-amount").performTextInput("2")
        compose.onNodeWithTag("alert-unit-hours").performClick()
        compose.onNodeWithTag("alert-add").performClick()
        assertEquals(listOf("addalert:120"), events)
    }

    @Test
    fun a_custom_alert_needs_a_number() {
        show()
        tap("alert-custom")
        compose.onNodeWithTag("alert-amount").performTextClearance()
        compose.onNodeWithTag("alert-add").performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun a_custom_repeat_reads_every_n_units() {
        show()
        tap("repeat-custom-open")
        compose.onNodeWithTag("repeat-sheet").assertIsDisplayed()
        compose.onNodeWithTag("repeat-amount").performTextClearance()
        compose.onNodeWithTag("repeat-amount").performTextInput("3")
        compose.onNodeWithTag("repeat-unit-months").performClick()
        compose.onNodeWithTag("repeat-set").performClick()
        assertEquals(listOf("repeat:Every 3 Months"), events)
    }

    @Test
    fun a_custom_alert_and_repeat_show_up_as_chips() {
        show(task().copy(alerts = setOf(45), repeat = "Every 2 Weeks"))
        compose.onNodeWithTag("alert-45").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("45 min before").assertExists()
        compose.onNodeWithTag("repeat-custom").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_preview_shows_how_it_will_look() {
        show(task().copy(title = "Pay rent", time = LocalTime.of(18, 0), repeat = "Every Week", alerts = setOf(30), projectId = "p1"))
        compose.onNodeWithTag("create-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("preview-title").assertIsDisplayed()
        compose.onNodeWithTag("preview-when").assertIsDisplayed()
        compose.onNodeWithText("Today · 6:00 PM · ↻ Weekly · 🔔 30 min before").assertExists()
        compose.onNodeWithText("● Design").assertExists()
    }

    @Test
    fun an_untitled_preview_says_untitled_and_an_error_and_saving_state_show() {
        show(task(), error = "Give it a title", saving = true)
        compose.onNodeWithText("Untitled", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("create-error").assertIsDisplayed()
        compose.onNodeWithText("Saving…").assertIsDisplayed()
    }

    @Test
    fun priorities_are_all_offered() {
        show()
        Priority.entries.forEach { compose.onNodeWithTag("priority-${it.name.lowercase()}").performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun there_are_no_style_chips_until_there_is_an_alert() {
        show()
        compose.onNodeWithTag("style-alarm").assertDoesNotExist()
        compose.onNodeWithTag("style-notification").assertDoesNotExist()
    }

    @Test
    fun the_alert_style_chips_report_once_there_is_an_alert() {
        show(task().copy(alerts = setOf(30)))
        compose.onNodeWithTag("style-notification").performScrollTo().assertIsDisplayed()
        tap("style-alarm")
        assertEquals(listOf("style:ALARM"), events)
    }

    @Test
    fun a_task_has_a_notes_field() {
        show()
        compose.onNodeWithText("Add notes or a link…").assertIsDisplayed()
        compose.onNodeWithTag("create-notes").performTextInput("ask about the card")
        assertEquals(listOf("notes:ask about the card"), events)
    }

    @Test
    fun an_event_has_no_notes_field() {
        show(event())
        compose.onNodeWithTag("create-notes").assertDoesNotExist()
    }

    @Test
    fun the_preview_says_alarm_when_the_alert_is_an_alarm() {
        show(task().copy(title = "Wake up", time = LocalTime.of(6, 0), alerts = setOf(0), alertStyle = AlertStyle.ALARM))
        compose.onNodeWithText("Today · 6:00 AM · 🔔 At time · alarm").performScrollTo().assertExists()
    }
}
