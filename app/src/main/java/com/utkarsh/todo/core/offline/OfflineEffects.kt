package com.utkarsh.todo.core.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Emits when the server is reachable again. Provided by the app shell; empty in previews and tests. */
val LocalReconnected = compositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Runs [action] each time the server comes back, so a screen that showed saved data can load fresh data. */
@Composable
fun OnReconnect(action: () -> Unit) {
    val reconnected = LocalReconnected.current
    val latest by rememberUpdatedState(action)
    LaunchedEffect(reconnected) { reconnected.collect { latest() } }
}

@Composable
fun ProvideReconnected(flow: Flow<Unit>, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalReconnected provides flow, content = content)
