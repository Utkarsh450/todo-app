package com.utkarsh.todo.feature.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.component.ProjectDot
import com.utkarsh.todo.core.designsystem.component.RuledTextField
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.feature.projects.domain.ProjectPalette
import kotlinx.coroutines.delay

private const val MESSAGE_VISIBLE_MS = 3_000L

@Composable
fun ProjectsRoute(onBack: () -> Unit, onOpenTask: (String) -> Unit, modifier: Modifier = Modifier, viewModel: ProjectsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectsScreen(
        state = state,
        onBack = onBack,
        onToggleExpanded = viewModel::toggleExpanded,
        onToggleTask = viewModel::toggleTask,
        onOpenTask = onOpenTask,
        onNew = viewModel::openNew,
        onRename = viewModel::openRename,
        onAskDelete = viewModel::askDelete,
        onEditorName = viewModel::onEditorName,
        onEditorColor = viewModel::onEditorColor,
        onEditorSave = viewModel::saveEditor,
        onEditorClose = viewModel::closeEditor,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onRetry = { viewModel.load() },
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onBack: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String) -> Unit,
    onAskDelete: (String) -> Unit,
    onEditorName: (String) -> Unit,
    onEditorColor: (String) -> Unit,
    onEditorSave: () -> Unit,
    onEditorClose: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    LaunchedEffect(state.message) { if (state.message != null) { delay(MESSAGE_VISIBLE_MS); onDismissMessage() } }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = colors.card,
            title = { Text("Delete ${row.name}?", style = Paper.type.headline, color = colors.ink) },
            text = {
                Text(
                    if (row.total == 0) "This project will be removed. This can't be undone."
                    else "This project will be removed. Its ${row.total} task${if (row.total == 1) "" else "s"} stay, but lose the project. This can't be undone.",
                    style = Paper.type.body, color = colors.muted,
                )
            },
            confirmButton = { Text("Delete", style = Paper.type.button, color = colors.accent, modifier = Modifier.clickable(onClick = onConfirmDelete).padding(12.dp).testTag("confirm-delete-project")) },
            dismissButton = { Text("Cancel", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onCancelDelete).padding(12.dp).testTag("cancel-delete-project")) },
        )
    }
    state.editor?.let { editor -> ProjectEditorSheet(editor, onEditorName, onEditorColor, onEditorSave, onEditorClose) }

    Box(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Today", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("projects-back"))
                Box(Modifier.weight(1f))
                MonoLabel("+ New project", color = colors.accent, modifier = Modifier.clickable(onClick = onNew).padding(8.dp).testTag("new-project"))
            }
            Text(
                "Projects",
                style = Paper.type.displayLarge, color = colors.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = if (state.isLoading || state.rows.isEmpty()) 16.dp else 2.dp).testTag("projects-title"),
            )
            if (!state.isLoading && state.error == null && state.rows.isNotEmpty()) {
                MonoLabel("${state.rows.size} project${if (state.rows.size == 1) "" else "s"} · ${state.totalDone} of ${state.total} tasks done", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).testTag("projects-summary"))
            }
            when {
                state.isLoading && state.rows.isEmpty() -> ProjectsSkeleton()
                state.error != null && state.rows.isEmpty() -> ErrorState(state.error, onRetry)
                state.rows.isEmpty() -> EmptyState(onNew)
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.rows, key = { it.id }) { row ->
                        ProjectCard(row, expanded = row.id == state.expandedId, onToggleExpanded, onToggleTask, onOpenTask, onRename, onAskDelete)
                    }
                    item(key = "spacer") { Box(Modifier.height(24.dp)) }
                }
            }
        }
        state.message?.let { message ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(14.dp).clip(RoundedCornerShape(PaperRadius.card)).background(colors.ink).padding(horizontal = 16.dp, vertical = 12.dp).testTag("projects-message"),
            ) { Text(message, style = Paper.type.bodySmall, color = colors.card) }
        }
    }
}

@Composable
private fun ProjectCard(
    row: ProjectRowUi,
    expanded: Boolean,
    onToggleExpanded: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onRename: (String) -> Unit,
    onAskDelete: (String) -> Unit,
) {
    val colors = Paper.colors
    PaperCard(Modifier.fillMaxWidth().testTag("project-${row.id}")) {
        Column(Modifier.fillMaxWidth().clickable { onToggleExpanded(row.id) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectDot(parseHexColor(row.colorHex), size = 10)
                Text(row.name, style = Paper.type.headline, color = colors.ink, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
                MonoLabel("${row.done}/${row.total}", Modifier.testTag("project-count-${row.id}"))
            }
            ProgressBar(row.progress, parseHexColor(row.colorHex), Modifier.padding(top = 12.dp))
        }
        if (expanded) {
            HorizontalDivider(color = colors.rule)
            if (row.tasks.isEmpty()) {
                Text("No tasks yet. Add one with + and #${row.name}.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(16.dp))
            }
            row.tasks.forEach { task ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("project-task-${task.id}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperCheckbox(task.done, { onToggleTask(task.id) }, label = if (task.done) "Reopen ${task.title}" else "Complete ${task.title}")
                    Text(
                        task.title, style = Paper.type.body, color = if (task.done) colors.muted else colors.ink,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f).clickable { onOpenTask(task.id) },
                    )
                    task.timeLabel?.let { MonoLabel(it) }
                }
            }
            HorizontalDivider(color = colors.rule)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                MonoLabel("Rename", color = colors.ink, modifier = Modifier.clickable { onRename(row.id) }.padding(8.dp).testTag("rename-${row.id}"))
                Box(Modifier.weight(1f))
                MonoLabel("Delete", color = colors.accent, modifier = Modifier.clickable { onAskDelete(row.id) }.padding(8.dp).testTag("delete-${row.id}"))
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(Paper.colors.rule)) {
        if (progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(color))
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("projects-empty"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No projects yet", style = Paper.type.headline, color = Paper.colors.ink)
        Text("Group related tasks under a project, then tag them with #name.", style = Paper.type.bodySmall, color = Paper.colors.muted, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
        Text("Make the first one", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onNew).padding(10.dp))
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("projects-error"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Can't reach your projects", style = Paper.type.headline, color = Paper.colors.ink)
        Text(message, style = Paper.type.bodySmall, color = Paper.colors.muted, modifier = Modifier.padding(top = 6.dp))
        Text("Retry", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp).testTag("projects-retry"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectEditorSheet(
    editor: ProjectEditorUi,
    onName: (String) -> Unit,
    onColor: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Paper.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            MonoLabel(if (editor.isNew) "New project" else "Edit project", color = colors.accent)
            RuledTextField(
                value = editor.name, onValueChange = onName, placeholder = "Project name",
                modifier = Modifier.padding(top = 8.dp), focusRequester = focus, imeAction = ImeAction.Done,
                inputModifier = Modifier.testTag("project-name"),
            )
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProjectPalette.forEach { hex ->
                    val selected = hex.equals(editor.colorHex, ignoreCase = true)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(parseHexColor(hex))
                            .then(if (selected) Modifier.border(2.dp, colors.ink, CircleShape) else Modifier)
                            .clickable { onColor(hex) }
                            .semantics { contentDescription = "Colour $hex"; this.selected = selected }
                            .testTag("colour-$hex"),
                    )
                }
            }
            editor.error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 12.dp).testTag("project-error")) }
            val enabled = editor.name.isNotBlank() && !editor.saving
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(PaperRadius.card)).background(colors.ink)
                    .clickable(enabled = enabled, onClick = onSave).padding(vertical = 14.dp).testTag("project-save"),
                contentAlignment = Alignment.Center,
            ) { Text(if (editor.saving) "Saving…" else if (editor.isNew) "Create project" else "Save", style = Paper.type.button, color = colors.card) }
        }
    }
}
