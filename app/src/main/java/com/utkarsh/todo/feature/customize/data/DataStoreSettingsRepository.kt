package com.utkarsh.todo.feature.customize.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keeps [AppSettings] in a Jetpack DataStore file on the device, so they survive restarts. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e } // unreadable file: start from defaults
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[ORDER] = SettingsCodec.encodeOrder(next.blockOrder)
            prefs[HIDDEN] = SettingsCodec.encodeHidden(next.hiddenBlocks)
            prefs[BACKGROUND] = next.background.name
            prefs[DENSITY] = next.density.name
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        blockOrder = SettingsCodec.decodeOrder(this[ORDER]),
        hiddenBlocks = SettingsCodec.decodeHidden(this[HIDDEN]),
        background = SettingsCodec.decodeEnum(PaperBackground.entries.toTypedArray(), this[BACKGROUND], PaperBackground.PLAIN),
        density = SettingsCodec.decodeEnum(RowDensity.entries.toTypedArray(), this[DENSITY], RowDensity.COZY),
    )

    private companion object {
        val ORDER = stringPreferencesKey("today_block_order")
        val HIDDEN = stringSetPreferencesKey("today_hidden_blocks")
        val BACKGROUND = stringPreferencesKey("paper_background")
        val DENSITY = stringPreferencesKey("row_density")
    }
}
