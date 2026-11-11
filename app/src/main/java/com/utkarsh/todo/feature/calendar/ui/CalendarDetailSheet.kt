package com.utkarsh.todo.feature.calendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.feature.calendar.domain.AgendaKind

/** What an event or alert opened from the calendar looks like, with the one thing you can do to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(detail: DetailUi, onClose: () -> Unit, onDelete: () -> Unit) {
    val colors = Paper.colors
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("detail-sheet")) {
            MonoLabel(if (detail.kind == AgendaKind.EVENT) "Event" else "Alert", color = colors.accent)
            Text(detail.title, style = Paper.type.display, color = colors.ink, modifier = Modifier.padding(top = 6.dp).testTag("detail-title"))
            Text(detail.whenLabel, style = Paper.type.monoMeta, color = colors.muted, modifier = Modifier.padding(top = 6.dp).testTag("detail-when"))
            detail.error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 12.dp).testTag("detail-error")) }
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().alpha(if (detail.deleting) 0.4f else 1f).clip(RoundedCornerShape(PaperRadius.card)).border(1.dp, colors.accent, RoundedCornerShape(PaperRadius.card))
                    .clickable(enabled = !detail.deleting, onClick = onDelete).padding(vertical = 14.dp).testTag("detail-delete"),
                contentAlignment = Alignment.Center,
            ) { Text(if (detail.deleting) "Deleting…" else "Delete", style = Paper.type.button, color = colors.accent) }
        }
    }
}
