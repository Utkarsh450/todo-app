package com.utkarsh.todo.feature.assistant.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonParagraph
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface

val QuickPrompts = listOf("Plan my day", "What's slipping?", "Review my week", "Remind me to…", "Schedule a meeting…")

@Composable
fun AssistantRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Voice goes through the system speech dialog (no microphone permission); the button only shows when a recogniser exists.
    val context = LocalContext.current
    val voice = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Grok")
    }
    val voiceAvailable = remember { voice.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken -> viewModel.ask(spoken) }
        }
    }
    AssistantScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onSend = { viewModel.ask() },
        onPrompt = { viewModel.ask(it) },
        onAccept = viewModel::accept,
        onSkip = viewModel::skip,
        onAcceptAll = viewModel::acceptAll,
        onRetry = viewModel::retry,
        onMic = if (voiceAvailable) ({ launcher.launch(voice) }) else null,
        modifier = modifier,
    )
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPrompt: (String) -> Unit,
    onAccept: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onAcceptAll: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Shows the microphone in the composer when set (the device has a speech recogniser). */
    onMic: (() -> Unit)? = null,
) {
    val colors = Paper.colors
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.loading) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size) // index 0 is the intro item
    }

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", style = Paper.type.title, color = colors.ink,
                modifier = Modifier.semantics { contentDescription = "Back" }.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(end = 14.dp, top = 2.dp, bottom = 2.dp).testTag("assistant-back"),
            )
            Text("✦ Ask Grok", style = Paper.type.title, color = colors.ink, modifier = Modifier.testTag("assistant-title"))
            Box(Modifier.weight(1f))
            MonoLabel("Sees your open tasks")
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = colors.rule)
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                if (state.turns.isEmpty()) {
                    Text(
                        "Ask about your day, or tell me what to add: tasks, calendar events, alerts. I can also move, split, tick off or re-prioritise tasks. You decide what happens.",
                        style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(top = 10.dp).testTag("assistant-empty"),
                    )
                }
            }
            items(state.turns, key = { it.id }) { turn ->
                Turn(turn, onAccept, onSkip, onAcceptAll, onRetry)
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickPrompts.forEachIndexed { index, prompt ->
                    val enabled = !state.busy
                    val primary = index == 0
                    val shape = RoundedCornerShape(50)
                    Text(
                        prompt, style = Paper.type.label, color = if (primary) colors.card else colors.ink,
                        modifier = Modifier
                            .alpha(if (enabled) 1f else 0.4f)
                            .clip(shape)
                            .background(if (primary) colors.ink else colors.card)
                            .border(1.dp, if (primary) colors.ink else colors.rule, shape)
                            .clickable(enabled = enabled) { if (prompt.endsWith("…")) onInputChange(prompt.dropLast(1) + " ") else onPrompt(prompt) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("prompt-$index"),
                    )
                }
            }
            InputRow(state.input, state.busy, onInputChange, onSend, onMic)
        }
    }
}

@Composable
private fun InputRow(text: String, busy: Boolean, onChange: (String) -> Unit, onSend: () -> Unit, onMic: (() -> Unit)?) {
    val colors = Paper.colors
    val canSend = text.isNotBlank() && !busy
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.card).border(1.dp, colors.rule, RoundedCornerShape(50)).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onMic?.let { mic ->
            Box(
                Modifier.padding(end = 10.dp).size(38.dp).alpha(if (busy) 0.4f else 1f).clip(CircleShape).background(colors.soft).clickable(enabled = !busy, onClick = mic).testTag("assistant-mic"),
                contentAlignment = Alignment.Center,
            ) { Icon(PaperIcons.Mic, contentDescription = "Speak", tint = colors.ink, modifier = Modifier.size(18.dp)) }
        }
        Box(Modifier.weight(1f).padding(end = 8.dp)) {
            BasicTextField(
                value = text, onValueChange = onChange, textStyle = Paper.type.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("assistant-input"),
            )
            if (text.isEmpty()) Text("Ask Grok, or tap the mic", style = Paper.type.body, color = colors.muted)
        }
        Box(
            Modifier.size(38.dp).alpha(if (canSend) 1f else 0.4f).clip(CircleShape).background(colors.accent).clickable(enabled = canSend, onClick = onSend).testTag("assistant-send"),
            contentAlignment = Alignment.Center,
        ) { Text("↑", style = Paper.type.button, color = colors.card) }
    }
}

@Composable
private fun Turn(turn: TurnUi, onAccept: (Int) -> Unit, onSkip: (Int) -> Unit, onAcceptAll: (Int) -> Unit, onRetry: (Int) -> Unit) {
    val colors = Paper.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                turn.question, style = Paper.type.body, color = colors.card,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(colors.ink).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        MonoLabel("✦ Grok", Modifier.padding(top = 16.dp, bottom = 6.dp), color = colors.accent)
        when {
            turn.loading -> SkeletonSurface("Grok is thinking", Modifier.padding(top = 4.dp), testTag = "assistant-loading") {
                SkeletonParagraph(widths = listOf(1f, 0.94f, 0.62f), ai = true)
                SkeletonCard(rows = 2, modifier = Modifier.padding(top = 22.dp), ai = true)
            }
            turn.error != null -> Column(Modifier.testTag("assistant-error")) {
                if (turn.notConfigured) {
                    Text("Grok isn't set up yet", style = Paper.type.title, color = colors.ink)
                    Text("Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.", style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(turn.error, style = Paper.type.body, color = colors.accent)
                    Text(
                        "Try again", style = Paper.type.button, color = colors.accent,
                        modifier = Modifier.padding(top = 6.dp).clickable { onRetry(turn.id) }.padding(vertical = 8.dp).testTag("assistant-retry"),
                    )
                }
            }
            else -> {
                Text(turn.reply.orEmpty(), style = Paper.type.title, color = colors.ink, modifier = Modifier.testTag("assistant-reply"))
                if (turn.suggestions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel(if (turn.suggestions.size == 1) "1 suggestion" else "${turn.suggestions.size} suggestions", Modifier.weight(1f))
                        if (turn.pendingCount > 1) {
                            MonoLabel("Accept all", color = colors.accent, modifier = Modifier.clickable { onAcceptAll(turn.id) }.padding(vertical = 6.dp, horizontal = 4.dp).testTag("accept-all-${turn.id}"))
                        }
                    }
                    PaperCard(Modifier.fillMaxWidth()) {
                        turn.suggestions.forEachIndexed { index, s ->
                            if (index > 0) HorizontalDivider(color = colors.rule)
                            SuggestionRow(s, onAccept, onSkip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: SuggestionUi, onAccept: (Int) -> Unit, onSkip: (Int) -> Unit) {
    val colors = Paper.colors
    val dim = s.status == SuggestionStatus.SKIPPED || s.status == SuggestionStatus.ACCEPTED
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).testTag("suggestion-${s.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                s.kind.label.uppercase(), style = Paper.type.monoLabel, color = colors.ink,
                modifier = Modifier.border(BorderStroke(1.dp, colors.ink), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Column(Modifier.weight(1f).alpha(if (dim) 0.5f else 1f)) {
                Text(s.title, style = Paper.type.body, color = colors.ink, textDecoration = if (s.status == SuggestionStatus.SKIPPED) TextDecoration.LineThrough else null)
                if (s.reason.isNotBlank()) Text(s.reason, style = Paper.type.bodySmall, color = colors.muted, maxLines = 2)
                if (s.steps.isNotEmpty()) Text(s.steps.joinToString("  ·  "), style = Paper.type.bodySmall, color = colors.muted, maxLines = 2)
            }
            when (s.status) {
                SuggestionStatus.ACCEPTED -> MonoLabel("✓ Done", Modifier.testTag("status-${s.id}"), color = colors.muted)
                SuggestionStatus.SKIPPED -> MonoLabel("Skipped", Modifier.testTag("status-${s.id}"), color = colors.muted)
                SuggestionStatus.WORKING -> MonoLabel("Applying…", Modifier.testTag("status-${s.id}"), color = colors.muted)
                SuggestionStatus.PENDING, SuggestionStatus.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundAction("✕", "Skip", filled = false, tag = "skip-${s.id}") { onSkip(s.id) }
                    RoundAction("✓", "Accept", filled = true, tag = "accept-${s.id}") { onAccept(s.id) }
                }
            }
        }
        if (s.status == SuggestionStatus.FAILED) {
            Text("Couldn't apply that. Tap ✓ to try again.", style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 6.dp).testTag("failed-${s.id}"))
        }
    }
}

/** The small round ✓ / ✕ buttons at the end of a suggestion row. */
@Composable
private fun RoundAction(glyph: String, label: String, filled: Boolean, tag: String, onClick: () -> Unit) {
    val colors = Paper.colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (filled) colors.ink else colors.card).border(1.dp, if (filled) colors.ink else colors.rule, CircleShape)
            .semantics { contentDescription = label }.clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = Paper.type.button, color = if (filled) colors.card else colors.ink) }
}
