package com.utkarsh.todo.feature.focus.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface

@Composable
fun FocusRoute(onBack: (() -> Unit)?, modifier: Modifier = Modifier, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FocusScreen(state, onBack, viewModel::setMinutes, viewModel::start, viewModel::pause, viewModel::reset, viewModel::markTaskDone, modifier)
}

@Composable
fun FocusScreen(
    state: FocusUiState,
    /** Null in the Timer tab, which has nothing to go back to. */
    onBack: (() -> Unit)?,
    onMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onMarkDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val view = LocalView.current
    // The screen stays awake while the countdown runs.
    DisposableEffect(state.phase) {
        view.keepScreenOn = state.phase == FocusPhase.RUNNING
        onDispose { view.keepScreenOn = false }
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.phase) { if (state.phase == FocusPhase.FINISHED) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (onBack != null) {
                Text("‹ Back", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("focus-back"))
            } else {
                MonoLabel("Timer", color = colors.muted, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MonoLabel(
                when (state.phase) {
                    FocusPhase.IDLE -> "Ready to focus"
                    FocusPhase.RUNNING -> "Focusing"
                    FocusPhase.PAUSED -> "Paused"
                    FocusPhase.FINISHED -> "Time's up"
                },
                color = if (state.phase == FocusPhase.FINISHED) colors.accent else colors.muted,
                modifier = Modifier.testTag("focus-phase"),
            )
            Text(
                state.taskTitle ?: if (state.hasTask) "…" else "Free focus", style = Paper.type.headline, color = colors.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp).testTag("focus-task"),
            )
            Text(state.clockLabel, style = Paper.type.displayLarge.copy(fontSize = 84.sp), color = colors.ink, modifier = Modifier.testTag("focus-clock"))
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(colors.rule)) {
                if (state.progress > 0f) Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).height(5.dp).background(colors.accent))
            }
            if (state.phase == FocusPhase.IDLE || state.phase == FocusPhase.FINISHED) {
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPresets.forEach { m ->
                        val selected = m == state.minutes
                        val shape = RoundedCornerShape(50)
                        Text(
                            "$m min", style = Paper.type.button, color = if (selected) colors.card else colors.ink,
                            modifier = Modifier.clip(shape).background(if (selected) colors.ink else colors.card).border(1.dp, colors.rule, shape)
                                .clickable { onMinutes(m) }.padding(horizontal = 16.dp, vertical = 9.dp).testTag("minutes-$m"),
                        )
                    }
                }
            }
            state.message?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 16.dp).testTag("focus-message")) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state.phase) {
                FocusPhase.IDLE -> BigButton("Start", onStart, "focus-start")
                FocusPhase.RUNNING -> BigButton("Pause", onPause, "focus-pause")
                FocusPhase.PAUSED -> {
                    BigButton("Resume", onStart, "focus-resume")
                    LinkButton("Reset", onReset, "focus-reset")
                }
                FocusPhase.FINISHED -> {
                    BigButton("Another round", onStart, "focus-again")
                }
            }
            if (state.taskTitle != null && !state.taskDone && state.phase != FocusPhase.RUNNING) LinkButton("Mark the task done", onMarkDone, "focus-done")
            if (state.taskDone) MonoLabel("Task done", Modifier.align(Alignment.CenterHorizontally).testTag("focus-task-done"), colors.accent)
        }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PaperRadius.card)).background(Paper.colors.ink).clickable(onClick = onClick).padding(vertical = 16.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Paper.type.button, color = Paper.colors.card) }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit, tag: String) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag), contentAlignment = Alignment.Center) {
        Text(label, style = Paper.type.button, color = Paper.colors.accent)
    }
}
