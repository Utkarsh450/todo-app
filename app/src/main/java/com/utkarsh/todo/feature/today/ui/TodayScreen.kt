package com.utkarsh.todo.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.ProjectTag
import com.utkarsh.todo.core.designsystem.component.UndoSnackbar
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.customize.ui.CustomizeViewModel
import com.utkarsh.todo.feature.habits.ui.HabitsBlock
import com.utkarsh.todo.feature.notes.ui.NoteBlock
import com.utkarsh.todo.feature.today.domain.TaskItem
import com.utkarsh.todo.feature.today.domain.TodayView
import kotlinx.coroutines.delay

private const val UNDO_VISIBLE_MS = 4_000L

@Composable
fun TodayRoute(
    onOpenTask: (String) -> Unit,
    onCustomize: () -> Unit,
    onAssistant: () -> Unit,
    onBrainDump: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
    settingsViewModel: CustomizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        onViewChange = viewModel::setView,
        onToggle = viewModel::toggleDone,
        onOpen = onOpenTask,
        onMoveToTomorrow = viewModel::moveToTomorrow,
        onArchive = viewModel::archive,
        onUndo = viewModel::undo,
        onDismissUndo = viewModel::dismissUndo,
        onRetry = viewModel::refresh,
        modifier = modifier,
        habitsContent = { HabitsBlock() },
        noteContent = { NoteBlock() },
        blocks = settings.settings.visibleBlocks,
        onCustomize = onCustomize,
        onAssistant = onAssistant,
        onBrainDump = onBrainDump,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onViewChange: (TodayView) -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMoveToTomorrow: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional extra blocks, supplied by the route so this screen stays free of Hilt for previews/tests. */
    habitsContent: (@Composable () -> Unit)? = null,
    noteContent: (@Composable () -> Unit)? = null,
    /** Which blocks to show, in order (see Customize). Defaults to all of them. */
    blocks: List<TodayBlock> = TodayBlock.entries,
    /** Shows the Customize button when set. */
    onCustomize: (() -> Unit)? = null,
    /** Shows the ✦ Ask Grok button when set. */
    onAssistant: (() -> Unit)? = null,
    /** Shows the brain dump button when set. */
    onBrainDump: (() -> Unit)? = null,
) {
    val colors = Paper.colors
    LaunchedEffect(state.undo) { if (state.undo != null) { delay(UNDO_VISIBLE_MS); onDismissUndo() } }

    Box(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(state.caption, Modifier.weight(1f))
                onBrainDump?.let { open ->
                    Text(
                        "✎", style = Paper.type.title, color = colors.muted,
                        modifier = Modifier.semantics { contentDescription = "Brain dump" }.clip(RoundedCornerShape(8.dp)).clickable(onClick = open).padding(horizontal = 10.dp, vertical = 4.dp).testTag("open-braindump"),
                    )
                }
                onAssistant?.let { open ->
                    Text(
                        "✦", style = Paper.type.title, color = colors.accent,
                        modifier = Modifier.semantics { contentDescription = "Ask Grok" }.clip(RoundedCornerShape(8.dp)).clickable(onClick = open).padding(horizontal = 10.dp, vertical = 4.dp).testTag("open-assistant"),
                    )
                }
                onCustomize?.let { open ->
                    Icon(
                        PaperIcons.Customize,
                        contentDescription = "Customize",
                        tint = colors.muted,
                        modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = open).padding(8.dp).testTag("open-customize"),
                    )
                }
                PaperSegmentedControl(
                    options = TodayView.entries,
                    selected = state.view,
                    onSelect = onViewChange,
                    labelOf = { if (it == TodayView.BLOCKS) "Blocks" else "Plan" },
                )
            }
            Text(
                text = buildAnnotatedString {
                    append("Today")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.muted, fontSize = 26.sp)) { append(" — ${state.left} to go") }
                },
                style = Paper.type.displayLarge,
                color = colors.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp).testTag("today-title"),
            )
            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colors.ink) }
                state.errorMessage != null && state.items.isEmpty() -> ErrorState(state.errorMessage, onRetry)
                state.view == TodayView.BLOCKS -> BlocksView(state, blocks, onToggle, onOpen, onMoveToTomorrow, onArchive, habitsContent, noteContent)
                else -> PlanView(state.items)
            }
        }
        state.undo?.let {
            UndoSnackbar(it.message, onUndo, Modifier.align(Alignment.BottomCenter).padding(14.dp))
        }
    }
}

@Composable
private fun BlocksView(
    state: TodayUiState,
    blocks: List<TodayBlock>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMoveToTomorrow: (String) -> Unit,
    onArchive: (String) -> Unit,
    habitsContent: (@Composable () -> Unit)?,
    noteContent: (@Composable () -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                TodayBlock.PINNED -> state.pinned?.let { pinned -> item(key = "block-pinned") { PinnedBlock(pinned) } }
                TodayBlock.TASKS -> item(key = "block-tasks") {
                    TasksBlock(state.items, state.left, onToggle, onOpen, onMoveToTomorrow, onArchive)
                }
                TodayBlock.HABITS -> habitsContent?.let { content -> item(key = "block-habits") { BlockCard(title = "Habits", count = "", content = content) } }
                TodayBlock.SCRATCHPAD -> noteContent?.let { content -> item(key = "block-note") { BlockCard(title = "Scratchpad", count = "", content = content) } }
            }
        }
        item(key = "spacer") { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BlockCard(title: String, count: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(true) }
    PaperCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(title, Modifier.weight(1f), Paper.colors.ink)
            if (count.isNotEmpty()) MonoLabel(count, Modifier.padding(end = 8.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Collapse $title" else "Expand $title", tint = Paper.colors.muted, modifier = Modifier.rotate(if (open) 0f else -90f))
        }
        if (open) content()
    }
}

@Composable
private fun PinnedBlock(item: TaskItem) {
    BlockCard(title = "Pinned", count = "") {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            Text(item.title, style = Paper.type.headline, color = Paper.colors.ink)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressSegments(done = item.subtasksDone, total = item.subtasksTotal, modifier = Modifier.weight(1f))
                if (item.timeLabel.isNotEmpty()) MonoLabel("Due ${item.timeLabel.replace(":00", "").replace(" ", "")}", color = Paper.colors.accent)
            }
        }
    }
}

@Composable
private fun ProgressSegments(done: Int, total: Int, modifier: Modifier = Modifier) {
    val segments = if (total == 0) 5 else total
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { i ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(1.dp)).background(if (i < done) Paper.colors.ink else Paper.colors.rule))
        }
    }
}

@Composable
private fun TasksBlock(
    items: List<TaskItem>,
    left: Int,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMoveToTomorrow: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    BlockCard(title = "Today's list", count = "$left left") {
        if (items.isEmpty()) {
            Text("Nothing planned. Tap + to add a task.", style = Paper.type.note, color = Paper.colors.muted, modifier = Modifier.padding(14.dp))
        }
        items.forEach { item ->
            RowDivider()
            SwipeableTaskRow(
                item = item,
                onToggle = { onToggle(item.id) },
                onOpen = { onOpen(item.id) },
                onMoveToTomorrow = { onMoveToTomorrow(item.id) },
                onArchive = { onArchive(item.id) },
                modifier = Modifier.testTag("task-${item.id}"),
            )
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = Paper.colors.rule)

@Composable
private fun PlanView(items: List<TaskItem>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    items.forEach { task ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(task.timeLabel.replace(" AM", "").replace(" PM", ""), style = Paper.type.monoMeta.copy(fontSize = 12.sp), color = Paper.colors.muted, modifier = Modifier.padding(top = 2.dp).width(52.dp))
                            Column {
                                Text(task.title, style = Paper.type.title, color = Paper.colors.ink)
                                task.projectName?.let { ProjectTag(it, parseHexColor(task.projectColorHex.orEmpty()), Modifier.padding(top = 5.dp)) }
                            }
                        }
                        HorizontalDivider(color = Paper.colors.rule)
                    }
                    Text("Evening is free.", style = Paper.type.note.copy(fontSize = 17.sp), color = Paper.colors.muted, modifier = Modifier.padding(vertical = 14.dp))
                }
            }
        }
    }
}


@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Can't reach your tasks", style = Paper.type.headline, color = Paper.colors.ink)
        Text(message, style = Paper.type.bodySmall, color = Paper.colors.muted, modifier = Modifier.padding(top = 6.dp))
        Text("Retry", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
    }
}
