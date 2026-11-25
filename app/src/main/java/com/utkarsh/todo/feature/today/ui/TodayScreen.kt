package com.utkarsh.todo.feature.today.ui

import com.utkarsh.todo.core.offline.OnReconnect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.utkarsh.todo.core.designsystem.component.UndoSnackbar
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.feature.customize.domain.TodayBlock
import com.utkarsh.todo.feature.customize.ui.CustomizeViewModel
import com.utkarsh.todo.feature.notes.ui.NoteBlock
import com.utkarsh.todo.feature.today.domain.TaskItem
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val UNDO_VISIBLE_MS = 4_000L

@Composable
fun TodayRoute(
    onOpenTask: (String) -> Unit,
    onCustomize: () -> Unit,
    onAssistant: () -> Unit,
    onBrainDump: () -> Unit,
    onDueSoon: () -> Unit,
    onSearch: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
    settingsViewModel: CustomizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    OnReconnect { viewModel.refresh() }
    TodayScreen(
        state = state,
        onToggle = viewModel::toggleDone,
        onOpen = onOpenTask,
        onMoveToTomorrow = viewModel::moveToTomorrow,
        onArchive = viewModel::archive,
        onUndo = viewModel::undo,
        onDismissUndo = viewModel::dismissUndo,
        onRetry = viewModel::refresh,
        modifier = modifier,
        noteContent = { NoteBlock() },
        blocks = settings.settings.visibleBlocks,
        onCustomize = onCustomize,
        onAssistant = onAssistant,
        onBrainDump = onBrainDump,
        onDueSoon = onDueSoon,
        onSearch = onSearch,
        onAddTask = onAddTask,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMoveToTomorrow: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional extra blocks, supplied by the route so this screen stays free of Hilt for previews/tests. */
    noteContent: (@Composable () -> Unit)? = null,
    /** Which blocks to show, in order (see Customize). Defaults to all of them. */
    blocks: List<TodayBlock> = TodayBlock.entries,
    /** Shows the Customize button when set. */
    onCustomize: (() -> Unit)? = null,
    /** Shows the ✦ Ask Grok button when set. */
    onAssistant: (() -> Unit)? = null,
    /** Shows the brain dump button when set. */
    onBrainDump: (() -> Unit)? = null,
    /** Makes the "Due soon" banner open the Due soon screen. */
    onDueSoon: (() -> Unit)? = null,
    /** Adds "Search" to the ⋯ menu when set. */
    onSearch: (() -> Unit)? = null,
    /** Shows "+ Add a task" on an empty day when set. */
    onAddTask: (() -> Unit)? = null,
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
                MoreMenu(onSearch = onSearch, onCustomize = onCustomize)
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
            if (!state.dueSoon.isEmpty) DueSoonBanner(state.dueSoon, onDueSoon, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
            when {
                state.isLoading && state.items.isEmpty() -> TodaySkeleton()
                state.errorMessage != null && state.items.isEmpty() -> ErrorState(state.errorMessage, onRetry)
                else -> BlocksView(state, blocks, onToggle, onOpen, onMoveToTomorrow, onArchive, onAddTask, noteContent)
            }
        }
        state.undo?.let {
            UndoSnackbar(it.message, onUndo, Modifier.align(Alignment.BottomCenter).padding(14.dp))
        }
    }
}

/** The dark bar under the title: "1 overdue · 4 alerts today", opens the Due soon screen. */
@Composable
private fun DueSoonBanner(summary: DueSoonSummary, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val text = listOfNotNull(
        if (summary.overdue > 0) "${summary.overdue} overdue" else null,
        if (summary.today > 0) "${summary.today} ${if (summary.today == 1) "alert" else "alerts"} today" else null,
    ).joinToString(" · ")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.ink)
            .let { if (onOpen != null) it.clickable(onClick = onOpen) else it }
            .padding(horizontal = 14.dp, vertical = 13.dp).testTag("due-soon-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.accentLight))
        Text(text, style = Paper.type.body, color = colors.card, modifier = Modifier.weight(1f).padding(start = 10.dp))
        MonoLabel("Due soon →", color = colors.mutedSoft)
    }
}

/** The ⋯ button and its menu (Search, Customize). Hidden when there is nothing to put in it. */
@Composable
private fun MoreMenu(onSearch: (() -> Unit)?, onCustomize: (() -> Unit)?) {
    if (onSearch == null && onCustomize == null) return
    val colors = Paper.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "⋯", style = Paper.type.title, color = colors.muted,
            modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = "More" }.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 10.dp, vertical = 4.dp).testTag("open-more"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.card) {
            onSearch?.let { go ->
                DropdownMenuItem(
                    text = { Text("Search", style = Paper.type.body, color = colors.ink) },
                    onClick = { open = false; go() },
                    modifier = Modifier.testTag("open-search"),
                )
            }
            onCustomize?.let { go ->
                DropdownMenuItem(
                    text = { Text("Customize", style = Paper.type.body, color = colors.ink) },
                    onClick = { open = false; go() },
                    modifier = Modifier.testTag("open-customize"),
                )
            }
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
    onAddTask: (() -> Unit)?,
    noteContent: (@Composable () -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                TodayBlock.PINNED -> state.pinned?.let { pinned -> item(key = "block-pinned") { PinnedBlock(pinned) } }
                TodayBlock.TASKS -> item(key = "block-tasks") {
                    TasksBlock(state, onToggle, onOpen, onMoveToTomorrow, onArchive, onAddTask)
                }
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
    state: TodayUiState,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMoveToTomorrow: (String) -> Unit,
    onArchive: (String) -> Unit,
    onAddTask: (() -> Unit)?,
) {
    BlockCard(title = "Today's list", count = if (state.total == 0) "" else "${state.doneCount} / ${state.total} done") {
        if (state.total > 0) {
            ProgressBar(state.progress, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
        }
        if (state.items.isEmpty()) {
            EmptyDay(onAddTask)
        }
        state.items.forEach { item ->
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
        state.next?.let { next ->
            RowDivider()
            val time = if (next.timeLabel.isEmpty()) "" else " · ${next.timeLabel}"
            Text(
                "Next: ${next.title}$time",
                style = Paper.type.bodySmall,
                color = Paper.colors.muted,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).testTag("today-next"),
            )
        }
    }
}

/** A thin bar, filled in the accent colour as far as [progress] (0..1). */
@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.rule)
            .semantics { contentDescription = "${(progress * 100).roundToInt()} percent of today's tasks done" }
            .testTag("today-progress"),
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
    }
}

@Composable
private fun EmptyDay(onAddTask: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        Text("Nothing planned.", style = Paper.type.note, color = Paper.colors.muted)
        if (onAddTask != null) {
            Text(
                "+ Add a task",
                style = Paper.type.button,
                color = Paper.colors.accent,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAddTask).padding(vertical = 8.dp, horizontal = 2.dp).testTag("add-task-empty"),
            )
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = Paper.colors.rule)

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Can't reach your tasks", style = Paper.type.headline, color = Paper.colors.ink)
        Text(message, style = Paper.type.bodySmall, color = Paper.colors.muted, modifier = Modifier.padding(top = 6.dp))
        Text("Retry", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
    }
}
