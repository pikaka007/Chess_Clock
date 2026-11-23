package com.bara.chessclock.ui

import com.bara.chessclock.core.ClockState
import com.bara.chessclock.core.IncrementGrant
import com.bara.chessclock.core.RestoreNotice
import com.bara.chessclock.core.Screen
import com.bara.chessclock.core.Side
import com.bara.chessclock.core.TimeControl
import com.bara.chessclock.core.TimeControlDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The "+2 秒" flash is time limited, so its window is pinned here instead of on a device where
 * wall-clock timing would make the assertion flaky.
 */
class ClockUiStateTest {

    private fun state(grant: IncrementGrant?, now: Long): ClockUiState = ClockUiState(
        screen = Screen.BOARD,
        clock = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0),
        nowElapsedMs = now,
        notice = RestoreNotice.NONE,
        resetDialogOpen = false,
        pendingNewGame = null,
        draft = TimeControlDraft.EMPTY,
        settingsError = null,
        lastIncrement = grant,
    )

    @Test
    fun `no grant means no flash`() {
        assertNull(state(null, 10_000).incrementFlashMs(Side.ONE))
        assertNull(state(null, 10_000).incrementFlashMs(Side.TWO))
    }

    @Test
    fun `the flash belongs to the side that received the increment`() {
        val grant = IncrementGrant(side = Side.TWO, amountMs = 2_000, atElapsedMs = 10_000)
        val ui = state(grant, 10_200)
        assertEquals(2_000L, ui.incrementFlashMs(Side.TWO))
        assertNull(ui.incrementFlashMs(Side.ONE))
    }

    @Test
    fun `the flash disappears after the window`() {
        val grant = IncrementGrant(side = Side.ONE, amountMs = 2_000, atElapsedMs = 10_000)
        assertEquals(2_000L, state(grant, 10_000).incrementFlashMs(Side.ONE))
        assertEquals(2_000L, state(grant, 12_500).incrementFlashMs(Side.ONE))
        assertNull(state(grant, 12_501).incrementFlashMs(Side.ONE))
    }

    @Test
    fun `a reading older than the grant never flashes`() {
        val grant = IncrementGrant(side = Side.ONE, amountMs = 2_000, atElapsedMs = 10_000)
        assertNull(state(grant, 9_900).incrementFlashMs(Side.ONE))
    }
}
