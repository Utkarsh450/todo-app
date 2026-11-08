package com.utkarsh.todo.feature.insights.ui

import com.utkarsh.todo.core.offline.OnReconnect
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.StatCard
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.feature.insights.domain.HeatmapMode
import java.time.LocalDate

@Composable
fun InsightsRoute(modifier: Modifier = Modifier, viewModel: InsightsViewModel = hiltViewModel()) {
    OnReconnect { viewModel.refresh() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(state, viewModel::setMode, viewModel::select, viewModel::refresh, modifier)
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onModeChange: (HeatmapMode) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("Last $HEATMAP_WEEKS weeks", Modifier.weight(1f))
            PaperSegmentedControl(HeatmapMode.entries, state.mode, onModeChange, labelOf = { if (it == HeatmapMode.TASKS) "Tasks" else "Habits" })
        }
        Text("Insights", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp).testTag("insights-title"))
        when {
            state.isLoading && state.weeks.isEmpty() -> InsightsSkeleton()
            state.error != null && state.weeks.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Can't reach your insights", style = Paper.type.headline, color = colors.ink)
                Text(state.error, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                Text("Retry", style = Paper.type.button, color = colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRetry).padding(10.dp))
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("${state.total}", state.unit, Modifier.weight(1f).testTag("stat-total"))
                    StatCard("${state.streak}", "Day streak", Modifier.weight(1f).testTag("stat-streak"), accent = true)
                    StatCard("${state.best}", "Best day", Modifier.weight(1f).testTag("stat-best"))
                }
                PaperCard(Modifier.fillMaxWidth()) { Heatmap(state, onSelect) }
                PaperCard(Modifier.fillMaxWidth()) { Rhythm(state) }
                Box(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Heatmap(state: InsightsUiState, onSelect: (LocalDate) -> Unit) {
    val colors = Paper.colors
    val ramp = if (state.mode == HeatmapMode.TASKS) colors.taskHeat else colors.habitHeat
    Column(Modifier.padding(14.dp).testTag("heatmap")) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            state.monthLabels.forEach { label ->
                Box(Modifier.weight(1f)) { if (label.isNotEmpty()) Text(label.uppercase(), style = Paper.type.monoMeta.copy(fontSize = 8.sp), color = colors.muted, maxLines = 1, softWrap = false) }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            state.weeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (cell.isFuture) Color.Transparent else ramp[cell.level.coerceIn(0, ramp.lastIndex)])
                                .then(if (cell.isSelected) Modifier.border(1.5.dp, colors.ink, RoundedCornerShape(3.dp)) else Modifier)
                                .then(if (cell.isFuture) Modifier else Modifier.clickable { onSelect(cell.date) })
                                .testTag("heat-${cell.date}"),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.selectedLabel, style = Paper.type.title, color = colors.ink)
                MonoLabel(state.selectedValue, Modifier.testTag("selected-value"))
            }
            MonoLabel("Less", Modifier.padding(end = 6.dp))
            ramp.forEach { Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
            MonoLabel("More", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Rhythm(state: InsightsUiState) {
    val colors = Paper.colors
    Column(Modifier.padding(14.dp)) {
        MonoLabel("Weekly rhythm")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            state.rhythm.forEach { bar ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.width(14.dp).height((56 * bar.fraction).coerceAtLeast(2f).dp).clip(RoundedCornerShape(3.dp)).background(if (bar.isPeak) colors.accent else colors.ink))
                    Text(bar.letter, style = Paper.type.monoMeta.copy(fontSize = 10.sp), color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
