package com.utkarsh.todo.feature.notes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.theme.Paper

@Composable
fun NoteBlockContent(state: NoteUiState, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = Paper.type.note.copy(color = Paper.colors.ink)
    Box(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        BasicTextField(
            value = state.text,
            onValueChange = onTextChange,
            textStyle = style,
            cursorBrush = SolidColor(Paper.colors.accent),
            modifier = Modifier.fillMaxWidth().testTag("note-input"),
        )
        if (state.text.isEmpty()) Text("Jot something down…", style = style, color = Paper.colors.mutedSoft)
    }
}

@Composable
fun NoteBlock(modifier: Modifier = Modifier, viewModel: NoteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NoteBlockContent(state, viewModel::onTextChange, modifier)
}
