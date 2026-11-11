package com.utkarsh.todo.feature.reminders.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface

/** The snooze choices on the alert, in minutes. The first is what the notification's own Snooze button uses. */
val AlertSnoozeChoices = listOf(5, 10, 30, 60)

/** What the full-screen alert shows. [time] is the due time (`9:30 AM`), [date] a short day (`SUN 8 NOV`). */
data class ReminderAlertUi(
    val title: String,
    val time: String,
    val date: String,
    /** `Due now`, `5 min ago`, or `Repeats every day`. */
    val status: String,
    val canOpenTask: Boolean = false,
)

fun snoozeLabel(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} hr" else "$minutes min"

/**
 * The screen that takes over the display when a reminder rings, like a calendar's alert: the time in a ringing
 * circle, what it is about, quick snooze choices and one big button to stop it. Stateless; the activity wires it.
 */
@Composable
fun ReminderAlertScreen(
    ui: ReminderAlertUi,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).testTag("reminder-alert")) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
            MonoLabel("Reminder", Modifier.padding(start = 8.dp), color = colors.accent)
            Spacer(Modifier.weight(1f))
            MonoLabel(ui.date, Modifier.testTag("alert-date"))
        }

        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            RingingClock(ui.time)
            Text(
                ui.title, style = Paper.type.display, color = colors.ink, textAlign = TextAlign.Center, maxLines = 4,
                modifier = Modifier.padding(top = 36.dp).testTag("alert-title"),
            )
            MonoLabel(ui.status, Modifier.padding(top = 10.dp).testTag("alert-status"))
        }

        MonoLabel("Snooze", Modifier.padding(bottom = 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertSnoozeChoices.forEach { minutes ->
                val shape = RoundedCornerShape(12.dp)
                Text(
                    snoozeLabel(minutes), style = Paper.type.button, color = colors.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(shape).background(colors.card).border(1.dp, colors.rule, shape)
                        .clickable { onSnooze(minutes) }.padding(vertical = 14.dp).testTag("alert-snooze-$minutes"),
                )
            }
        }
        val shape = RoundedCornerShape(14.dp)
        Text(
            "Done", style = Paper.type.button, color = colors.card, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(colors.ink).clickable(onClick = onDismiss).padding(vertical = 18.dp).testTag("alert-dismiss"),
        )
        Text(
            if (ui.canOpenTask) "Open task" else "Open Paper Todo", style = Paper.type.button, color = colors.muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 18.dp).testTag("alert-open"),
        )
    }
}

/** The due time inside two slowly widening rings, so the screen looks like it is ringing. */
@Composable
private fun RingingClock(time: String) {
    val colors = Paper.colors
    val transition = rememberInfiniteTransition(label = "ring")
    val pulse = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2
            listOf(0f, 0.5f).forEach { offset ->
                val t = (pulse.value + offset) % 1f
                drawCircle(colors.accent.copy(alpha = (1f - t) * 0.5f), radius = maxRadius * (0.62f + 0.38f * t), center = center, style = Stroke(width = 2.dp.toPx()))
            }
            drawCircle(colors.card, radius = maxRadius * 0.62f, center = center)
            drawCircle(colors.accent, radius = maxRadius * 0.62f, center = center, style = Stroke(width = 2.dp.toPx()))
        }
        Text(time, style = Paper.type.headline, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.testTag("alert-time"))
    }
}
