package com.utkarsh.todo.feature.habits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.HabitBox
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper

/** Weekly streak tracker body (rows of seven boxes) shown inside the Today "Habits" block. */
@Composable
fun HabitsBlockContent(state: HabitsUiState, onToggle: (String, Int) -> Unit, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(state.summary, Modifier.weight(1f))
            state.dayLetters.forEachIndexed { i, letter ->
                Text(letter, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = if (i == state.todayIndex) colors.accent else colors.muted, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
            }
        }
        state.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("habit-${row.id}")) {
                Text(row.name, style = Paper.type.body.copy(fontSize = 13.5.sp), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                row.kept.forEachIndexed { i, kept ->
                    HabitBox(filled = kept, highlighted = i == state.todayIndex, onToggle = { onToggle(row.id, i) }, label = "${row.name} day ${i + 1}")
                }
            }
        }
        AddHabitField(onAdd)
        state.error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent) }
    }
}

@Composable
private fun AddHabitField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = Paper.type.bodySmall.copy(color = Paper.colors.ink),
            cursorBrush = SolidColor(Paper.colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
            modifier = Modifier.fillMaxWidth().testTag("add-habit-input"),
        )
        if (text.isEmpty()) Text("+ Add habit", style = Paper.type.bodySmall, color = Paper.colors.muted)
    }
}

@Composable
fun HabitsBlock(modifier: Modifier = Modifier, viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HabitsBlockContent(state, viewModel::toggle, viewModel::add, modifier)
}
