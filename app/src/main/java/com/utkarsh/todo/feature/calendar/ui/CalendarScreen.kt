package com.utkarsh.todo.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.ProjectTag
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import java.time.LocalDate

private val HOUR_HEIGHT = 56.dp

@Composable
fun CalendarRoute(modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onToday = viewModel::goToToday,
        onSelect = viewModel::select,
        onModeChange = viewModel::setMode,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onModeChange: (CalendarMode) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(state.yearLabel, Modifier.weight(1f))
            PaperSegmentedControl(
                options = CalendarMode.entries,
                selected = state.mode,
                onSelect = onModeChange,
                labelOf = { if (it == CalendarMode.MONTH) "Month" else "Week" },
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.monthLabel, style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.weight(1f).testTag("month-title"))
            NavButton("‹", "Previous month", onPrevious)
            Text("Today", style = Paper.type.label, color = colors.accent, modifier = Modifier.clickable(onClick = onToday).padding(10.dp).testTag("go-today"))
            NavButton("›", "Next month", onNext)
        }
        when {
            state.isLoading && state.cells.isEmpty() -> CalendarSkeleton()
            state.error != null && state.cells.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Can't reach your calendar", style = Paper.type.headline, color = colors.ink)
                Text(state.error, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
            }
            state.mode == CalendarMode.MONTH -> MonthContent(state, onSelect)
            else -> WeekContent(state, onSelect)
        }
    }
}

@Composable
private fun NavButton(glyph: String, description: String, onClick: () -> Unit) {
    Text(glyph, style = Paper.type.display, color = Paper.colors.ink, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp).testTag(description))
}

@Composable
private fun MonthContent(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PaperCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d ->
                            Text(d, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = if (i == 0 || i == 6) Paper.colors.muted else Paper.colors.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        }
                    }
                    state.cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { week.forEach { cell -> DayCell(cell, onSelect, Modifier.weight(1f)) } }
                    }
                }
            }
        }
        item { AgendaCard(state) }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayCell(cell: DayCellUi, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val colors = Paper.colors
    val textColor = when {
        cell.isSelected -> colors.card
        cell.isToday -> colors.accent
        !cell.inMonth -> colors.mutedSoft
        else -> colors.ink
    }
    Column(
        modifier
            .height(48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (cell.isSelected) colors.ink else Color.Transparent)
            .then(if (cell.isToday && !cell.isSelected) Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onSelect(cell.date) }
            .testTag("day-${cell.date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(cell.date.dayOfMonth.toString(), style = Paper.type.body, color = textColor)
        Row(Modifier.height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            cell.dots.forEach { hex ->
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (cell.isSelected) colors.card else hex?.let(::parseHexColor) ?: colors.ink))
            }
        }
    }
}

@Composable
private fun AgendaCard(state: CalendarUiState) {
    val colors = Paper.colors
    PaperCard(Modifier.fillMaxWidth().testTag("agenda")) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.selectedLabel, style = Paper.type.title, color = colors.ink, modifier = Modifier.weight(1f))
            if (state.plannedLabel.isNotEmpty()) MonoLabel(state.plannedLabel)
        }
        if (state.agenda.isEmpty()) {
            Text("Nothing planned. Enjoy the quiet.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(14.dp))
        }
        state.agenda.forEach { row ->
            HorizontalDivider(color = colors.rule)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).testTag("agenda-${row.id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.timeLabel, style = Paper.type.monoMeta, color = colors.muted, modifier = Modifier.width(64.dp).padding(top = 2.dp))
                Column {
                    Text(row.title, style = Paper.type.title.copy(fontSize = 18.sp), color = colors.ink, textDecoration = if (row.done) TextDecoration.LineThrough else null)
                    row.projectName?.let { ProjectTag(it, parseHexColor(row.projectColorHex.orEmpty()), Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WeekContent(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val colors = Paper.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            state.weekDays.forEach { day ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(day.date) }.padding(vertical = 6.dp).testTag("week-${day.date}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel(day.letter)
                    Text(
                        day.date.dayOfMonth.toString(),
                        style = Paper.type.title,
                        color = if (day.isSelected) colors.accent else colors.ink,
                        modifier = Modifier.padding(top = 2.dp).then(if (day.isSelected) Modifier.border(1.5.dp, colors.accent, CircleShape).padding(horizontal = 9.dp, vertical = 2.dp) else Modifier.padding(horizontal = 9.dp, vertical = 2.dp)),
                    )
                    Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (day.hasItems) colors.ink else Color.Transparent))
                }
            }
        }
        Text(
            buildAnnotatedString {
                append(state.selectedLabel)
                if (state.plannedLabel.isNotEmpty()) withStyle(SpanStyle(color = colors.muted, fontSize = 14.sp)) { append("  ·  ${state.plannedLabel}") }
            },
            style = Paper.type.title, color = colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        state.timeline.allDay.forEach { row ->
            Text("All day · ${row.title}", style = Paper.type.label, color = colors.ink, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(7.dp)).background(colors.soft).padding(8.dp))
        }
        PaperCard(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("timeline")) { Timeline(state.timeline) }
    }
}

@Composable
private fun Timeline(timeline: TimelineUi) {
    val colors = Paper.colors
    val hours = (timeline.startHour until timeline.endHour).toList()
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT * hours.size)) {
            hours.forEachIndexed { i, hour ->
                Row(Modifier.fillMaxWidth().offset(y = HOUR_HEIGHT * i), verticalAlignment = Alignment.Top) {
                    MonoLabel(hourLabel(hour), Modifier.width(44.dp).padding(start = 10.dp, top = 2.dp))
                    HorizontalDivider(color = colors.rule, modifier = Modifier.weight(1f))
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(start = 54.dp, end = 8.dp)) {
                timeline.blocks.forEach { block ->
                    val laneWidth = maxWidth / block.laneCount
                    val color = block.colorHex?.let(::parseHexColor) ?: colors.ink
                    Column(
                        Modifier
                            .offset(x = laneWidth * block.lane, y = HOUR_HEIGHT * (block.startMinute / 60f) + 2.dp)
                            .width(laneWidth - 3.dp)
                            .height(HOUR_HEIGHT * (block.durationMinutes / 60f) - 4.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color.copy(alpha = 0.14f))
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("block-${block.id}"),
                    ) {
                        Text(block.title, style = Paper.type.label, color = colors.ink, maxLines = 2)
                        Text(block.timeLabel, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = colors.muted)
                    }
                }
            }
        }
    }
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12a"
    hour < 12 -> "${hour}a"
    hour == 12 -> "12p"
    else -> "${hour - 12}p"
}
