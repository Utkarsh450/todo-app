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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.paperSurface

/** The snooze choices on the alert, in minutes. The first is the big "Snooze 10 min" button, the rest are small links. */
val AlertSnoozeChoices = listOf(10, 5, 30, 60)

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

private val AlertBackground = Color(0xFF191714)
private val AlertText = Color(0xFFEFE9DD)
private val AlertMuted = Color(0xFFB5AC9B)
private val AlertCard = Color(0xFFFFFDF8)
private const val UNDO_MS = 3_000L

/** What was just tapped, shown for a moment with Undo before it really happens. */
private sealed interface Pending {
    data class Snoozed(val minutes: Int) : Pending
    data object Done : Pending
}

/**
 * Design screen 24: the dark full-screen takeover when an alarm-style alert fires, like a calendar's. The time sits
 * in two slowly widening rings, then what it is about, Done, Snooze 10 min and Open task. Tapping Done or Snooze
 * shows "Nice. One less." or "Back at ..." with Undo for three seconds, and only then does the action happen.
 * Stateless apart from that pause; the activity wires the real actions.
 */
@Composable
fun ReminderAlertScreen(
    ui: ReminderAlertUi,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The words after "Snoozed": by default "Back in 10 min"; the activity says "Back at 11:10 AM". */
    snoozedText: (Int) -> String = { "Back in ${snoozeLabel(it)}" },
) {
    val colors = Paper.colors
    var pending by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Pending?, String>(
        save = { p -> when (p) { is Pending.Snoozed -> "s${p.minutes}"; Pending.Done -> "d"; null -> "" } },
        restore = { v -> when { v.startsWith("s") -> Pending.Snoozed(v.drop(1).toInt()); v == "d" -> Pending.Done; else -> null } },
    )) { mutableStateOf<Pending?>(null) }
    LaunchedEffect(pending) {
        val current = pending ?: return@LaunchedEffect
        delay(UNDO_MS)
        when (current) { is Pending.Snoozed -> onSnooze(current.minutes); Pending.Done -> onDismiss() }
    }

    Box(modifier.fillMaxSize().background(AlertBackground).testTag("reminder-alert")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accentLight))
                MonoLabel("Reminder · now", Modifier.padding(start = 8.dp), color = colors.accentLight)
            }

            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                RingingClock(ui.time)
                MonoLabel(ui.date, Modifier.padding(top = 18.dp).testTag("alert-date"), color = AlertMuted)
                Text(
                    ui.title, style = Paper.type.display, color = AlertText, textAlign = TextAlign.Center, maxLines = 4,
                    modifier = Modifier.padding(top = 18.dp).testTag("alert-title"),
                )
                Text(ui.status.replaceFirstChar { it.uppercase() }, style = Paper.type.note, color = AlertMuted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 8.dp).testTag("alert-status"))
            }

            when (val p = pending) {
                null -> Unit
                else -> UndoBar(
                    label = if (p is Pending.Snoozed) "Snoozed" else "Done",
                    text = if (p is Pending.Snoozed) snoozedText(p.minutes) else "Nice. One less.",
                    onUndo = { pending = null },
                )
            }
            val shape = RoundedCornerShape(14.dp)
            Text(
                "Done", style = Paper.type.button, color = AlertBackground, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(AlertCard).clickable(enabled = pending == null) { pending = Pending.Done }.padding(vertical = 18.dp).testTag("alert-dismiss"),
            )
            Text(
                "Snooze 10 min", style = Paper.type.button, color = AlertText, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape).border(1.dp, AlertText.copy(alpha = 0.3f), shape)
                    .clickable(enabled = pending == null) { pending = Pending.Snoozed(10) }.padding(vertical = 16.dp).testTag("alert-snooze-10"),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AlertSnoozeChoices.drop(1).forEach { minutes ->
                    Text(
                        snoozeLabel(minutes), style = Paper.type.label, color = AlertMuted,
                        modifier = Modifier.clickable(enabled = pending == null) { pending = Pending.Snoozed(minutes) }.padding(horizontal = 14.dp, vertical = 10.dp).testTag("alert-snooze-$minutes"),
                    )
                }
            }
            Text(
                if (ui.canOpenTask) "Open task" else "Open Paper Todo", style = Paper.type.button, color = AlertMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 14.dp).testTag("alert-open"),
            )
        }
    }
}

@Composable
private fun UndoBar(label: String, text: String, onUndo: () -> Unit) {
    val colors = Paper.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(shape).background(AlertText.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 12.dp).testTag("alert-undo-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(label, color = colors.accentLight)
        Text(text, style = Paper.type.body, color = AlertText, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text("Undo", style = Paper.type.button, color = colors.accentLight, modifier = Modifier.clickable(onClick = onUndo).padding(8.dp).testTag("alert-undo"))
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
            drawCircle(AlertText.copy(alpha = 0.18f), radius = maxRadius * 0.62f, center = center, style = Stroke(width = 1.5.dp.toPx()))
        }
        Text(time, style = Paper.type.display.copy(fontSize = 44.sp), color = AlertText, textAlign = TextAlign.Center, modifier = Modifier.testTag("alert-time"))
    }
}
