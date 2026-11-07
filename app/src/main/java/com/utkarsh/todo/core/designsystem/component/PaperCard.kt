package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Off-white card with a hairline border, the basic surface of the paper system. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Paper.colors
    val shape = RoundedCornerShape(PaperRadius.card)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.rule, shape),
        content = content,
    )
}

/** Uppercase monospace caption ("SAT 7 NOV · W45", "DUE 2PM"). */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Paper.colors.muted) {
    Text(text = text.uppercase(), style = Paper.type.monoLabel, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Small coloured dot identifying a project. */
@Composable
fun ProjectDot(color: Color, modifier: Modifier = Modifier, size: Int = 6) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

/** Dot + uppercase project name, as used in agendas. */
@Composable
fun ProjectTag(name: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProjectDot(color)
        Text(
            text = name.uppercase(),
            style = Paper.type.monoMeta.copy(letterSpacing = 0.06.em),
            color = Paper.colors.muted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Big number + caption tile used in Insights. */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    PaperCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = Paper.type.headline, color = if (accent) Paper.colors.accent else Paper.colors.ink)
            MonoLabel(label)
        }
    }
}
