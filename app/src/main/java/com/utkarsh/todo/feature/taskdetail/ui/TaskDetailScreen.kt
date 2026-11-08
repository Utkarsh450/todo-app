package com.utkarsh.todo.feature.taskdetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.component.ProjectDot
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.PriorityColors
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.feature.breakdown.ui.BreakdownSheet
import com.utkarsh.todo.feature.tasks.domain.Priority

@Composable
fun TaskDetailRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: TaskDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBreakdown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.closed.collect { onBack() } }
    if (showBreakdown) {
        BreakdownSheet(onDismiss = {
            showBreakdown = false
            viewModel.refresh()
        })
    }
    TaskDetailScreen(
        state = state,
        onBack = onBack,
        onToggleDone = viewModel::toggleDone,
        onToggleSubtask = viewModel::toggleSubtask,
        onAddSubtask = viewModel::addSubtask,
        onDelete = viewModel::delete,
        onRetry = viewModel::load,
        onBreakDown = { showBreakdown = true },
        onToggleRemind = viewModel::toggleRemind,
        onNotesChange = viewModel::onNotesChange,
        modifier = modifier,
    )
}

@Composable
fun TaskDetailScreen(
    state: TaskDetailUiState,
    onBack: () -> Unit,
    onToggleDone: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBreakDown: () -> Unit = {},
    onToggleRemind: () -> Unit = {},
    onNotesChange: (String) -> Unit = {},
) {
    val colors = Paper.colors
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.card,
            title = { Text("Delete this task?", style = Paper.type.headline, color = colors.ink) },
            text = { Text("It, its subtasks and its reminder will be removed. This can't be undone.", style = Paper.type.body, color = colors.muted) },
            confirmButton = {
                Text(
                    "Delete", style = Paper.type.button, color = colors.accent,
                    modifier = Modifier.clickable { confirmDelete = false; onDelete() }.padding(12.dp).testTag("confirm-delete"),
                )
            },
            dismissButton = {
                Text("Cancel", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable { confirmDelete = false }.padding(12.dp).testTag("cancel-delete"))
            },
        )
    }
    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        val task = state.task
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Today", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Box(Modifier.weight(1f))
            if (task != null) MonoLabel("Delete", color = colors.accent, modifier = Modifier.clickable { confirmDelete = true }.padding(8.dp).testTag("delete-task"))
        }
        when {
            task != null -> Content(task, state.error, onToggleDone, onToggleSubtask, onAddSubtask, onBreakDown, onToggleRemind, onNotesChange)
            state.isLoading -> TaskDetailSkeleton()
            else -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Couldn't open this task", style = Paper.type.headline, color = colors.ink)
                Text(state.error.orEmpty(), style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
            }
        }
    }
}

@Composable
private fun Content(
    task: TaskDetailUi,
    error: String?,
    onToggleDone: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onBreakDown: () -> Unit,
    onToggleRemind: () -> Unit,
    onNotesChange: (String) -> Unit,
) {
    val colors = Paper.colors
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            MonoLabel(task.statusLabel, color = colors.muted)
            Text(task.title, style = Paper.type.displayLarge.copy(fontSize = 38.sp), color = colors.ink, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp).testTag("detail-title"))

            MetaRow("Due", task.dueLabel ?: "No date")
            RemindRow(task.remind, onToggleRemind)
            MetaRow("Priority", "⚑ ${task.priority.label()}", valueColor = task.priority.color())
            task.projectName?.let { name ->
                MetaRow("Project", name, leading = { ProjectDot(parseHexColor(task.projectColorHex.orEmpty()), size = 8) })
            }
            task.repeat?.let { MetaRow("Repeat", "↻ $it") }

            Row(Modifier.padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("Subtasks", Modifier.weight(1f))
                if (task.subtasks.isNotEmpty()) MonoLabel("${task.subtasksDone}/${task.subtasks.size}")
            }
            if (task.subtasks.isEmpty()) {
                Text("No subtasks. Keep it simple.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp))
            }
            task.subtasks.forEach { sub ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperCheckbox(sub.done, { onToggleSubtask(sub.id) }, label = "Subtask ${sub.title}")
                    Text(
                        sub.title, style = Paper.type.body, color = colors.ink,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.alpha(if (sub.done) 0.45f else 1f),
                    )
                }
            }
            AddSubtaskField(onAddSubtask)
            MonoLabel(
                "✦ Break it down", color = colors.accent,
                modifier = Modifier.padding(top = 6.dp).clickable(onClick = onBreakDown).padding(vertical = 10.dp).testTag("break-it-down"),
            )

            MonoLabel("Notes", Modifier.padding(top = 22.dp, bottom = 6.dp))
            NotesField(task.notes, onNotesChange)
            error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 14.dp)) }
            Box(Modifier.padding(bottom = 16.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = if (task.done) "Reopen" else "Mark done",
                style = Paper.type.button,
                color = colors.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PaperRadius.card))
                    .background(colors.ink)
                    .clickable(onClick = onToggleDone)
                    .padding(vertical = 14.dp)
                    .testTag("toggle-done"),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String, valueColor: Color = Paper.colors.ink, leading: (@Composable () -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(label, Modifier.width(96.dp))
            leading?.let { it(); Box(Modifier.width(8.dp)) }
            Text(value, style = Paper.type.body, color = valueColor)
        }
        HorizontalDivider(color = Paper.colors.rule)
    }
}

/** The REMIND row: tap to set (or remove) a reminder one hour before the due time. */
@Composable
private fun RemindRow(remind: RemindUi, onToggle: () -> Unit) {
    val colors = Paper.colors
    val actionable = remind.state == RemindState.OFF || remind.state == RemindState.ON
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = actionable, onClick = onToggle).padding(vertical = 12.dp).testTag("remind-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("Remind", Modifier.width(96.dp))
            Text(
                remind.label, style = Paper.type.body,
                color = when (remind.state) {
                    RemindState.OFF -> colors.accent
                    RemindState.ON -> colors.ink
                    else -> colors.muted
                },
            )
            if (remind.state == RemindState.ON) {
                Box(Modifier.weight(1f))
                MonoLabel("Remove", color = colors.muted)
            }
        }
        HorizontalDivider(color = colors.rule)
    }
}

/** Notes are edited in place; the view model saves them shortly after typing stops. */
@Composable
private fun NotesField(notes: String, onChange: (String) -> Unit) {
    val colors = Paper.colors
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = notes,
            onValueChange = onChange,
            textStyle = Paper.type.note.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().testTag("notes-input"),
        )
        if (notes.isEmpty()) Text("+ Add notes", style = Paper.type.note, color = colors.muted)
    }
}

@Composable
private fun AddSubtaskField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val colors = Paper.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = Paper.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
            modifier = Modifier.fillMaxWidth().testTag("add-subtask-input"),
        )
        if (text.isEmpty()) Text("+ Add subtask", style = Paper.type.body, color = colors.muted)
    }
}

private fun Priority.label() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun Priority.color(): Color = when (this) {
    Priority.URGENT -> PriorityColors.Urgent
    Priority.HIGH -> PriorityColors.High
    Priority.NORMAL -> PriorityColors.Normal
    Priority.LOW -> PriorityColors.Low
}
