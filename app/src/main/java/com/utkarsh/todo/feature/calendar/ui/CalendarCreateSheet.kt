package com.utkarsh.todo.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.component.PaperSwitch
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.feature.calendar.domain.AgendaKind
import com.utkarsh.todo.feature.calendar.domain.AlertChoices
import com.utkarsh.todo.feature.calendar.domain.CreateKind
import com.utkarsh.todo.feature.calendar.domain.RepeatChoices
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class CreateSheetActions(
    val onKind: (CreateKind) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onStart: (LocalTime) -> Unit = {},
    val onEnd: (LocalTime) -> Unit = {},
    val onAllDay: (Boolean) -> Unit = {},
    val onAlert: (Int?) -> Unit = {},
    val onRepeat: (String?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onClose: () -> Unit = {},
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** One bottom sheet for making an event, a task or a reminder, so the choice is one tap and nothing else changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(sheet: CreateSheetUi, actions: CreateSheetActions) {
    val colors = Paper.colors
    val draft = sheet.draft
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("create-sheet")) {
            PaperSegmentedControl(CreateKind.entries, draft.kind, actions.onKind, labelOf = { it.label }, modifier = Modifier.testTag("create-kind"))
            BasicTextField(
                value = draft.title, onValueChange = actions.onTitle,
                textStyle = Paper.type.display.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).testTag("create-title"),
                decorationBox = { inner ->
                    Box {
                        if (draft.title.isEmpty()) Text("Add title", style = Paper.type.display, color = colors.mutedSoft)
                        inner()
                    }
                },
            )
            HorizontalDivider(color = colors.rule, modifier = Modifier.padding(top = 6.dp))

            FieldRow("Date", DATE_FORMAT.format(draft.date), "create-date") { picking = "date" }
            if (draft.kind != CreateKind.REMINDER) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("All day", Modifier.weight(1f))
                    PaperSwitch(draft.allDay, actions.onAllDay, Modifier.testTag("create-allday"), label = "All day")
                }
            }
            if (draft.hasTime) {
                FieldRow(if (draft.kind == CreateKind.EVENT) "Starts" else "Time", TIME_FORMAT.format(draft.start), "create-start") { picking = "start" }
            }
            if (draft.kind == CreateKind.EVENT && !draft.allDay) FieldRow("Ends", TIME_FORMAT.format(draft.end), "create-end") { picking = "end" }

            if (draft.kind == CreateKind.EVENT) {
                MonoLabel("Alert before", Modifier.padding(top = 18.dp, bottom = 8.dp))
                ChoiceChips(AlertChoices, draft.alertMinutes, actions.onAlert, "alert")
            } else {
                MonoLabel("Repeat", Modifier.padding(top = 18.dp, bottom = 8.dp))
                ChoiceChips(RepeatChoices, draft.repeat, actions.onRepeat, "repeat")
            }

            (sheet.error)?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(top = 12.dp).testTag("create-error")) }
            val enabled = !sheet.saving
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(PaperRadius.card)).background(colors.ink)
                    .clickable(enabled = enabled, onClick = actions.onSave).padding(vertical = 14.dp).testTag("create-save"),
                contentAlignment = Alignment.Center,
            ) { Text(if (sheet.saving) "Saving…" else "Save ${draft.kind.label.lowercase()}", style = Paper.type.button, color = colors.card) }
        }
    }

    when (picking) {
        "date" -> DatePickerSheet(draft.date, { picking = null }) { actions.onDate(it); picking = null }
        "start" -> TimePickerSheet(draft.start, { picking = null }) { actions.onStart(it); picking = null }
        "end" -> TimePickerSheet(draft.end, { picking = null }) { actions.onEnd(it); picking = null }
    }
}

@Composable
private fun FieldRow(label: String, value: String, tag: String, onClick: () -> Unit) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 14.dp, bottom = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel(label, Modifier.weight(1f))
        Text(value, style = Paper.type.title, color = colors.ink)
    }
}

@Composable
private fun <T> ChoiceChips(choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, tagPrefix: String) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(50)
            Text(
                label, style = Paper.type.label, color = if (on) colors.card else colors.ink,
                modifier = Modifier.clip(shape).background(if (on) colors.ink else colors.card).border(1.dp, if (on) colors.ink else colors.rule, shape)
                    .clickable { onSelect(value) }.padding(horizontal = 14.dp, vertical = 9.dp).testTag("$tagPrefix-${value ?: "none"}"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text(
                "OK", style = Paper.type.button, color = Paper.colors.accent,
                modifier = Modifier.clickable { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }.padding(12.dp).testTag("date-ok"),
            )
        },
        dismissButton = { Text("Cancel", style = Paper.type.button, color = Paper.colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp)) },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Text("OK", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.clickable { onPick(LocalTime.of(state.hour, state.minute)) }.padding(12.dp).testTag("time-ok")) },
        dismissButton = { Text("Cancel", style = Paper.type.button, color = Paper.colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp)) },
        text = { TimePicker(state) },
        containerColor = Paper.colors.card,
    )
}

/** What an event or reminder opened from the calendar looks like, with the one thing you can do to it. */
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
            MonoLabel(if (detail.kind == AgendaKind.EVENT) "Event" else "Reminder", color = colors.accent)
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
