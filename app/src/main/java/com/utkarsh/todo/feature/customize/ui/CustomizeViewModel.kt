package com.utkarsh.todo.feature.customize.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.SettingsRepository
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomizeUiState(
    val settings: AppSettings = AppSettings(),
    /** False until the saved settings have been read, so the screen does not flash the defaults. */
    val isLoaded: Boolean = false,
)

/** Reads and edits the app settings; every change is saved at once, there is no Save button. */
@HiltViewModel
class CustomizeViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<CustomizeUiState> = repository.settings
        .map { CustomizeUiState(it, isLoaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomizeUiState())

    fun setVisible(block: TodayBlock, visible: Boolean) = edit { it.withVisible(block, visible) }

    fun move(block: TodayBlock, delta: Int) = edit { it.moved(block, delta) }

    fun setBackground(background: PaperBackground) = edit { it.copy(background = background) }

    fun setDensity(density: RowDensity) = edit { it.copy(density = density) }

    fun resetToDefaults() = edit { AppSettings() }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
