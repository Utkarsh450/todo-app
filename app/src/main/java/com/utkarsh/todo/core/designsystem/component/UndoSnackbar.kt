package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Dark toast with an "Undo" action, shown after swipe actions. */
@Composable
fun UndoSnackbar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(PaperRadius.toast))
            .background(colors.ink)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message, style = Paper.type.button, color = colors.card)
        Text(
            "Undo",
            style = Paper.type.button,
            color = colors.accentLight,
            modifier = Modifier.clickable(onClick = onUndo).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
