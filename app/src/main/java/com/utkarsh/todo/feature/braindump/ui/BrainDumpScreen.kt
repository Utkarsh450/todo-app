package com.utkarsh.todo.feature.braindump.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.component.ProjectDot
import com.utkarsh.todo.core.designsystem.component.RuledTextField
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.feature.tasks.domain.Priority

@Composable
fun BrainDumpRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: BrainDumpViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.added.collect { onBack() } }

    // Voice typing goes through the system speech dialog, so the app needs no microphone permission.
    // The button is only shown on devices that have a speech recogniser.
    val context = LocalContext.current
    val voice = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what's on your mind")
    }
    val voiceAvailable = remember { voice.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::appendSpoken)
        }
    }

    BrainDumpScreen(
        state = state,
        onBack = onBack,
        onTextChange = viewModel::onTextChange,
        onSort = viewModel::sort,
        onStartOver = viewModel::startOver,
        onEditNote = viewModel::editNote,
        onToggle = viewModel::toggle,
        onAdd = viewModel::addSelected,
        onMic = if (voiceAvailable) ({ launcher.launch(voice) }) else null,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrainDumpScreen(
    state: BrainDumpUiState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSort: () -> Unit,
    onStartOver: () -> Unit,
    onEditNote: () -> Unit,
    onToggle: (Int) -> Unit,
    onAdd: () -> Unit,
    onMic: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val sorting = state.phase == DumpPhase.SORTING
    val results = state.phase == DumpPhase.RESULTS

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Back", style = Paper.type.button, color = colors.ink,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("dump-back"),
            )
            Box(Modifier.weight(1f))
            val canReset = state.text.isNotEmpty() && !sorting && !state.adding
            Text(
                "Start over", style = Paper.type.bodySmall, color = colors.muted,
                modifier = Modifier.alpha(if (canReset) 1f else 0.4f).clickable(enabled = canReset, onClick = onStartOver).padding(vertical = 4.dp).testTag("dump-start-over"),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            MonoLabel("✦ Brain dump · Grok", color = colors.accent)
            Text("Empty your head", style = Paper.type.displayLarge.copy(fontSize = 40.sp, lineHeight = 42.sp), color = colors.ink, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp).testTag("dump-title"))

            PaperCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 140.dp)) {
                    if (results) {
                        Text(state.text, style = Paper.type.note, color = colors.muted, modifier = Modifier.testTag("dump-note"))
                    } else {
                        RuledTextField(
                            value = state.text, onValueChange = onTextChange, placeholder = "Talk or type everything on your mind…",
                            inputModifier = Modifier.testTag("dump-input"),
                        )
                    }
                }
            }

            if (sorting) {
                SkeletonSurface("Sorting your note into tasks", Modifier.padding(top = 20.dp), testTag = "dump-skeleton") {
                    MonoLabel("Sorting…", Modifier.padding(bottom = 8.dp))
                    SkeletonCard(rows = 5, ai = true)
                }
            } else if (!results) {
                if (onMic != null) {
                    Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp).alpha(if (sorting) 0.4f else 1f).clip(CircleShape).background(colors.ink).clickable(enabled = !sorting, onClick = onMic).testTag("dump-mic"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(PaperIcons.Mic, contentDescription = "Dictate", tint = colors.card, modifier = Modifier.size(26.dp)) }
                        MonoLabel(if (state.text.isBlank()) "Tap to speak" else "Tap to add more", Modifier.padding(top = 10.dp))
                    }
                }
                state.message?.let { message ->
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("dump-message")) {
                        if (state.notConfigured) {
                            Text("Grok isn't set up yet", style = Paper.type.body, color = colors.ink)
                            Text("Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.", style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Text(message, style = Paper.type.bodySmall, color = colors.accent)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel(
                        if (state.rows.size == 1) "Found 1 task · untick to drop" else "Found ${state.rows.size} tasks · untick to drop",
                        Modifier.weight(1f).testTag("dump-found"),
                    )
                    MonoLabel("Edit note", color = colors.accent, modifier = Modifier.clickable(onClick = onEditNote).padding(vertical = 6.dp, horizontal = 4.dp).testTag("dump-edit"))
                }
                PaperCard(Modifier.fillMaxWidth()) {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = colors.rule)
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(index) }.padding(horizontal = 14.dp, vertical = 12.dp).testTag("dump-row-$index"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PaperCheckbox(row.selected, { onToggle(index) }, modifier = Modifier.padding(top = 2.dp), label = "Add ${row.title}")
                            Column(Modifier.weight(1f).alpha(if (row.selected) 1f else 0.45f)) {
                                Text(row.title, style = Paper.type.body, color = colors.ink)
                                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                    row.dueLabel?.let { Chip("◷ $it") }
                                    if (row.priority != Priority.NORMAL) Chip("⚑ ${row.priority.name}")
                                    row.repeat?.let { Chip("↻ $it") }
                                    row.projectName?.let { name ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ProjectDot(parseHexColor(row.projectColorHex.orEmpty()), size = 8)
                                            Text(name, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(start = 5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state.message?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 10.dp).testTag("dump-message")) }
                Box(Modifier.padding(bottom = 12.dp))
            }
        }
        val count = state.selectedCount
        val enabled = when {
            sorting || state.adding -> false
            results -> count > 0
            else -> state.text.isNotBlank()
        }
        val label = when {
            sorting -> "Sorting…"
            state.adding -> "Adding…"
            results && count == 0 -> "Pick at least one"
            results -> if (count == 1) "Add 1 task" else "Add $count tasks"
            else -> "✦ Turn into tasks"
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.4f)
                    .clip(RoundedCornerShape(PaperRadius.card))
                    .background(colors.ink)
                    .clickable(enabled = enabled, onClick = if (results) onAdd else onSort)
                    .padding(vertical = 15.dp)
                    .testTag(if (results) "dump-add" else "dump-sort"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Paper.type.button, color = colors.card, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text.uppercase(), style = Paper.type.monoLabel, color = Paper.colors.ink,
        modifier = Modifier.border(BorderStroke(1.dp, Paper.colors.ink), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
