package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Segmented toggle (Blocks / Plan, Task / Note / Event, Cozy / Compact...). */
@Composable
fun <T> PaperSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String = { it.toString() },
) {
    val colors = Paper.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card)
            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = labelOf(option),
                style = Paper.type.label,
                color = if (isSelected) colors.card else colors.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(PaperRadius.segment - 1.dp))
                    .background(if (isSelected) colors.ink else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}
