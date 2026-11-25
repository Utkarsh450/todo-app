package com.utkarsh.todo.feature.memory.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.SkeletonCard
import com.utkarsh.todo.core.designsystem.component.SkeletonSurface

/** What the Memory screen shows while the list loads: two cards of rows. */
@Composable
fun MemorySkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading what I remember", modifier.fillMaxWidth().padding(horizontal = 20.dp), testTag = "memory-skeleton") {
        SkeletonCard(rows = 3)
        SkeletonCard(rows = 2, modifier = Modifier.padding(top = 14.dp))
    }
}
