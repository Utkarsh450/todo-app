package com.utkarsh.todo.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.feature.today.domain.TaskItem

/**
 * Task row with the design's swipe gestures: right = move to tomorrow (accent),
 * left = archive (grey). Completion toggles via the checkbox; tapping the row opens the task.
 */
@Composable
fun SwipeableTaskRow(
    item: TaskItem,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMoveToTomorrow: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.4f })
    // Fire the action once the swipe settles past the threshold, then reset: if the item is removed the
    // row leaves composition, and if the action was rejected (nothing to move) the row snaps back.
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onMoveToTomorrow()
            SwipeToDismissBoxValue.EndToStart -> onArchive()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.snapTo(SwipeToDismissBoxValue.Settled)
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val (bg, label, align) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(colors.accent, "TOMORROW →", Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(colors.archive, "← ARCHIVE", Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, "", Alignment.Center)
            }
            Box(Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp), contentAlignment = align) {
                Text(label, style = Paper.type.monoLabel, color = colors.card)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = Paper.density.rowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperCheckbox(checked = item.done, onCheckedChange = { onToggle() }, label = "Complete ${item.title}")
            Text(
                text = item.title,
                style = Paper.type.body,
                color = colors.ink,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).alpha(if (item.done) 0.45f else 1f),
            )
            if (item.timeLabel.isNotEmpty()) {
                Text(item.timeLabel, style = Paper.type.monoMeta, color = colors.muted)
            }
        }
    }
}
