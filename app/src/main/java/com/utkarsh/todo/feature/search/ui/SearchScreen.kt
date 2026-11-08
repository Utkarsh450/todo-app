package com.utkarsh.todo.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.ProjectTag
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor

@Composable
fun SearchRoute(onBack: () -> Unit, onOpenTask: (String) -> Unit, modifier: Modifier = Modifier, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(state, onBack, viewModel::onQueryChange, viewModel::clear, viewModel::retry, onOpenTask, modifier)
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Today", style = Paper.type.button, color = colors.ink, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("search-back"))
        }
        Text("Search", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp))
        val shape = RoundedCornerShape(PaperRadius.card)
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(shape).background(colors.card).border(1.dp, colors.rule, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = state.query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = Paper.type.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-input"),
                )
                if (state.query.isEmpty()) Text("Search your tasks", style = Paper.type.body, color = colors.mutedSoft)
            }
            if (state.query.isNotEmpty()) {
                Text("✕", style = Paper.type.body, color = colors.muted, modifier = Modifier.semantics { contentDescription = "Clear search" }.clickable(onClick = onClear).padding(start = 12.dp).testTag("search-clear"))
            }
        }
        Box(Modifier.padding(top = 14.dp).weight(1f)) {
            when (state.phase) {
                SearchPhase.IDLE -> Message("Find a task by its title.", "Try a word you remember, like “invoice”.", "search-idle")
                SearchPhase.LOADING -> SearchSkeleton()
                SearchPhase.EMPTY -> Message("Nothing matches “${state.query.trim()}”", "Check the spelling, or try a shorter word.", "search-empty")
                SearchPhase.ERROR -> Message("Couldn't search", state.error.orEmpty(), "search-error", action = "Try again" to onRetry)
                SearchPhase.RESULTS -> Results(state, onOpenTask)
            }
        }
    }
}

@Composable
private fun Results(state: SearchUiState, onOpenTask: (String) -> Unit) {
    val colors = Paper.colors
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp).testTag("search-results")) {
        item(key = "count") {
            MonoLabel("${state.results.size} result${if (state.results.size == 1) "" else "s"}", Modifier.padding(start = 6.dp, bottom = 8.dp).testTag("search-count"))
        }
        item(key = "card") {
            PaperCard(Modifier.fillMaxWidth()) {
                state.results.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = colors.rule)
                    Column(Modifier.fillMaxWidth().clickable { onOpenTask(row.id) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("result-${row.id}")) {
                        Text(
                            row.title, style = Paper.type.title, color = if (row.done) colors.muted else colors.ink,
                            textDecoration = if (row.done) TextDecoration.LineThrough else null,
                        )
                        if (row.dueLabel != null || row.projectName != null) {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.dueLabel?.let { MonoLabel(it) }
                                row.projectName?.let { ProjectTag(it, parseHexColor(row.projectColorHex.orEmpty())) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, tag: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxSize().padding(24.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Paper.type.headline, color = Paper.colors.ink, textAlign = TextAlign.Center)
        if (body.isNotEmpty()) Text(body, style = Paper.type.bodySmall, color = Paper.colors.muted, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
        action?.let { (label, onClick) ->
            Text(label, style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onClick).padding(10.dp).testTag("search-retry"))
        }
    }
}
