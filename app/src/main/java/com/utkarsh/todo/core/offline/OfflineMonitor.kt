package com.utkarsh.todo.core.offline

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the backend can be reached right now. The HTTP layer flips it: a request that fails to connect marks us
 * offline, any answer from the server marks us online again (and announces it on [reconnected] so screens reload).
 */
@Singleton
class OfflineMonitor @Inject constructor() {
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once each time we go from offline back to online. */
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    fun markOffline() {
        _offline.value = true
    }

    fun markOnline() {
        if (_offline.compareAndSet(expect = true, update = false)) _reconnected.tryEmit(Unit)
    }
}
