package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper

/** A coloured label revealed behind a row while it is swiped. */
data class SwipeAction(val label: String, val color: Color)

/**
 * Wraps [content] with the design's two-way swipe: start-to-end triggers [onSwipeRight],
 * end-to-start triggers [onSwipeLeft]. The row snaps back after the callback, so it either
 * leaves composition (item removed) or stays put (action rejected).
 */
@Composable
fun SwipeActionRow(
    rightAction: SwipeAction,
    leftAction: SwipeAction,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.4f })
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onSwipeRight()
            SwipeToDismissBoxValue.EndToStart -> onSwipeLeft()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val (action, align) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> rightAction to Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> leftAction to Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> null to Alignment.Center
            }
            Box(Modifier.fillMaxSize().background(action?.color ?: Color.Transparent).padding(horizontal = 16.dp), contentAlignment = align) {
                action?.let { Text(it.label, style = Paper.type.monoLabel, color = Paper.colors.card) }
            }
        },
    ) { content() }
}
