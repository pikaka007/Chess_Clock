package com.bara.chessclock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeControlDraftTest {

    private fun controlOf(draft: TimeControlDraft): TimeControl =
        (draft.validate() as TimeControlDraft.Validation.Valid).control

    private fun errorOf(draft: TimeControlDraft): String =
        (draft.validate() as TimeControlDraft.Validation.Invalid).message

    @Test
    fun `five minutes typed as 5 and 0 becomes 300 000 ms`() {
        val control = controlOf(TimeControlDraft("5", "0", "0"))
        assertEquals(300_000, control.baseMs)
        assertEquals(0, control.incrementMs)
    }

    @Test
    fun `minutes plus seconds are combined`() {
        assertEquals(150_000, controlOf(TimeControlDraft("2", "30", "0")).baseMs)
        // seconds-only input is accepted ("0 minutes" is implied)
        assertEquals(45_000, controlOf(TimeControlDraft("", "45", "")).baseMs)
    }

    @Test
    fun `increment is parsed in seconds and blank means zero`() {
        assertEquals(2_000, controlOf(TimeControlDraft("3", "0", "2")).incrementMs)
        assertEquals(0, controlOf(TimeControlDraft("3", "0", "   ")).incrementMs)
    }

    @Test
    fun `empty base duration is rejected with a readable message`() {
        assertTrue(errorOf(TimeControlDraft("", "", "0")).contains("请填写基础时长"))
    }

    @Test
    fun `non numeric input is rejected`() {
        assertTrue(errorOf(TimeControlDraft("abc", "0", "0")).contains("分钟只能填数字"))
        assertTrue(errorOf(TimeControlDraft("5", "x", "0")).contains("秒只能填数字"))
        assertTrue(errorOf(TimeControlDraft("5", "0", "two")).contains("每步加秒只能填数字"))
    }

    @Test
    fun `out of range values are rejected with the limit in the message`() {
        assertTrue(errorOf(TimeControlDraft("61", "0", "0")).contains("60"))
        assertTrue(errorOf(TimeControlDraft("5", "60", "0")).contains("59"))
        assertTrue(errorOf(TimeControlDraft("0", "4", "0")).contains("至少"))
        assertTrue(errorOf(TimeControlDraft("5", "0", "61")).contains("60"))
        assertTrue(errorOf(TimeControlDraft("5", "0", "-1")).contains("不能为负"))
        assertTrue(errorOf(TimeControlDraft("-1", "0", "0")).contains("不能为负"))
    }

    @Test
    fun `a control round trips through the draft`() {
        for (control in TimeControl.PRESETS) {
            assertEquals(control, controlOf(TimeControlDraft.from(control)))
        }
        assertEquals(TimeControl(150_000, 3_000), controlOf(TimeControlDraft.from(TimeControl(150_000, 3_000))))
    }

    @Test
    fun `the summary explains the increment in plain words`() {
        assertEquals("双方各 5:00，不加秒", TimeControlDraft.describe(TimeControl.PRESET_5_MIN))
        assertTrue(TimeControlDraft.describe(TimeControl.PRESET_3_PLUS_2).contains("每走完一步 +2 秒"))
        assertTrue(TimeControlDraft.from(TimeControl.PRESET_3_PLUS_2).summary().contains("3:00"))
    }
}
