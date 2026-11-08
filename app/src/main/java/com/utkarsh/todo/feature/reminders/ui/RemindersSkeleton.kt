package com.utkarsh.todo.feature.reminders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.utkarsh.todo.core.designsystem.theme.PaperRadius

/** Reminders while they load: the "next up" card and a card of rows with a time and a switch. */
@Composable
fun RemindersSkeleton(modifier: Modifier = Modifier) {
    SkeletonSurface("Loading reminders", modifier.fillMaxWidth().padding(horizontal = 14.dp), testTag = "reminders-skeleton") {
        SkeletonBlock(Modifier.fillMaxWidth().height(140.dp), RoundedCornerShape(PaperRadius.card))
        SkeletonBlock(Modifier.padding(top = 20.dp, bottom = 8.dp, start = 6.dp).width(60.dp).height(10.dp), RoundedCornerShape(50))
        PaperCard(Modifier.fillMaxWidth()) {
            repeat(3) { index ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SkeletonBlock(Modifier.width(44.dp).height(20.dp), RoundedCornerShape(4.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(if (index == 1) 0.8f else 0.6f, height = 13.dp)
                        SkeletonLine(0.3f, height = 9.dp)
                    }
                    SkeletonBlock(Modifier.width(44.dp).height(26.dp), RoundedCornerShape(50))
                }
            }
        }
    }
}
