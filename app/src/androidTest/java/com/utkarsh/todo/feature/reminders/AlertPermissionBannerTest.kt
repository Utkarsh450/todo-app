package com.utkarsh.todo.feature.reminders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.reminders.platform.MissingPermission
import com.utkarsh.todo.feature.reminders.ui.AlertPermissionBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlertPermissionBannerTest {
    @get:Rule
    val compose = createComposeRule()

    private var allowed = 0

    private fun show(missing: MissingPermission?) {
        allowed = 0
        compose.setContent { PaperTheme { AlertPermissionBanner(missing) { allowed++ } } }
    }

    @Test
    fun nothing_is_drawn_when_nothing_is_missing() {
        show(null)
        compose.onNodeWithTag("alert-permission-banner").assertDoesNotExist()
    }

    @Test
    fun each_missing_permission_says_what_to_allow() {
        show(MissingPermission.FULL_SCREEN)
        compose.onNodeWithText("Allow full-screen alerts so reminders pop up on your screen, like Google Calendar.").assertIsDisplayed()
    }

    @Test
    fun notifications_and_exact_alarms_have_their_own_words() {
        show(MissingPermission.NOTIFICATIONS)
        compose.onNodeWithText("Allow notifications so alerts can reach you.").assertIsDisplayed()
    }

    @Test
    fun exact_alarms_have_their_own_words() {
        show(MissingPermission.EXACT_ALARMS)
        compose.onNodeWithText("Allow exact alarms so alerts ring right on time.").assertIsDisplayed()
    }

    @Test
    fun the_allow_button_reports_a_tap() {
        show(MissingPermission.FULL_SCREEN)
        compose.onNodeWithTag("alert-permission-allow").performClick()
        assertEquals(1, allowed)
    }
}
