package com.utkarsh.todo.core.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Feeds the "Offline" banner and checks whether the server is back. */
@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val monitor: OfflineMonitor,
    private val health: HealthApi,
) : ViewModel() {
    val offline: StateFlow<Boolean> = monitor.offline
    val reconnected: Flow<Unit> = monitor.reconnected

    /** Asks the server whether it is there; if so the app goes back online and screens reload. */
    fun recheck() {
        viewModelScope.launch {
            runCatching { health.health() }.onSuccess { monitor.markOnline() }
        }
    }
}
