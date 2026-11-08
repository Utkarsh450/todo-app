package com.utkarsh.todo.feature.customize

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.feature.customize.data.DataStoreSettingsRepository
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.customize.ui.CustomizeScreen
import com.utkarsh.todo.feature.customize.ui.CustomizeViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The real screen, the real view-model and a real DataStore file on the device, wired together. */
class CustomizeFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "customize-${UUID.randomUUID()}.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    private fun repository(): DataStoreSettingsRepository {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        return DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun show(repo: DataStoreSettingsRepository): CustomizeViewModel {
        val vm = CustomizeViewModel(repo)
        compose.setContent {
            PaperTheme {
                val state by vm.uiState.collectAsState()
                CustomizeScreen(state, {}, vm::setVisible, vm::move, vm::setBackground, vm::setDensity, vm::resetToDefaults)
            }
        }
        return vm
    }

    private fun saved(repo: DataStoreSettingsRepository) = runBlocking { repo.settings.first() }

    private fun waitUntilSaved(repo: DataStoreSettingsRepository, check: (AppSettings) -> Boolean) =
        compose.waitUntil(5_000) { check(saved(repo)) }

    @Test
    fun a_switch_flips_on_screen_and_is_saved() {
        val repo = repository()
        show(repo)
        compose.onNodeWithTag("block-switch-SCRATCHPAD").assertIsOff()

        compose.onNodeWithTag("block-switch-SCRATCHPAD").performClick()

        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("block-switch-SCRATCHPAD").assertIsOn() }.isSuccess }
        waitUntilSaved(repo) { it.isVisible(TodayBlock.SCRATCHPAD) }
    }

    @Test
    fun the_arrows_reorder_the_rows_and_the_order_is_saved() {
        val repo = repository()
        show(repo)

        compose.onNodeWithTag("move-up-HABITS").performClick()

        waitUntilSaved(repo) { it.blockOrder == listOf(TodayBlock.PINNED, TodayBlock.HABITS, TodayBlock.TASKS, TodayBlock.SCRATCHPAD) }
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("block-row-HABITS").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag("block-row-TASKS").fetchSemanticsNode().boundsInRoot.top
        }
    }

    @Test
    fun choices_survive_the_app_being_closed_and_reopened() {
        val repo = repository()
        show(repo)
        compose.onNodeWithTag("block-switch-PINNED").performClick()
        compose.onNodeWithText("Dots").performScrollTo().performClick()
        compose.onNodeWithText("Compact").performScrollTo().performClick()
        waitUntilSaved(repo) { it.background == PaperBackground.DOTS && it.density == RowDensity.COMPACT && !it.isVisible(TodayBlock.PINNED) }

        scopes.forEach { it.cancel() } // the process "dies"; only the file remains
        val reopened = repository()
        val after = saved(reopened)
        assertEquals(PaperBackground.DOTS, after.background)
        assertEquals(RowDensity.COMPACT, after.density)
        assertEquals(false, after.isVisible(TodayBlock.PINNED))
    }

    @Test
    fun reset_puts_everything_back_to_the_design_defaults() {
        val repo = repository()
        show(repo)
        compose.onNodeWithTag("block-switch-HABITS").performClick()
        compose.onNodeWithTag("move-down-PINNED").performClick()
        waitUntilSaved(repo) { it != AppSettings() }

        compose.onNodeWithTag("customize-reset").performScrollTo().performClick()

        waitUntilSaved(repo) { it == AppSettings() }
    }
}
