package com.utkarsh.todo.core.offline

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.utkarsh.todo.core.designsystem.component.OfflineBanner
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OfflineUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_banner_says_what_is_going_on_and_offers_retry() {
        var retried = 0
        compose.setContent { PaperTheme { OfflineBanner(onRetry = { retried++ }) } }
        compose.onNodeWithTag("offline-banner").assertIsDisplayed()
        compose.onNodeWithText("OFFLINE · SHOWING SAVED DATA").assertIsDisplayed()
        compose.onNodeWithTag("offline-retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun a_screen_reloads_each_time_the_server_comes_back() {
        val reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var reloads = 0
        compose.setContent { PaperTheme { ProvideReconnected(reconnected) { OnReconnect { reloads++ } } } }
        compose.waitForIdle()
        assertEquals(0, reloads)

        runBlocking { reconnected.emit(Unit) }
        compose.waitUntil(3_000) { reloads == 1 }
        runBlocking { reconnected.emit(Unit) }
        compose.waitUntil(3_000) { reloads == 2 }
    }

    @Test
    fun without_an_app_shell_nothing_happens_and_nothing_crashes() {
        var reloads = 0
        compose.setContent { PaperTheme { OnReconnect { reloads++ } } }
        compose.waitForIdle()
        assertEquals(0, reloads)
    }

    @Test
    fun the_saved_copy_lives_in_the_app_s_own_folder_and_survives_reopening() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "offline-test-${System.nanoTime()}")
        try {
            FileOfflineStore(dir).write("http://10.0.2.2:3000/api/tasks", """[{"id":"1"}]""".toByteArray(), "application/json")
            val saved = FileOfflineStore(dir).read("http://10.0.2.2:3000/api/tasks")!!
            assertEquals("""[{"id":"1"}]""", String(saved.body))
            assertNull(FileOfflineStore(dir).read("http://10.0.2.2:3000/api/other"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
