package com.utkarsh.todo.feature.assistant.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface

val QuickPrompts = listOf("Plan my day", "What's slipping?", "Review my week")

@Composable
fun AssistantRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
) {
    val colors = Paper.colors
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.loading) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size) // index 0 is the header
    }

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding()) {
        Text(
            "‹ Back", style = Paper.type.button, color = colors.ink,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp).clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("assistant-back"),
        )
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                MonoLabel("✦ Assistant · Grok", color = colors.accent)
                Text("Ask Grok", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(top = 4.dp).testTag("assistant-title"))
                MonoLabel("Grok · sees your open tasks", Modifier.padding(top = 6.dp, bottom = 14.dp))
                if (state.turns.isEmpty()) {
                    Text(
                        "Ask about your day. I can suggest moving, splitting or re-prioritising tasks. You decide what happens.",
                        style = Paper.type.note, color = colors.muted, modifier = Modifier.testTag("assistant-empty"),
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
                            .clickable(enabled = enabled) { onPrompt(prompt) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("prompt-$index"),
                    )
                }
            }
            InputRow(state.input, state.busy, onInputChange, onSend)
            Text(
                "Grok can make mistakes. Nothing changes until you accept.",
                style = Paper.type.bodySmall, color = colors.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun InputRow(text: String, busy: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    val colors = Paper.colors
    val canSend = text.isNotBlank() && !busy
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PaperRadius.card)).background(colors.card).border(1.dp, colors.rule, RoundedCornerShape(PaperRadius.card)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = text, onValueChange = onChange, textStyle = Paper.type.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("assistant-input"),
            )
            if (text.isEmpty()) Text("Ask Grok about your tasks…", style = Paper.type.body, color = colors.muted)
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
            turn.loading -> Row(Modifier.padding(vertical = 6.dp).testTag("assistant-loading"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Thinking…", style = Paper.type.note, color = colors.muted)
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
    Column(Modifier.fillMaxWidth().padding(14.dp).testTag("suggestion-${s.id}")) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                s.kind.label.uppercase(), style = Paper.type.monoLabel, color = colors.ink,
                modifier = Modifier.padding(top = 2.dp).border(BorderStroke(1.dp, colors.ink), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Column(Modifier.weight(1f).alpha(if (dim) 0.5f else 1f)) {
                Text(s.title, style = Paper.type.body, color = colors.ink, textDecoration = if (s.status == SuggestionStatus.SKIPPED) TextDecoration.LineThrough else null)
                if (s.reason.isNotBlank()) Text(s.reason, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 2.dp))
                if (s.steps.isNotEmpty()) Text(s.steps.joinToString("  ·  "), style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        when (s.status) {
            SuggestionStatus.ACCEPTED -> MonoLabel("✓ Done", Modifier.padding(top = 10.dp).testTag("status-${s.id}"), color = colors.muted)
            SuggestionStatus.SKIPPED -> MonoLabel("Skipped", Modifier.padding(top = 10.dp).testTag("status-${s.id}"), color = colors.muted)
            SuggestionStatus.WORKING -> MonoLabel("Applying…", Modifier.padding(top = 10.dp).testTag("status-${s.id}"), color = colors.muted)
            SuggestionStatus.PENDING, SuggestionStatus.FAILED -> {
                if (s.status == SuggestionStatus.FAILED) {
                    Text("Couldn't apply that. Tap Accept to try again.", style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 8.dp).testTag("failed-${s.id}"))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Accept", style = Paper.type.button, color = colors.card,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.ink).clickable { onAccept(s.id) }.padding(horizontal = 16.dp, vertical = 9.dp).testTag("accept-${s.id}"),
                    )
                    Text(
                        "Skip", style = Paper.type.button, color = colors.ink,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.rule, RoundedCornerShape(8.dp)).clickable { onSkip(s.id) }.padding(horizontal = 16.dp, vertical = 9.dp).testTag("skip-${s.id}"),
                    )
                }
            }
        }
    }
}
