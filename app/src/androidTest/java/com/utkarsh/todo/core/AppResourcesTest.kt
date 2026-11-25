package com.utkarsh.todo.core

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.utkarsh.todo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled fonts and the launcher icon are really in the installed app. */
class AppResourcesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun every_bundled_font_loads() {
        listOf(R.font.satoshi, R.font.satoshi_italic).forEach { id -> assertNotNull("font $id", ResourcesCompat.getFont(context, id)) }
    }

    @Test
    fun the_font_licences_ship_with_the_app() {
        val text = context.assets.open("licenses/Satoshi-FFL.txt").bufferedReader().use { it.readText() }
        assertTrue(text.contains("Indian Type Foundry"))
        assertTrue(text.contains("Mobile or Desktop Applications"))
    }

    @Test
    fun the_launcher_icon_is_an_adaptive_icon_with_a_themed_layer() {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull(icon.foreground)
        assertNotNull(icon.background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) assertNotNull(icon.monochrome)
        assertEquals("Paper Todo", context.applicationInfo.loadLabel(context.packageManager).toString())
    }
}
