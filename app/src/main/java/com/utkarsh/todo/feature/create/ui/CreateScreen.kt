package com.utkarsh.todo.feature.create.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.MonoLabel
import com.utkarsh.todo.core.designsystem.component.PaperCard
import com.utkarsh.todo.core.designsystem.component.PaperSegmentedControl
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.core.designsystem.theme.PaperRadius
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import com.utkarsh.todo.feature.create.domain.AlertPresets
import com.utkarsh.todo.feature.create.domain.CreateForm
import com.utkarsh.todo.feature.create.domain.CreateType
import com.utkarsh.todo.feature.create.domain.DurationPresets
import com.utkarsh.todo.feature.create.domain.RepeatPresets
import com.utkarsh.todo.feature.create.domain.TimePresets
import com.utkarsh.todo.feature.create.domain.alertLabel
import com.utkarsh.todo.feature.create.domain.repeatLabel
import com.utkarsh.todo.feature.reminders.domain.AlertStyle
import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

class CreateActions(
    val onClose: () -> Unit = {},
    val onType: (CreateType) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onTime: (LocalTime?) -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onAllDay: () -> Unit = {},
    val onToggleAlert: (Int) -> Unit = {},
    val onAddAlert: (Int) -> Unit = {},
    val onRepeat: (String?) -> Unit = {},
    val onAlertStyle: (AlertStyle) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onLocation: (String) -> Unit = {},
    val onVideoLink: (String) -> Unit = {},
    val onBusy: (Boolean) -> Unit = {},
    val onAddGuest: (String) -> Unit = {},
    val onRemoveGuest: (String) -> Unit = {},
    val onPriority: (Priority) -> Unit = {},
    val onProject: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun CreateRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: CreateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onClose() } }
    CreateScreen(
        state = state,
        actions = CreateActions(
            onClose = onClose, onType = viewModel::setType, onTitle = viewModel::setTitle, onDate = viewModel::setDate,
            onTime = viewModel::setTime, onDuration = viewModel::setDuration, onAllDay = viewModel::setAllDay,
            onToggleAlert = viewModel::toggleAlert, onAddAlert = viewModel::addAlert, onRepeat = viewModel::setRepeat, onAlertStyle = viewModel::setAlertStyle, onNotes = viewModel::setNotes, onLocation = viewModel::setLocation, onVideoLink = viewModel::setVideoLink, onBusy = viewModel::setBusy,
            onAddGuest = viewModel::addGuest, onRemoveGuest = viewModel::removeGuest,
            onPriority = viewModel::setPriority, onProject = viewModel::toggleProject, onSave = viewModel::save,
        ),
        modifier = modifier,
    )
}

/** Screen 15: make a task or an event with chips; the long lists live behind "Pick date…" and "Custom…". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(state: CreateUiState, actions: CreateActions, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    val colors = Paper.colors
    val form = state.form
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().paperSurface().statusBarsPadding().imePadding().testTag("create-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✕", style = Paper.type.title, color = colors.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = actions.onClose).padding(horizontal = 8.dp, vertical = 4.dp).testTag("create-close"),
            )
            Box(Modifier.weight(1f))
            if (state.editingEventId == null) PaperSegmentedControl(CreateType.entries, form.type, actions.onType, labelOf = { it.label }, modifier = Modifier.testTag("create-type"))
            else MonoLabel("Edit event", color = colors.accent, modifier = Modifier.testTag("create-edit-label"))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            BasicTextField(
                value = form.title, onValueChange = actions.onTitle,
                textStyle = Paper.type.display.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("create-title"),
                decorationBox = { inner ->
                    Box {
                        if (form.title.isEmpty()) Text(if (form.type == CreateType.TASK) "What needs doing?" else "What's happening?", style = Paper.type.display, color = colors.mutedSoft)
                        inner()
                    }
                },
            )
            HorizontalDivider(color = colors.rule, modifier = Modifier.padding(top = 6.dp))
            if (form.type == CreateType.TASK) {
                BasicTextField(
                    value = form.notes, onValueChange = actions.onNotes,
                    textStyle = Paper.type.note.copy(color = colors.muted), cursorBrush = SolidColor(colors.ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("create-notes"),
                    decorationBox = { inner ->
                        Box {
                            if (form.notes.isEmpty()) Text("Add notes or a link…", style = Paper.type.note, color = colors.mutedSoft)
                            inner()
                        }
                    },
                )
            }

            Group("Day") {
                Chip("Today", form.date == today, "day-today") { actions.onDate(today) }
                Chip("Tomorrow", form.date == today.plusDays(1), "day-tomorrow") { actions.onDate(today.plusDays(1)) }
                if (form.date != today && form.date != today.plusDays(1)) Chip(DAY_FORMAT.format(form.date), true, "day-custom") { picking = "date" }
                Link("Pick date…", "day-pick") { picking = "date" }
            }
            Group("Time") {
                if (form.type == CreateType.TASK) Chip("No time", form.time == null, "time-none") { actions.onTime(null) }
                TimePresets.forEach { t -> Chip(TIME_FORMAT.format(t), form.time == t && !form.allDay, "time-${t.hour}") { actions.onTime(t) } }
                form.time?.takeIf { it !in TimePresets && !form.allDay }?.let { Chip(TIME_FORMAT.format(it), true, "time-custom") { picking = "time" } }
                Link("Pick time…", "time-pick") { picking = "time" }
            }
            if (form.type == CreateType.EVENT) {
                Group("Length") {
                    DurationPresets.forEach { m -> Chip(durationLabel(m), form.durationMinutes == m && !form.allDay, "duration-$m") { actions.onDuration(m) } }
                    Chip("All day", form.allDay, "duration-allday") { actions.onAllDay() }
                }
            }
            if (state.editingEventId == null) Group("Alerts") {
                (AlertPresets + form.alerts.filter { it !in AlertPresets }.sorted()).forEach { m ->
                    Chip(alertLabel(m), m in form.alerts, "alert-$m") { actions.onToggleAlert(m) }
                }
                Link("Custom…", "alert-custom") { picking = "alert" }
            }
            if (state.editingEventId == null && form.alerts.isNotEmpty()) {
                Group("Alert style") {
                    Chip("Notification", form.alertStyle == AlertStyle.NOTIFICATION, "style-notification") { actions.onAlertStyle(AlertStyle.NOTIFICATION) }
                    Chip("Alarm", form.alertStyle == AlertStyle.ALARM, "style-alarm") { actions.onAlertStyle(AlertStyle.ALARM) }
                }
            }
            if (form.type == CreateType.TASK) {
                Group("Repeat") {
                    Chip("Never", form.repeat == null, "repeat-never") { actions.onRepeat(null) }
                    RepeatPresets.forEach { r -> Chip(repeatLabel(r), form.repeat == r, "repeat-${r.substringAfter(' ').lowercase()}") { actions.onRepeat(r) } }
                    form.repeat?.takeIf { it !in RepeatPresets }?.let { Chip(it, true, "repeat-custom") { picking = "repeat" } }
                    Link("Custom…", "repeat-custom-open") { picking = "repeat" }
                }
                Group("Priority") {
                    Priority.entries.forEach { p -> Chip(p.name.lowercase().replaceFirstChar { it.uppercase() }, form.priority == p, "priority-${p.name.lowercase()}") { actions.onPriority(p) } }
                }
            }
            if (form.type == CreateType.EVENT) EventDetails(form, actions)
            if (state.projects.isNotEmpty()) {
                Group("Project") {
                    state.projects.forEach { p ->
                        Chip(p.name, form.projectId == p.id, "project-${p.id}", dot = parseHexColor(p.colorHex)) { actions.onProject(p.id) }
                    }
                }
            }
            Preview(state, today)
            Box(Modifier.padding(bottom = 12.dp))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp, top = 6.dp)) {
            state.error?.let { Text(it, style = Paper.type.bodySmall, color = colors.accent, modifier = Modifier.padding(bottom = 8.dp).testTag("create-error")) }
            val enabled = !state.saving
            Box(
                Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(PaperRadius.card)).background(colors.ink)
                    .clickable(enabled = enabled, onClick = actions.onSave).padding(vertical = 14.dp).testTag("create-save"),
                contentAlignment = Alignment.Center,
            ) { Text(if (state.saving) "Saving…" else if (state.editingEventId != null) "Save changes" else "Save ${form.type.label.lowercase()}", style = Paper.type.button, color = colors.card) }
        }
    }

    when (picking) {
        "date" -> DateSheet(form.date, { picking = null }) { actions.onDate(it); picking = null }
        "time" -> TimeSheet(form.effectiveTime, { picking = null }) { actions.onTime(it); picking = null }
        "alert" -> AlertsSheet({ picking = null }) { actions.onAddAlert(it); picking = null }
        "repeat" -> RepeatSheet({ picking = null }) { actions.onRepeat(it); picking = null }
    }
}

/** Where, video link, guests and busy/free: the rest of what an event can carry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetails(form: CreateForm, actions: CreateActions) {
    val colors = Paper.colors
    var guest by rememberSaveable { mutableStateOf("") }
    MonoLabel("Where", Modifier.padding(top = 18.dp, bottom = 6.dp))
    DetailField(form.location, actions.onLocation, "Add a place", "event-location")
    MonoLabel("Video call", Modifier.padding(top = 14.dp, bottom = 6.dp))
    DetailField(form.videoLink, actions.onVideoLink, "Add a video link", "event-video")
    MonoLabel("Guests", Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { DetailField(guest, { guest = it }, "Email or name", "event-guest-input", ImeAction.Done) { actions.onAddGuest(guest); guest = "" } }
        Text(
            "Add", style = Paper.type.label, color = colors.accent,
            modifier = Modifier.clickable { actions.onAddGuest(guest); guest = "" }.padding(horizontal = 12.dp, vertical = 9.dp).testTag("event-guest-add"),
        )
    }
    if (form.guests.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            form.guests.forEach { g -> Chip("$g  ✕", true, "guest-$g") { actions.onRemoveGuest(g) } }
        }
    }
    Group("Show as") {
        Chip("Busy", form.busy, "show-busy") { actions.onBusy(true) }
        Chip("Free", !form.busy, "show-free") { actions.onBusy(false) }
    }
}

@Composable
private fun DetailField(value: String, onChange: (String) -> Unit, placeholder: String, tag: String, ime: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    val colors = Paper.colors
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = Paper.type.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(imeAction = ime),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.card).border(1.dp, colors.rule, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = Paper.type.body, color = colors.mutedSoft); inner() } },
    )
}

private fun durationLabel(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    MonoLabel(label, Modifier.padding(top = 18.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, dot: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val colors = Paper.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (selected) colors.ink else colors.card).border(1.dp, if (selected) colors.ink else colors.rule, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { Box(Modifier.padding(end = 7.dp).size(8.dp).clip(CircleShape).background(it)) }
        Text(label, style = Paper.type.label, color = if (selected) colors.card else colors.ink)
    }
}

/** The "Pick date…" / "Custom…" link that opens a full picker. */
@Composable
private fun Link(label: String, tag: String, onClick: () -> Unit) {
    Text(label, style = Paper.type.label, color = Paper.colors.accent, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 9.dp).testTag(tag))
}

/** How the thing will look, so there are no surprises after Save. */
@Composable
private fun Preview(state: CreateUiState, today: LocalDate) {
    val colors = Paper.colors
    val form = state.form
    val project = state.projects.firstOrNull { it.id == form.projectId }
    val whenText = buildList {
        add(if (form.date == today) "Today" else if (form.date == today.plusDays(1)) "Tomorrow" else DAY_FORMAT.format(form.date))
        if (form.type == CreateType.EVENT && form.allDay) add("All day")
        else if (form.time != null) add(TIME_FORMAT.format(form.time) + if (form.type == CreateType.EVENT) " – " + TIME_FORMAT.format(form.time.plusMinutes(form.durationMinutes.toLong())) else "")
        form.repeat?.let { add("↻ ${repeatLabel(it)}") }
        form.alerts.minOrNull()?.let { add("🔔 ${alertLabel(it)}" + (if (form.alerts.size > 1) " +${form.alerts.size - 1}" else "") + if (form.alertStyle == AlertStyle.ALARM) " · alarm" else "") }
    }.joinToString(" · ")
    MonoLabel("Preview", Modifier.padding(top = 22.dp, bottom = 8.dp))
    PaperCard(Modifier.fillMaxWidth().testTag("create-preview")) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            if (form.type == CreateType.TASK) {
                Box(Modifier.padding(top = 2.dp, end = 12.dp).size(18.dp).clip(CircleShape).border(1.5.dp, colors.rule, CircleShape))
            } else {
                Box(Modifier.padding(end = 12.dp).size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(2.dp)).background(project?.let { parseHexColor(it.colorHex) } ?: colors.ink))
            }
            Column(Modifier.weight(1f)) {
                Text(form.title.ifBlank { "Untitled" }, style = Paper.type.body, color = if (form.title.isBlank()) colors.mutedSoft else colors.ink, modifier = Modifier.testTag("preview-title"))
                Text(whenText, style = Paper.type.monoMeta, color = colors.muted, modifier = Modifier.padding(top = 3.dp).testTag("preview-when"))
                project?.let { Text("● ${it.name}", style = Paper.type.monoMeta, color = parseHexColor(it.colorHex), modifier = Modifier.padding(top = 3.dp)) }
            }
        }
    }
}

// ---- the full pickers (screens 16-18)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSheet(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
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
private fun TimeSheet(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Text("OK", style = Paper.type.button, color = Paper.colors.accent, modifier = Modifier.clickable { onPick(LocalTime.of(state.hour, state.minute)) }.padding(12.dp).testTag("time-ok")) },
        dismissButton = { Text("Cancel", style = Paper.type.button, color = Paper.colors.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp)) },
        text = { TimePicker(state) },
        containerColor = Paper.colors.card,
    )
}

private enum class LenUnit(val label: String, val minutes: Int) { MINUTES("Minutes", 1), HOURS("Hours", 60), DAYS("Days", 1440) }
private enum class Every(val label: String, val word: String) { DAYS("Days", "Day"), WEEKS("Weeks", "Week"), MONTHS("Months", "Month") }

/** Screen 17: an alert of any length, as "N minutes / hours / days before". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsSheet(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("15") }
    var unit by rememberSaveable { mutableStateOf(LenUnit.MINUTES) }
    val minutes = amount.toIntOrNull()?.times(unit.minutes)?.takeIf { it in 1..(60 * 24 * 60) }
    SheetFrame(onDismiss, "Alert", "alerts-sheet") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(amount, { amount = it.filter(Char::isDigit).take(4) }, "alert-amount")
            Text("before", style = Paper.type.title, color = Paper.colors.muted, modifier = Modifier.padding(start = 12.dp))
        }
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LenUnit.entries.forEach { u -> Chip(u.label, unit == u, "alert-unit-${u.name.lowercase()}") { unit = u } }
        }
        SheetButton("Add alert", minutes != null, "alert-add") { minutes?.let(onAdd) }
    }
}

/** Screen 18: "Every N days / weeks / months". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSheet(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("2") }
    var every by rememberSaveable { mutableStateOf(Every.WEEKS) }
    val n = amount.toIntOrNull()?.takeIf { it in 1..99 }
    SheetFrame(onDismiss, "Repeat", "repeat-sheet") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every", style = Paper.type.title, color = Paper.colors.muted, modifier = Modifier.padding(end = 12.dp))
            NumberField(amount, { amount = it.filter(Char::isDigit).take(2) }, "repeat-amount")
        }
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Every.entries.forEach { e -> Chip(e.label, every == e, "repeat-unit-${e.name.lowercase()}") { every = e } }
        }
        SheetButton("Repeat", n != null, "repeat-set") { n?.let { onSet(if (it == 1) "Every ${every.word}" else "Every $it ${every.word}s") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(onDismiss: () -> Unit, label: String, tag: String, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paper.colors.paper,
        shape = RoundedCornerShape(topStart = PaperRadius.sheet, topEnd = PaperRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag(tag)) {
            MonoLabel(label, Modifier.padding(bottom = 12.dp), color = Paper.colors.accent)
            content()
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, tag: String) {
    val colors = Paper.colors
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = Paper.type.display.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.width(90.dp).clip(RoundedCornerShape(8.dp)).background(colors.card).border(1.dp, colors.rule, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp).testTag(tag),
    )
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val colors = Paper.colors
    Box(
        Modifier.padding(top = 20.dp).fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(PaperRadius.card)).background(colors.ink)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Paper.type.button, color = colors.card) }
}
