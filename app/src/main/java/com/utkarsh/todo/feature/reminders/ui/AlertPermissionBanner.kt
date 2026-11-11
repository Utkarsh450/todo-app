package com.utkarsh.todo.feature.reminders.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.feature.reminders.platform.AlertPermissions
import com.utkarsh.todo.feature.reminders.platform.MissingPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlertPermissionsViewModel @Inject constructor(val permissions: AlertPermissions) : ViewModel()

/** Asks for whatever alerts still need, and checks again every time the app comes back to the screen. */
@Composable
fun AlertPermissionPrompt(modifier: Modifier = Modifier, viewModel: AlertPermissionsViewModel = hiltViewModel()) {
    val permissions = viewModel.permissions
    val context = LocalContext.current
    var missing by remember { mutableStateOf(permissions.missing()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) missing = permissions.missing() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { missing = permissions.missing() }
    AlertPermissionBanner(missing, modifier) {
        val current = missing ?: return@AlertPermissionBanner
        if (current == MissingPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { context.startActivity(permissions.settingsIntent(current)) }
        }
    }
}

/** The bar above the tabs: what to allow, and one button that takes the person there. Nothing is drawn when nothing is missing. */
@Composable
fun AlertPermissionBanner(missing: MissingPermission?, modifier: Modifier = Modifier, onAllow: () -> Unit) {
    if (missing == null) return
    val colors = Paper.colors
    val text = when (missing) {
        MissingPermission.NOTIFICATIONS -> "Allow notifications so alerts can reach you."
        MissingPermission.OVERLAY -> "Allow \"display over other apps\" so alerts open full screen even while you are using the phone."
        MissingPermission.FULL_SCREEN -> "Allow full-screen alerts so reminders pop up on your screen, like Google Calendar."
        MissingPermission.EXACT_ALARMS -> "Allow exact alarms so alerts ring right on time."
    }
    Column(modifier.fillMaxWidth().background(colors.ink).testTag("alert-permission-banner")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Paper.type.bodySmall, color = colors.card, modifier = Modifier.weight(1f))
            MonoLabel(
                "Allow", color = colors.accentLight,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAllow).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).testTag("alert-permission-allow"),
            )
        }
    }
}
