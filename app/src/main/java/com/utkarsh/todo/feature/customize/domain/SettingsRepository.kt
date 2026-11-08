package com.utkarsh.todo.feature.customize.domain

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Emits the saved settings, then again after every change. */
    val settings: Flow<AppSettings>

    /** Atomically applies [transform] to the latest saved settings and saves the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
