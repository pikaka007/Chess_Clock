package com.bara.chessclock.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {

    @Test
    fun `whole minutes render as the AppForge scenarios expect`() {
        assertEquals("1:00", ClockFormat.display(60_000))
        assertEquals("5:00", ClockFormat.display(300_000))
        assertEquals("3:00", ClockFormat.display(180_000))
        assertEquals("0:00", ClockFormat.display(0))
        assertEquals("60:00", ClockFormat.display(3_600_000))
    }

    @Test
    fun `seconds are zero padded and truncate towards zero`() {
        assertEquals("0:19.9", ClockFormat.display(19_900))
        assertEquals("4:59", ClockFormat.display(299_900)) // >= 20 s left: whole seconds only
        assertEquals("1:01", ClockFormat.display(61_000))
        assertEquals("0:09.9", ClockFormat.display(9_900))
    }

    @Test
    fun `the last twenty seconds show tenths so the increment is visible`() {
        assertEquals("0:19.9", ClockFormat.display(19_900))
        assertEquals("0:20", ClockFormat.display(20_000))
        assertEquals("0:18.4", ClockFormat.display(18_400))
        assertEquals("0:00.4", ClockFormat.display(400))
    }

    @Test
    fun `spoken text always agrees with the visual digits`() {
        // Regression guard: rounding in the accessibility label once announced "2 分 59 秒" for a
        // face showing 2:58, found by dumping the real UI hierarchy on the emulator.
        var ms = 0L
        while (ms <= 600_000) {
            assertEquals("at ${ms}ms", ClockFormat.display(ms).substringBefore('.'), spokenAsDigits(ms))
            ms += 250
        }
    }

    private fun spokenAsDigits(ms: Long): String {
        val spoken = ClockFormat.spoken(ms)
        val minutes = Regex("(\\d+) 分").find(spoken)?.groupValues?.get(1)?.toInt() ?: 0
        val seconds = Regex("(\\d+) 秒").find(spoken)?.groupValues?.get(1)?.toInt() ?: 0
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    @Test
    fun `negative readings never render as negative time`() {
        assertEquals("0:00", ClockFormat.display(-1))
        assertEquals("0:00", ClockFormat.display(-5_000))
    }

    @Test
    fun `spoken form truncates exactly like the digits`() {
        assertEquals("5 分 0 秒", ClockFormat.spoken(300_000))
        assertEquals("59 秒", ClockFormat.spoken(59_800)) // 0:59 on screen, so 59 s is spoken
        assertEquals("59 秒", ClockFormat.spoken(59_400))
        assertEquals("0 秒", ClockFormat.spoken(0))
        assertEquals("2 分 58 秒", ClockFormat.spoken(178_600)) // matches display(178_600) == "2:58"
    }
}
