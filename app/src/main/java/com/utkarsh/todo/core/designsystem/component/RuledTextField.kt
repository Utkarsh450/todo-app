package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utkarsh.todo.core.designsystem.theme.Paper

private val RuleSpacing = 32.dp

/** Large serif input drawn on ruled-paper lines (25sp Newsreader on 32dp rules). */
@Composable
fun RuledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    /** Applied to the editable field itself (e.g. test tags), unlike [modifier] which wraps the ruled box. */
    inputModifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val textStyle: TextStyle = Paper.type.display.copy(fontSize = 25.sp, lineHeight = 32.sp, color = colors.ink)
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val step = RuleSpacing.toPx()
                var y = step
                while (y < size.height + step) {
                    drawLine(colors.rule, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    y += step
                }
            },
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            modifier = inputModifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        )
        if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.mutedSoft)
    }
}
