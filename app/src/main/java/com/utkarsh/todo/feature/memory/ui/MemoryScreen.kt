package com.utkarsh.todo.feature.memory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.offline.OnReconnect
import com.utkarsh.todo.feature.ai.domain.MemoryFact
import kotlinx.coroutines.delay

private const val MESSAGE_VISIBLE_MS = 4_000L

@Composable
fun MemoryRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MemoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnReconnect { viewModel.refresh() }
    MemoryScreen(
        state = state,
        onBack = onBack,
        onAdd = viewModel::add,
        onStartEdit = viewModel::startEdit,
        onSaveEdit = viewModel::saveEdit,
        onCancelEdit = viewModel::cancelEdit,
        onDelete = viewModel::delete,
        onAskForgetAll = viewModel::askForgetAll,
        onConfirmForgetAll = viewModel::confirmForgetAll,
        onCancelForgetAll = viewModel::cancelForgetAll,
        onDismissMessage = viewModel::dismissMessage,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onStartEdit: (String) -> Unit,
    onSaveEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (String) -> Unit,
    onAskForgetAll: () -> Unit,
    onConfirmForgetAll: () -> Unit,
    onCancelForgetAll: () -> Unit,
    onDismissMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    LaunchedEffect(state.message) { if (state.message != null) { delay(MESSAGE_VISIBLE_MS); onDismissMessage() } }

    if (state.confirmingForgetAll) {
        AlertDialog(
            onDismissRequest = onCancelForgetAll,
            containerColor = colors.card,
            title = { Text("Forget everything?", style = Paper.type.headline, color = colors.ink) },
            text = { Text("I will no longer know any of this about you. Your conversation and your tasks are not touched. This can't be undone.", style = Paper.type.body, color = colors.muted) },
            confirmButton = { Text("Forget everything", style = Paper.type.button, color = colors.accent, modifier = Modifier.clickable(onClick = onConfirmForgetAll).padding(12.dp).testTag("confirm-forget-all")) },
            dismissButton = { Text("Cancel", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onCancelForgetAll).padding(12.dp).testTag("cancel-forget-all")) },
        )
    }

    Box(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Back", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("memory-back"))
            }
            Text("What I remember", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp).testTag("memory-title"))
            MonoLabel(
                if (state.facts.isEmpty()) "Nothing yet" else "${state.facts.size} thing${if (state.facts.size == 1) "" else "s"} about you",
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            )
            AddRow(onAdd)
            when {
                state.isLoading && state.facts.isEmpty() -> MemorySkeleton(Modifier.padding(top = 8.dp))
                state.error != null && state.facts.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, style = Paper.type.body, color = colors.muted)
                    Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry).padding(10.dp).testTag("memory-retry"))
                }
                state.facts.isEmpty() -> Text(
                    "Nothing yet. As we talk I'll note things that stay true, like your routines, who matters to you and how you like things done. You'll see each one here and can take it back, or tell me something yourself above.",
                    style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("memory-empty"),
                )
                else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    state.groups.forEach { (heading, facts) ->
                        item(key = "heading-$heading") { MonoLabel(heading, Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                        item(key = "group-$heading") {
                            PaperCard(Modifier.fillMaxWidth()) {
                                facts.forEachIndexed { index, fact ->
                                    if (index > 0) HorizontalDivider(color = colors.rule)
                                    if (state.editing == fact.id) EditRow(fact, onSaveEdit, onCancelEdit) else FactRow(fact, onStartEdit, onDelete)
                                }
                            }
                        }
                    }
                    item(key = "forget-all") {
                        Text(
                            "Forget everything", style = Paper.type.button, color = colors.accent,
                            modifier = Modifier.padding(top = 22.dp, bottom = 28.dp).clickable(onClick = onAskForgetAll).padding(vertical = 10.dp).testTag("forget-all"),
                        )
                    }
                }
            }
        }
        state.message?.let { message ->
            Text(
                message, style = Paper.type.bodySmall, color = colors.card,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(12.dp)).background(colors.ink)
                    .clickable(onClick = onDismissMessage).padding(horizontal = 16.dp, vertical = 12.dp).testTag("memory-message"),
            )
        }
    }
}

/** A line to tell the assistant something to remember. */
@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    val colors = Paper.colors
    var draft by remember { mutableStateOf("") }
    val canAdd = draft.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(50)).background(colors.card).border(1.dp, colors.rule, RoundedCornerShape(50)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(end = 8.dp)) {
            BasicTextField(
                value = draft, onValueChange = { draft = it }, textStyle = Paper.type.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("memory-input"),
            )
            if (draft.isEmpty()) Text("Tell me something to remember", style = Paper.type.body, color = colors.muted)
        }
        Text(
            "Add", style = Paper.type.button, color = colors.card,
            modifier = Modifier.alpha(if (canAdd) 1f else 0.4f).clip(RoundedCornerShape(50)).background(colors.ink)
                .clickable(enabled = canAdd) { onAdd(draft); draft = "" }.padding(horizontal = 16.dp, vertical = 9.dp).testTag("memory-add"),
        )
    }
}

@Composable
private fun FactRow(fact: MemoryFact, onStartEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("memory-fact-${fact.id}"), verticalAlignment = Alignment.CenterVertically) {
        Text(fact.text, style = Paper.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Edit", style = Paper.type.label, color = colors.muted, modifier = Modifier.clickable { onStartEdit(fact.id) }.padding(8.dp).testTag("memory-edit-${fact.id}"))
            Text("Delete", style = Paper.type.label, color = colors.accent, modifier = Modifier.clickable { onDelete(fact.id) }.padding(8.dp).testTag("memory-delete-${fact.id}"))
        }
    }
}

@Composable
private fun EditRow(fact: MemoryFact, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    val colors = Paper.colors
    var draft by remember(fact.id) { mutableStateOf(fact.text) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        BasicTextField(
            value = draft, onValueChange = { draft = it }, textStyle = Paper.type.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
            modifier = Modifier.fillMaxWidth().testTag("memory-edit-input-${fact.id}"),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Save", style = Paper.type.button, color = colors.accent, modifier = Modifier.clickable { onSave(fact.id, draft) }.padding(vertical = 6.dp, horizontal = 4.dp).testTag("memory-save-${fact.id}"))
            Text("Cancel", style = Paper.type.button, color = colors.muted, modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 6.dp, horizontal = 4.dp).testTag("memory-cancel-edit"))
        }
    }
}
