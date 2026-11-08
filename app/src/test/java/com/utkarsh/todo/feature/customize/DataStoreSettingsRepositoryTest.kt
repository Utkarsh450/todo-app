package com.utkarsh.todo.feature.customize

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.utkarsh.todo.feature.customize.data.DataStoreSettingsRepository
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Integration tests against a real DataStore file on disk, not a fake. */
class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun closeStores() = scopes.forEach { it.cancel() }

    private fun openStore(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun file() = File(folder.root, "settings.preferences_pb")

    @Test
    fun a_fresh_install_gets_the_design_defaults() = runBlocking {
        assertEquals(AppSettings(), DataStoreSettingsRepository(openStore(file())).settings.first())
    }

    @Test
    fun an_update_is_saved_and_emitted() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore(file()))
        repo.update { it.withVisible(TodayBlock.SCRATCHPAD, true).moved(TodayBlock.HABITS, -1).copy(background = PaperBackground.DOTS, density = RowDensity.COMPACT) }
        val saved = repo.settings.first()
        assertEquals(TodayBlock.entries.toSet(), saved.visibleBlocks.toSet())
        assertEquals(listOf(TodayBlock.PINNED, TodayBlock.HABITS, TodayBlock.TASKS, TodayBlock.SCRATCHPAD), saved.blockOrder)
        assertEquals(PaperBackground.DOTS, saved.background)
        assertEquals(RowDensity.COMPACT, saved.density)
    }

    @Test
    fun settings_survive_a_restart_of_the_app() = runBlocking {
        val first = openStore(file())
        val changed = AppSettings().withVisible(TodayBlock.PINNED, false).moved(TodayBlock.TASKS, -1).copy(background = PaperBackground.LINES)
        DataStoreSettingsRepository(first).update { changed }
        scopes.forEach { it.cancel() } // the process dies: the file is the only thing left

        val afterRestart = DataStoreSettingsRepository(openStore(file())).settings.first()
        assertEquals(changed, afterRestart)
    }

    @Test
    fun hiding_everything_is_remembered_and_not_mistaken_for_never_saved() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore(file()))
        repo.update { it.copy(hiddenBlocks = emptySet()) }
        assertEquals(emptySet<TodayBlock>(), repo.settings.first().hiddenBlocks)
    }

    @Test
    fun garbage_in_the_file_falls_back_to_defaults_instead_of_crashing() = runBlocking {
        val store = openStore(file())
        store.edit {
            it[stringPreferencesKey("paper_background")] = "DIAGONAL"
            it[stringPreferencesKey("row_density")] = "TINY"
            it[stringPreferencesKey("today_block_order")] = "WHAT,HABITS"
        }
        val s = DataStoreSettingsRepository(store).settings.first()
        assertEquals(PaperBackground.PLAIN, s.background)
        assertEquals(RowDensity.COZY, s.density)
        assertEquals(listOf(TodayBlock.HABITS, TodayBlock.PINNED, TodayBlock.TASKS, TodayBlock.SCRATCHPAD), s.blockOrder)
    }

    @Test
    fun simultaneous_updates_are_all_applied_without_losing_any() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore(file()))
        // Each update toggles one distinct block; if any read-modify-write raced, a toggle would be lost.
        val toggles = listOf(TodayBlock.PINNED, TodayBlock.TASKS, TodayBlock.HABITS)
        toggles.map { block -> async(Dispatchers.IO) { repo.update { it.withVisible(block, false) } } }.awaitAll()
        assertEquals(TodayBlock.entries.toSet(), repo.settings.first().hiddenBlocks)
    }

    @Test
    fun a_flow_collector_sees_each_change_once() = runBlocking {
        val repo = DataStoreSettingsRepository(openStore(file()))
        repo.update { it.copy(density = RowDensity.COMPACT) }
        repo.update { it.copy(density = RowDensity.COMPACT) } // same value again: no new emission needed
        assertEquals(RowDensity.COMPACT, repo.settings.first().density)
    }
}
