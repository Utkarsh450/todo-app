package com.utkarsh.todo.core.designsystem

import androidx.compose.ui.graphics.Color
import com.utkarsh.todo.core.designsystem.theme.Density
import com.utkarsh.todo.core.designsystem.theme.PaperColors
import com.utkarsh.todo.core.designsystem.theme.PaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesignTokensTest {
    private val colors = PaperColors()

    @Test
    fun `the heat ramp has five levels that start from the soft paper tone`() {
        assertEquals(5, colors.taskHeat.size)
        assertEquals(PaperPalette.Soft, colors.taskHeat.first())
    }

    @Test
    fun `the heat ramp never repeats a colour so every level is distinguishable`() {
        assertEquals(colors.taskHeat.size, colors.taskHeat.toSet().size)
    }

    @Test
    fun `accent matches the design vermilion and differs from ink`() {
        assertEquals(Color(0xFFC4532F), colors.accent)
        assertNotEquals(colors.ink, colors.accent)
    }

    @Test
    fun `compact density is tighter than cozy`() {
        assert(Density.Compact.rowPadding < Density.Cozy.rowPadding)
    }
}
