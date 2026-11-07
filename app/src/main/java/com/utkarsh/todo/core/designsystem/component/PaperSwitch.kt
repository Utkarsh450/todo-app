package com.utkarsh.todo.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper

/** 36x20 pill switch from the design. */
@Composable
fun PaperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Toggle",
) {
    val colors = Paper.colors
    val knobOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "switchKnob")
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) colors.ink else colors.switchOff)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.card),
        )
    }
}
