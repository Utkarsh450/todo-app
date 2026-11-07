package com.utkarsh.todo.core

import androidx.compose.ui.graphics.Color
import com.utkarsh.todo.core.common.dayRange
import com.utkarsh.todo.core.common.formatDayCaption
import com.utkarsh.todo.core.common.formatTime
import com.utkarsh.todo.core.designsystem.theme.parseHexColor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCommonTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T20:00:00Z"), ist) // Sun 8 Nov 01:30 IST

    @Test
    fun `dayRange follows the local day not UTC`() {
        val today = clock.dayRange()
        assertEquals(Instant.parse("2026-11-07T18:30:00Z"), today.start)
        assertEquals(Instant.parse("2026-11-08T18:30:00Z"), today.end)
        assertEquals(today.end, clock.dayRange(1).start)
    }

    @Test
    fun `range is half open`() {
        val today = clock.dayRange()
        assertTrue(today.start in today)
        assertFalse(today.end in today)
    }

    @Test
    fun `formats time and caption in the given zone`() {
        val instant = Instant.parse("2026-11-07T03:30:00Z") // 09:00 IST, Saturday
        assertEquals("9:00 AM", instant.formatTime(ist))
        assertEquals("SAT 7 NOV · W45", instant.formatDayCaption(ist))
    }

    @Test
    fun `hex colours parse with a safe fallback`() {
        assertEquals(Color(0xFF935EDF), parseHexColor("#935EDF"))
        assertEquals(Color.Red, parseHexColor("purple", Color.Red))
        assertEquals(Color.Red, parseHexColor("#FFF", Color.Red))
    }
}
