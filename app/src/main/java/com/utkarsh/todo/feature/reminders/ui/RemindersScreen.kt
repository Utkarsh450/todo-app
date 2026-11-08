package com.utkarsh.todo.feature.reminders.ui

import com.utkarsh.todo.core.offline.OnReconnect
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSwitch
import com.utkarsh.todo.core.designsystem.component.SwipeAction
import com.utkarsh.todo.core.designsystem.component.SwipeActionRow
import com.utkarsh.todo.core.designsystem.component.UndoSnackbar
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.feature.quickadd.ui.QuickAddContent
import kotlinx.coroutines.delay

private const val UNDO_VISIBLE_MS = 4_000L

@Composable
fun RemindersRoute(modifier: Modifier = Modifier, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    OnReconnect { viewModel.refresh() }

    // Android 13+ needs runtime permission before notifications can be shown.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    RemindersScreen(
        state = state,
        onToggle = viewModel::setEnabled,
        onSnoozeRow = viewModel::snoozeRow,
        onDelete = viewModel::delete,
        onSnoozeHero = viewModel::snoozeHero,
        onAdd = { adding = true },
        onUndo = viewModel::undoDelete,
        onDismissUndo = viewModel::dismissUndo,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
    if (adding) AddReminderSheet(onDismiss = { adding = false; viewModel.refresh() })
}

@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onToggle: (String, Boolean) -> Unit,
    onSnoozeRow: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSnoozeHero: (SnoozeOption) -> Unit,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    LaunchedEffect(state.undo) { if (state.undo != null) { delay(UNDO_VISIBLE_MS); onDismissUndo() } }

    Box(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            MonoLabel("Notifications", Modifier.padding(start = 20.dp, top = 16.dp))
            Text("Reminders", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp).testTag("reminders-title"))
            when {
                state.isLoading && state.sections.isEmpty() -> RemindersSkeleton()
                state.error != null && state.sections.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("Can't reach your reminders", style = Paper.type.headline, color = colors.ink)
                    Text(state.error, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                    Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.hero?.let { hero -> item(key = "hero") { NextUpCard(hero, onSnoozeHero) } }
                    if (state.sections.isEmpty()) item(key = "empty") {
                        Text("No reminders. Nothing to remember.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(8.dp))
                    }
                    state.sections.forEach { section ->
                        item(key = "section-${section.kind}") { SectionCard(section, onToggle, onSnoozeRow, onDelete) }
                    }
                    item(key = "add") {
                        Box(
                            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, colors.dashed, RoundedCornerShape(10.dp)).clickable(onClick = onAdd).testTag("add-reminder"),
                            contentAlignment = Alignment.Center,
                        ) { Text("+ New reminder", style = Paper.type.button.copy(fontSize = 13.sp), color = colors.muted) }
                    }
                    item(key = "spacer") { Box(Modifier.height(24.dp)) }
                }
            }
        }
        state.undo?.let { UndoSnackbar(it.message, onUndo, Modifier.align(Alignment.BottomCenter).padding(14.dp)) }
    }
}

@Composable
private fun NextUpCard(hero: HeroUi, onSnooze: (SnoozeOption) -> Unit) {
    val colors = Paper.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.ink).padding(16.dp).testTag("next-up")) {
        MonoLabel(if (hero.snoozed) "Snoozed" else "Next up", color = colors.accentLight)
        Text(hero.title, style = Paper.type.headline.copy(fontSize = 26.sp), color = colors.card, modifier = Modifier.padding(top = 6.dp))
        Text(hero.subtitle, style = Paper.type.monoMeta, color = colors.mutedSoft, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnoozeOption.entries.forEach { option ->
                Text(
                    option.label,
                    style = Paper.type.label,
                    color = colors.card,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.card.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable { onSnooze(option) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionCard(section: ReminderSection, onToggle: (String, Boolean) -> Unit, onSnooze: (String) -> Unit, onDelete: (String) -> Unit) {
    val colors = Paper.colors
    PaperCard(Modifier.fillMaxWidth()) {
        MonoLabel(section.label, color = if (section.kind == SectionKind.OVERDUE) colors.accent else colors.muted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
        section.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = colors.rule)
            SwipeActionRow(
                rightAction = SwipeAction("SNOOZE 1H →", colors.ink),
                leftAction = SwipeAction("← DELETE", colors.accent),
                onSwipeRight = { onSnooze(row.id) },
                onSwipeLeft = { onDelete(row.id) },
                modifier = Modifier.testTag("reminder-${row.id}"),
            ) {
                ReminderRowContent(row, overdue = section.kind == SectionKind.OVERDUE, onToggle = { onToggle(row.id, it) })
            }
        }
    }
}

@Composable
private fun ReminderRowContent(row: ReminderRow, overdue: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth().background(colors.card).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(56.dp).alpha(if (row.enabled) 1f else 0.4f)) {
            Text(row.time, style = Paper.type.monoMeta.copy(fontSize = 15.sp), color = if (overdue) colors.accent else colors.ink)
            Text(row.meridiem, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = colors.muted)
        }
        Column(Modifier.weight(1f).alpha(if (row.enabled) 1f else 0.4f)) {
            Text(row.title, style = Paper.type.body, color = if (overdue) colors.accent else colors.ink)
            row.meta?.let { MonoLabel(it, Modifier.padding(top = 2.dp)) }
            (row.repeat ?: row.location)?.let { Text(it, style = Paper.type.monoMeta, color = colors.accent, modifier = Modifier.padding(top = 2.dp)) }
        }
        PaperSwitch(checked = row.enabled, onCheckedChange = onToggle, label = "Reminder ${row.title}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderSheet(onDismiss: () -> Unit, viewModel: AddReminderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.added.collect { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Paper.colors.paper,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        QuickAddContent(
            state = state,
            onTextChange = viewModel::onTextChange,
            onSubmit = viewModel::submit,
            destination = "→ Reminders",
            placeholder = "Remind me to…",
            submitLabel = "Add reminder",
        )
    }
}
