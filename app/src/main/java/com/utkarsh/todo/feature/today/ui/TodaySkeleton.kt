package com.utkarsh.todo.feature.today.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** What Today looks like while its tasks load: the pinned card and a list card. */
@Composable
fun TodaySkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading today's tasks", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "today-skeleton") {
        SkeletonBlock(Modifier.fillMaxWidth().height(112.dp), RoundedCornerShape(PaperRadius.card))
        Column(Modifier.padding(top = 12.dp)) { SkeletonCard(rows = 4) }
    }
}
