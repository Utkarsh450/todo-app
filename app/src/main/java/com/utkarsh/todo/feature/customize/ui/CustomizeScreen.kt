package com.utkarsh.todo.feature.customize.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.PaperSwitch
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.feature.customize.domain.PaperBackground
import com.utkarsh.todo.feature.customize.domain.RowDensity
import com.utkarsh.todo.feature.customize.domain.TodayBlock

@Composable
fun CustomizeRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CustomizeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CustomizeScreen(
        state = state,
        onBack = onBack,
        onSetVisible = viewModel::setVisible,
        onMove = viewModel::move,
        onBackground = viewModel::setBackground,
        onDensity = viewModel::setDensity,
        onReset = viewModel::resetToDefaults,
        modifier = modifier,
    )
}

/** Today blocks (show/hide, reorder), paper background and row density. Changes apply as you make them. */
@Composable
fun CustomizeScreen(
    state: CustomizeUiState,
    onBack: () -> Unit,
    onSetVisible: (TodayBlock, Boolean) -> Unit,
    onMove: (TodayBlock, Int) -> Unit,
    onBackground: (PaperBackground) -> Unit,
    onDensity: (RowDensity) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val settings = state.settings
    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Today",
                style = Paper.type.button,
                color = colors.ink,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp).testTag("customize-back"),
            )
            Box(Modifier.weight(1f))
            MonoLabel("Saved automatically")
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Customize", style = Paper.type.displayLarge, color = colors.ink, modifier = Modifier.padding(bottom = 6.dp).testTag("customize-title"))
            Text("Choose what Today shows and how it looks.", style = Paper.type.note, color = colors.muted, modifier = Modifier.padding(bottom = 18.dp))

            SectionLabel("Today blocks")
            PaperCard(Modifier.fillMaxWidth().testTag("blocks-card")) {
                settings.blockOrder.forEachIndexed { index, block ->
                    if (index > 0) HorizontalDivider(color = colors.rule)
                    BlockRow(
                        block = block,
                        visible = settings.isVisible(block),
                        canMoveUp = settings.canMoveUp(block),
                        canMoveDown = settings.canMoveDown(block),
                        onVisible = { onSetVisible(block, it) },
                        onMove = { onMove(block, it) },
                    )
                }
            }

            SectionLabel("Paper")
            PaperSegmentedControl(
                options = PaperBackground.entries,
                selected = settings.background,
                onSelect = onBackground,
                labelOf = { it.label },
                modifier = Modifier.testTag("background-control"),
            )

            SectionLabel("Density")
            PaperSegmentedControl(
                options = RowDensity.entries,
                selected = settings.density,
                onSelect = onDensity,
                labelOf = { it.label },
                modifier = Modifier.testTag("density-control"),
            )

            Text(
                "Reset to defaults",
                style = Paper.type.button,
                color = colors.accent,
                modifier = Modifier.padding(top = 28.dp, bottom = 32.dp).clickable(onClick = onReset).padding(vertical = 10.dp).testTag("customize-reset"),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    MonoLabel(text, Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun BlockRow(
    block: TodayBlock,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisible: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val colors = Paper.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 14.dp, end = 10.dp).testTag("block-row-${block.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (visible) 1f else 0.45f)) {
            Text(block.label, style = Paper.type.title.copy(fontSize = 17.sp), color = colors.ink)
            Text(block.hint, style = Paper.type.bodySmall, color = colors.muted)
        }
        MoveButton("▲", "Move ${block.label} up", canMoveUp, Modifier.testTag("move-up-${block.name}")) { onMove(-1) }
        MoveButton("▼", "Move ${block.label} down", canMoveDown, Modifier.testTag("move-down-${block.name}")) { onMove(+1) }
        PaperSwitch(
            checked = visible,
            onCheckedChange = onVisible,
            label = "Show ${block.label}",
            modifier = Modifier.padding(start = 6.dp, end = 4.dp).testTag("block-switch-${block.name}"),
        )
    }
}

@Composable
private fun MoveButton(glyph: String, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = 2.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Paper.type.monoMeta, color = Paper.colors.ink)
    }
}
