package com.utkarsh.todo.feature.calendar.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.ProjectTag
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.core.offline.OnReconnect
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import java.time.LocalDate
import java.time.LocalTime

/** Everything the calendar can ask for, so the stateless screen stays easy to test with plain lambdas. */
class CalendarActions(
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onSelect: (LocalDate) -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val onModeChange: (CalendarMode) -> Unit = {},
    val onRetry: () -> Unit = {},
    /** A tap on an empty half hour of the day, 3-day or week grid: opens Create at that moment. */
    val onCreate: (LocalDate, LocalTime) -> Unit = { _, _ -> },
    val onOpenItem: (id: String, kind: AgendaKind) -> Unit = { _, _ -> },
    val onCloseDetail: () -> Unit = {},
    val onDeleteDetail: () -> Unit = {},
)

@Composable
fun CalendarRoute(onOpenTask: (String) -> Unit, onCreate: (LocalDate, LocalTime) -> Unit, modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    OnReconnect { viewModel.refresh() }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.reload() } // reminders may have changed on their own tab
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        actions = CalendarActions(
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToday = viewModel::goToToday,
            onSelect = viewModel::select,
            onOpenDay = viewModel::openDay,
            onModeChange = viewModel::setMode,
            onRetry = viewModel::refresh,
            onCreate = onCreate,
            onOpenItem = { id, kind -> if (kind == AgendaKind.TASK) onOpenTask(id) else viewModel.openDetail(id) },
            onCloseDetail = viewModel::closeDetail,
            onDeleteDetail = viewModel::deleteDetail,
        ),
        modifier = modifier,
    )
}

@Composable
fun CalendarScreen(state: CalendarUiState, actions: CalendarActions, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Box(modifier.fillMaxSize().paperSurface()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            CalendarHeader(state, actions)
            when {
                state.isLoading && state.cells.isEmpty() && state.columns.isEmpty() && state.schedule.isEmpty() -> CalendarSkeleton()
                state.error != null && state.cells.isEmpty() && state.columns.isEmpty() && state.schedule.isEmpty() ->
                    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Text("Can't reach your calendar", style = Paper.type.headline, color = colors.ink)
                        Text(state.error, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                        Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = actions.onRetry).padding(10.dp).testTag("calendar-retry"))
                    }
                state.mode == CalendarMode.MONTH -> MonthContent(state, actions)
                state.mode == CalendarMode.SCHEDULE -> ScheduleContent(state, actions)
                else -> TimeGrid(state, actions)
            }
        }
    }
    state.detail?.let { DetailSheet(it, actions.onCloseDetail, actions.onDeleteDetail) }
}

@Composable
private fun CalendarHeader(state: CalendarUiState, actions: CalendarActions) {
    val colors = Paper.colors
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { menuOpen = true }.padding(end = 8.dp).testTag("mode-menu")) {
                MonoLabel("${state.yearLabel} · ${state.mode.label}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.monthLabel, style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.testTag("month-title"))
                    Text(" ▾", style = Paper.type.title, color = colors.muted)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.card) {
                CalendarMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, style = Paper.type.body, color = if (mode == state.mode) colors.accent else colors.ink) },
                        onClick = { menuOpen = false; actions.onModeChange(mode) },
                        modifier = Modifier.testTag("mode-${mode.name}"),
                    )
                }
            }
        }
        NavButton("‹", "calendar-prev", actions.onPrevious)
        Text("Today", style = Paper.type.label, color = colors.accent, modifier = Modifier.clickable(onClick = actions.onToday).padding(10.dp).testTag("go-today"))
        NavButton("›", "calendar-next", actions.onNext)
    }
}

@Composable
private fun NavButton(glyph: String, tag: String, onClick: () -> Unit) {
    Text(glyph, style = Paper.type.display, color = Paper.colors.ink, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp).testTag(tag))
}

// ---- Month

/** The colour of an entry: its project's, or one per kind (events ink, tasks muted, reminders vermilion). */
@Composable
internal fun entryColor(kind: AgendaKind, projectHex: String?): Color {
    val colors = Paper.colors
    return projectHex?.let(::parseHexColor) ?: when (kind) {
        AgendaKind.EVENT -> colors.ink
        AgendaKind.TASK -> colors.muted
        AgendaKind.REMINDER -> colors.accent
    }
}

internal fun AgendaKind.glyph(): String = when (this) {
    AgendaKind.EVENT -> ""
    AgendaKind.TASK -> "☐ "
    AgendaKind.REMINDER -> "⏰ "
}

@Composable
private fun MonthContent(state: CalendarUiState, actions: CalendarActions) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(d, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = Paper.colors.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            HorizontalDivider(color = Paper.colors.rule, modifier = Modifier.padding(top = 6.dp))
            state.cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(MONTH_ROW_HEIGHT)) { week.forEach { cell -> DayCell(cell, actions, Modifier.weight(1f)) } }
                HorizontalDivider(color = Paper.colors.rule)
            }
        }
        item { AgendaCard(state, actions, Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp)) }
    }
}

private val MONTH_ROW_HEIGHT = 92.dp

@Composable
private fun DayCell(cell: DayCellUi, actions: CalendarActions, modifier: Modifier) {
    val colors = Paper.colors
    Column(
        modifier.fillMaxSize().clickable { actions.onSelect(cell.date) }.padding(horizontal = 1.dp, vertical = 3.dp).testTag("day-${cell.date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val numberColor = when {
            cell.isSelected -> colors.card
            cell.isToday -> colors.accent
            !cell.inMonth -> colors.mutedSoft
            else -> colors.ink
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (cell.isSelected) colors.ink else Color.Transparent)
                .then(if (cell.isToday && !cell.isSelected) Modifier.border(1.5.dp, colors.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Text(cell.date.dayOfMonth.toString(), style = Paper.type.label, color = numberColor) }
        Column(Modifier.fillMaxWidth().padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cell.chips.forEach { chip ->
                val color = entryColor(chip.kind, chip.colorHex)
                Text(
                    chip.kind.glyph().trim().let { if (it.isEmpty()) chip.title else "$it ${chip.title}" },
                    style = Paper.type.label.copy(fontSize = 9.sp, lineHeight = 11.sp), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (chip.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 3.dp, vertical = 1.dp).testTag("chip-${chip.id}"),
                )
            }
            if (cell.more > 0) Text("+${cell.more}", style = Paper.type.monoMeta.copy(fontSize = 9.sp), color = colors.muted, modifier = Modifier.padding(start = 3.dp).testTag("more-${cell.date}"))
        }
    }
}

@Composable
private fun AgendaCard(state: CalendarUiState, actions: CalendarActions, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    PaperCard(modifier.fillMaxWidth().testTag("agenda")) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.selectedLabel, style = Paper.type.title, color = colors.ink, modifier = Modifier.weight(1f))
            if (state.plannedLabel.isNotEmpty()) MonoLabel(state.plannedLabel)
        }
        if (state.agenda.isEmpty()) {
            Text("Nothing planned. Tap + to add something.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(14.dp))
        }
        state.agenda.forEach { row ->
            HorizontalDivider(color = colors.rule)
            AgendaRow(row, actions, Modifier.testTag("agenda-${row.id}"))
        }
    }
}

@Composable
private fun AgendaRow(row: AgendaRowUi, actions: CalendarActions, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Row(
        modifier.fillMaxWidth().clickable { actions.onOpenItem(row.id, row.kind) }.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(entryColor(row.kind, row.projectColorHex)))
        Text(row.timeLabel, style = Paper.type.monoMeta, color = colors.muted, modifier = Modifier.width(64.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = Paper.type.title.copy(fontSize = 18.sp), color = colors.ink, textDecoration = if (row.done) TextDecoration.LineThrough else null)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.kind != AgendaKind.EVENT) MonoLabel(if (row.kind == AgendaKind.TASK) "Task" else "Alert")
                row.projectName?.let { ProjectTag(it, parseHexColor(row.projectColorHex.orEmpty())) }
            }
        }
    }
}

// ---- Schedule

@Composable
private fun ScheduleContent(state: CalendarUiState, actions: CalendarActions) {
    val colors = Paper.colors
    if (state.schedule.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp).testTag("schedule-empty"), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Nothing coming up", style = Paper.type.headline, color = colors.ink)
            Text("The next month is clear. Tap + to plan something.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("schedule"), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        items(state.schedule, key = { it.date.toString() }) { day ->
            Column(Modifier.fillMaxWidth().padding(top = 14.dp).testTag("schedule-${day.date}")) {
                MonoLabel(day.label + if (day.isToday) " · Today" else "", color = if (day.isToday) colors.accent else colors.muted)
                PaperCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    day.rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = colors.rule)
                        AgendaRow(row, actions, Modifier.testTag("agenda-${row.id}"))
                    }
                }
            }
        }
    }
}
