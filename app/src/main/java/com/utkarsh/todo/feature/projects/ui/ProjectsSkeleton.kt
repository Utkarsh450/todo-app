package com.utkarsh.todo.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.SkeletonBlock
import com.utkarsh.todo.core.designsystem.component.SkeletonLine
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface

/** Projects while they load: a stack of cards, each with a name and a progress bar. */
@Composable
fun ProjectsSkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading projects", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "projects-skeleton") {
        repeat(5) { index ->
            PaperCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBlock(Modifier.size(10.dp), RoundedCornerShape(2.dp))
                        SkeletonLine(if (index % 2 == 0) 0.4f else 0.3f, modifier = Modifier.weight(1f, fill = false), height = 16.dp)
                        SkeletonBlock(Modifier.width(26.dp).height(10.dp), RoundedCornerShape(50))
                    }
                    SkeletonLine(1f, height = 5.dp)
                }
            }
        }
    }
}
