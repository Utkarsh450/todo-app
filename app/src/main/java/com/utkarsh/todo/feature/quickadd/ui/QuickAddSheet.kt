package com.utkarsh.todo.feature.quickadd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.RuledTextField
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.TokenColors
import com.utkarsh.todo.feature.quickadd.domain.QuickAddToken
import com.utkarsh.todo.feature.quickadd.domain.QuickAddType
import com.utkarsh.todo.feature.quickadd.domain.TokenKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, viewModel: QuickAddViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.added.collect { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paper.colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        QuickAddContent(state, viewModel::onTextChange, viewModel::submit, onTypeChange = viewModel::onTypeChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddContent(
    state: QuickAddUiState,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, a Task / Note / Event switch is shown. The Reminders sheet reuses this content without it. */
    onTypeChange: ((QuickAddType) -> Unit)? = null,
    destination: String? = null,
    placeholder: String? = null,
    submitLabel: String? = null,
) {
    val colors = Paper.colors
    val copy = state.type.copy()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        if (onTypeChange != null) {
            PaperSegmentedControl(
                options = QuickAddType.entries,
                selected = state.type,
                onSelect = onTypeChange,
                labelOf = { it.copy().label },
                modifier = Modifier.padding(bottom = 14.dp).testTag("quick-add-type"),
            )
        }
        MonoLabel(destination ?: copy.destination, Modifier.padding(bottom = 8.dp))
        RuledTextField(
            value = state.text,
            onValueChange = onTextChange,
            placeholder = placeholder ?: copy.placeholder,
            focusRequester = focus,
            imeAction = ImeAction.Done,
            inputModifier = Modifier.testTag("quick-add-input"),
        )
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.parsed?.tokens?.forEach { TokenChip(it) }
            if (state.isAllDayEvent) AllDayChip()
        }
        if (state.parsed?.tokens.isNullOrEmpty()) {
            Text(copy.hint, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 14.dp))
        }
        state.error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 10.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = if (state.isSaving) "Adding…" else submitLabel ?: copy.submitLabel,
                style = Paper.type.button,
                color = colors.card,
                modifier = Modifier
                    .clip(RoundedCornerShape(PaperRadius.card))
                    .background(if (state.canSubmit) colors.ink else colors.mutedSoft)
                    .clickable(enabled = state.canSubmit, onClick = onSubmit)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("quick-add-submit"),
            )
        }
    }
}

@Composable
private fun TokenChip(token: QuickAddToken) {
    val (bg, fg, icon) = when (token.kind) {
        TokenKind.DAY -> Triple(TokenColors.DayBg, TokenColors.DayFg, "◷")
        TokenKind.PROJECT -> Triple(TokenColors.ProjectBg, TokenColors.ProjectFg, "■")
        TokenKind.PRIORITY -> Triple(TokenColors.PriorityBg, TokenColors.PriorityFg, "⚑")
        TokenKind.REPEAT -> Triple(TokenColors.RepeatBg, TokenColors.RepeatFg, "↻")
    }
    Text(
        text = "$icon ${token.label}",
        style = Paper.type.label,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun AllDayChip() {
    Text(
        text = "◷ All day",
        style = Paper.type.label,
        color = TokenColors.DayFg,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(TokenColors.DayBg).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** The words the sheet shows for each type. */
private data class TypeCopy(val label: String, val destination: String, val placeholder: String, val hint: String, val submitLabel: String)

private fun QuickAddType.copy(): TypeCopy = when (this) {
    QuickAddType.TASK -> TypeCopy(
        label = "Task",
        destination = "→ Today's list",
        placeholder = "What needs doing?",
        hint = "Type “fri 3pm”, “#project”, “!high”, “every monday”",
        submitLabel = "Add task",
    )
    QuickAddType.NOTE -> TypeCopy(
        label = "Note",
        destination = "→ Today's scratchpad",
        placeholder = "Jot something down…",
        hint = "Added to the end of today's scratchpad, exactly as you type it.",
        submitLabel = "Add note",
    )
    QuickAddType.EVENT -> TypeCopy(
        label = "Event",
        destination = "→ Calendar",
        placeholder = "What's happening?",
        hint = "Type “fri 3pm” for a time, or just a day for an all-day event. “#project” works too.",
        submitLabel = "Add event",
    )
}
