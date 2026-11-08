package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper

/** A thin strip saying the screen is showing saved data because the server can't be reached. */
@Composable
fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Row(
        modifier.fillMaxWidth().background(colors.ink).padding(horizontal = 20.dp, vertical = 8.dp).testTag("offline-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("Offline · showing saved data", Modifier.weight(1f), color = colors.card)
        MonoLabel("Retry", color = colors.accentLight, modifier = Modifier.clickable(onClick = onRetry).padding(start = 12.dp, top = 4.dp, bottom = 4.dp).testTag("offline-retry"))
    }
}
