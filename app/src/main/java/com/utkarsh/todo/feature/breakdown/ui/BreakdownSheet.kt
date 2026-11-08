package com.utkarsh.todo.feature.breakdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCheckbox
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Bottom sheet that suggests subtasks for the open task. [onDismiss] runs on close, whether or not anything was added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownSheet(onDismiss: () -> Unit, viewModel: BreakdownViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.added.collect { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paper.colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        BreakdownContent(state, viewModel::toggle, viewModel::addSelected, viewModel::suggest, onDismiss)
    }
}

@Composable
fun BreakdownContent(
    state: BreakdownUiState,
    onToggle: (Int) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        MonoLabel("✦ Break it down", color = colors.accent)
        Text("Suggested subtasks", style = Paper.type.headline, color = colors.ink, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        when (state.phase) {
            BreakdownPhase.LOADING -> Row(
                Modifier.fillMaxWidth().padding(vertical = 28.dp).testTag("breakdown-loading"),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("Thinking it through…", style = Paper.type.note, color = colors.muted)
            }

            BreakdownPhase.READY -> {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    state.suggestions.forEachIndexed { index, suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(index) }.padding(vertical = 10.dp).testTag("suggestion-$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PaperCheckbox(suggestion.selected, { onToggle(index) }, label = "Add ${suggestion.title}")
                            Text(
                                suggestion.title, style = Paper.type.body, color = colors.ink,
                                modifier = Modifier.alpha(if (suggestion.selected) 1f else 0.45f),
                            )
                        }
                    }
                }
                state.message?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 8.dp).testTag("breakdown-message")) }
                Text("Nothing is added until you tap the button.", style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
                val count = state.selectedCount
                val enabled = count > 0 && !state.adding
                ActionButton(
                    label = when {
                        state.adding -> "Adding…"
                        count == 0 -> "Pick at least one"
                        count == 1 -> "Add 1 subtask"
                        else -> "Add $count subtasks"
                    },
                    enabled = enabled,
                    onClick = onAdd,
                    tag = "breakdown-add",
                )
            }

            BreakdownPhase.ERROR -> Problem(
                title = "Couldn't get suggestions",
                body = state.message.orEmpty(),
                action = { ActionButton("Try again", true, onRetry, "breakdown-retry") },
            )

            BreakdownPhase.NOT_CONFIGURED -> Problem(
                title = "Grok isn't set up yet",
                body = "Add your xAI key as XAI_API_KEY in backend/.env, then restart the backend.",
                action = { ActionButton("Close", true, onClose, "breakdown-close") },
            )
        }
    }
}

@Composable
private fun Problem(title: String, body: String, action: @Composable () -> Unit) {
    val colors = Paper.colors
    Column(Modifier.fillMaxWidth().testTag("breakdown-problem")) {
        Text(title, style = Paper.type.body, color = colors.ink)
        Text(body, style = Paper.type.bodySmall, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        action()
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val colors = Paper.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PaperRadius.card))
            .background(colors.ink)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Paper.type.button, color = colors.card, textAlign = TextAlign.Center)
    }
}
