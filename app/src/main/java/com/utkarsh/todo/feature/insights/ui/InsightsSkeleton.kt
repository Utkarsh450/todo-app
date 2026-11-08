package com.utkarsh.todo.feature.insights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Insights while they load: three stat cards, the heat-map card and the weekly bars card. */
@Composable
fun InsightsSkeleton(modifier: Modifier = Modifier) {
    val card = RoundedCornerShape(PaperRadius.card)
    SkeletonSurface("Loading insights", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "insights-skeleton") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SkeletonBlock(Modifier.weight(1f).height(76.dp), card) }
        }
        SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth().height(190.dp), card)
        SkeletonBlock(Modifier.padding(top = 10.dp).fillMaxWidth().height(150.dp), card)
    }
}
