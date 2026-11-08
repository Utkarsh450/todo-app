package com.utkarsh.todo.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.PaperColors
import com.utkarsh.todo.core.designsystem.theme.PaperPattern
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.core.designsystem.theme.paperSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders the surface and looks at the actual pixels, so a pattern that silently stops drawing is caught. */
class PaperSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    /** Counts pixels that are not the plain paper colour. */
    private fun inkedPixels(pattern: PaperPattern): Pair<Int, Int> {
        compose.setContent { PaperTheme(pattern = pattern) { Box(Modifier.size(200.dp).paperSurface().testTag("surface")) } }
        val pixels = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        val paper = PaperColors().paper
        var other = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (pixels[x, y] != paper) other++
        return other to pixels.width * pixels.height
    }

    @Test
    fun plain_is_one_flat_colour() {
        assertEquals(0, inkedPixels(PaperPattern.Plain).first)
    }

    @Test
    fun dots_draw_a_sparse_grid_over_the_paper() {
        val (inked, total) = inkedPixels(PaperPattern.Dots)
        assertTrue("dots should be drawn, found $inked inked pixels", inked > 40)
        assertTrue("dots should stay sparse, but $inked of $total pixels are inked", inked < total / 10)
    }

    @Test
    fun lines_draw_thin_rules_across_the_paper() {
        val (inked, total) = inkedPixels(PaperPattern.Lines)
        assertTrue("lines should be drawn, found $inked inked pixels", inked > 500)
        assertTrue("lines should stay thin, but $inked of $total pixels are inked", inked < total / 8)
    }
}
