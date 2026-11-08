package com.utkarsh.todo.feature.customize

import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SettingsRepository] for view-model tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    var updates = 0
        private set

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        updates++
        state.value = transform(state.value)
    }

    val current: AppSettings get() = state.value
}
