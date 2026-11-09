package com.utkarsh.todo.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import java.time.LocalTime

internal val HOUR_HEIGHT = 56.dp
private val GUTTER = 46.dp
private const val SLOT_MINUTES = 30

/** The half hour that a tap at [yPx] on a grid with [hourPx] per hour lands in, as minutes since midnight. */
internal fun slotMinutes(yPx: Float, hourPx: Float): Int =
    (((yPx / hourPx) * 60).toInt() / SLOT_MINUTES * SLOT_MINUTES).coerceIn(0, 24 * 60 - SLOT_MINUTES)

/**
 * The week and day views: day headers, an all-day strip, and a 24-hour grid with the entries as blocks and a
 * red line for now. Tapping an empty slot opens the create sheet at that half hour; tapping a block opens it.
 */
@Composable
internal fun TimeGrid(state: CalendarUiState, actions: CalendarActions) {
    val colors = Paper.colors
    val columns = state.columns
    if (columns.isEmpty()) return
    Column(Modifier.fillMaxSize().testTag("time-grid-${state.mode.name.lowercase()}")) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp)) {
            Spacer(Modifier.width(GUTTER))
            columns.forEach { column ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { actions.onOpenDay(column.date) }.padding(vertical = 4.dp).testTag("column-${column.date}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel(column.letter, color = if (column.isToday) colors.accent else colors.muted)
                    Box(
                        Modifier.padding(top = 2.dp).size(30.dp).clip(CircleShape)
                            .background(if (column.isToday) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(column.date.dayOfMonth.toString(), style = Paper.type.title.copy(fontSize = 17.sp), color = if (column.isToday) colors.card else colors.ink)
                    }
                }
            }
        }
        if (columns.any { it.allDay.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 4.dp)) {
                Box(Modifier.width(GUTTER), contentAlignment = Alignment.CenterEnd) { MonoLabel("All day", Modifier.padding(end = 6.dp)) }
                columns.forEach { column ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        column.allDay.forEach { row ->
                            val color = entryColor(row.kind, row.projectColorHex)
                            Text(
                                row.title, style = Paper.type.label.copy(fontSize = 10.sp), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.2f))
                                    .clickable { actions.onOpenItem(row.id, row.kind) }.padding(horizontal = 4.dp, vertical = 2.dp).testTag("allday-${row.id}"),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.rule)
        Grid(state, actions)
    }
}

@Composable
private fun Grid(state: CalendarUiState, actions: CalendarActions) {
    val colors = Paper.colors
    val columns = state.columns
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    LaunchedEffect(state.mode, state.scrollHour) { scroll.scrollTo((hourPx * state.scrollHour).toInt()) }

    Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)) {
            for (hour in 0 until 24) {
                Row(Modifier.fillMaxWidth().offset(y = HOUR_HEIGHT * hour), verticalAlignment = Alignment.Top) {
                    MonoLabel(if (hour == 0) "" else hourLabel(hour), Modifier.width(GUTTER).padding(end = 6.dp).offset(y = (-6).dp), color = colors.muted)
                    HorizontalDivider(color = colors.rule, modifier = Modifier.weight(1f))
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(start = GUTTER, end = 6.dp)) {
                val columnWidth = maxWidth / columns.size
                // Column dividers.
                columns.indices.drop(1).forEach { i -> Box(Modifier.offset(x = columnWidth * i).width(1.dp).fillMaxSize().background(colors.rule)) }
                // Taps on empty space create something at that half hour.
                Box(
                    Modifier.fillMaxSize().testTag("slots").pointerInput(columns.map { it.date }) {
                        detectTapGestures { tap ->
                            val index = (tap.x / size.width * columns.size).toInt().coerceIn(0, columns.size - 1)
                            val minutes = slotMinutes(tap.y, hourPx)
                            actions.onCreate(com.utkarsh.todo.feature.calendar.domain.CreateKind.EVENT, columns[index].date, LocalTime.of(minutes / 60, minutes % 60))
                        }
                    },
                )
                columns.forEachIndexed { index, column ->
                    column.blocks.forEach { block ->
                        val laneWidth = columnWidth / block.laneCount
                        val color = entryColor(block.kind, block.colorHex)
                        Column(
                            Modifier
                                .offset(x = columnWidth * index + laneWidth * block.lane, y = HOUR_HEIGHT * (block.startMinute / 60f) + 1.dp)
                                .width(laneWidth - 2.dp)
                                .height(HOUR_HEIGHT * (block.durationMinutes / 60f) - 2.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color.copy(alpha = 0.16f))
                                .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                                .clickable { actions.onOpenItem(block.id, block.kind) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("block-${block.id}"),
                        ) {
                            Text(
                                (if (block.kind == AgendaKind.EVENT) "" else block.kind.glyph()) + block.title,
                                style = Paper.type.label.copy(fontSize = if (columns.size > 1) 10.sp else 13.sp, lineHeight = if (columns.size > 1) 12.sp else 16.sp),
                                color = colors.ink, maxLines = if (columns.size > 1) 3 else 2, overflow = TextOverflow.Ellipsis,
                                textDecoration = if (block.done) TextDecoration.LineThrough else null,
                            )
                            if (columns.size == 1) Text(block.timeLabel, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = colors.muted)
                        }
                    }
                }
                state.nowMinute?.let { minute ->
                    val today = columns.indexOfFirst { it.isToday }
                    if (today >= 0) {
                        Box(Modifier.offset(x = columnWidth * today, y = HOUR_HEIGHT * (minute / 60f) - 4.dp).testTag("now-line")) {
                            Box(Modifier.padding(top = 3.dp).width(columnWidth).height(2.dp).background(colors.accent))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
                        }
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
