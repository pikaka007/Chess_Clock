package com.bara.chessclock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules that live in the production state machine, driven by an injected monotonic clock.
 * Expected values come from docs/acceptance.md §A and §B and were written before this code.
 */
class ClockStateTest {

    // ------------------------------------------------------------------ A. zero increment

    @Test
    fun `A - sixty second game, handover after 3s, 2s later`() {
        var s = ClockState.fresh(TimeControl.PRESET_1_MIN, nowElapsedMs = 0)
        assertEquals(Phase.READY, s.phase)
        assertEquals(60_000, s.remainingMs(Side.ONE))
        assertEquals(60_000, s.remainingMs(Side.TWO))

        s = s.tap(Side.TWO, 0) // first tap starts player two, move count stays 0
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(Side.TWO, s.active)
        assertEquals(0, s.moves)

        s = s.tap(Side.ONE, 3_000) // handover after 3 s
        assertEquals(Side.ONE, s.active)
        assertEquals(1, s.moves)
        assertEquals(57_000, s.remainingMs(Side.TWO))
        assertEquals(60_000, s.remainingMs(Side.ONE))

        assertEquals(57_000, s.remainingAt(Side.TWO, 5_000))
        assertEquals(58_000, s.remainingAt(Side.ONE, 5_000))

        // pause for 5 s: nothing moves, and the frozen value is what the UI shows
        val paused = s.pause(5_000)
        assertEquals(Phase.PAUSED, paused.phase)
        assertEquals(Side.ONE, paused.active)
        assertEquals(57_000, paused.remainingAt(Side.TWO, 10_000))
        assertEquals(58_000, paused.remainingAt(Side.ONE, 10_000))
        assertEquals(1, paused.moves)

        // resume, then 1 s: only player two advanced (58 000 - 1 000)
        val resumed = paused.resume(10_000)
        assertEquals(Phase.RUNNING, resumed.phase)
        assertEquals(57_000, resumed.remainingAt(Side.ONE, 11_000))
        assertEquals(57_000, resumed.remainingAt(Side.TWO, 11_000))
        assertEquals(1, resumed.moves)
    }

    @Test
    fun `A - tapping the frozen clock while paused changes nothing`() {
        val running = ClockState.fresh(TimeControl.PRESET_1_MIN, 0).tap(Side.TWO, 0)
        val paused = running.pause(1_000)
        assertSame(paused, paused.tap(Side.ONE, 2_000))
        assertSame(paused, paused.tap(Side.TWO, 2_000))
    }

    // ------------------------------------------------------------------ B. Fischer 3+2

    @Test
    fun `B - 3+2 handover after 3s gives the mover 2s and increments the move count`() {
        var s = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0)
        s = s.tap(Side.TWO, 0)
        s = s.tap(Side.ONE, 3_000)

        assertEquals(179_000, s.remainingMs(Side.TWO)) // 180 000 - 3 000 + 2 000
        assertEquals(180_000, s.remainingMs(Side.ONE))
        assertEquals(1, s.moves)
        assertEquals(Side.ONE, s.active)
    }

    @Test
    fun `B - repeated taps on the running side add neither time nor moves`() {
        var s = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0).tap(Side.TWO, 0).tap(Side.ONE, 3_000)
        repeat(3) { s = s.tap(Side.ONE, 3_500L + it * 100L) }
        assertEquals(179_000, s.remainingMs(Side.TWO))
        assertEquals(1, s.moves)
        // player one has been running since the handover at 3 000 ms, so 800 ms are gone
        assertEquals(179_200, s.remainingAt(Side.ONE, 3_800))
    }

    @Test
    fun `B - pause and resume never grant the increment`() {
        var s = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0).tap(Side.TWO, 0).tap(Side.ONE, 3_000)
        s = s.pause(5_000) // player one used 2 s
        assertEquals(178_000, s.remainingMs(Side.ONE))
        assertEquals(179_000, s.remainingMs(Side.TWO))
        s = s.resume(9_000) // 4 s of reading
        assertEquals(178_000, s.remainingAt(Side.ONE, 9_000))
        assertEquals(179_000, s.remainingAt(Side.TWO, 9_000))
        assertEquals(1, s.moves)
    }

    @Test
    fun `B - handover at exactly zero is a timeout, no increment and no move`() {
        val control = TimeControl(5_000, 2_000)
        val s = ClockState.fresh(control, 0)
            .tap(Side.TWO, 0)
            .tap(Side.ONE, 5_000) // player two is exactly at 0

        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(Side.TWO, s.flagged)
        assertEquals(0, s.remainingMs(Side.TWO))
        assertEquals(0, s.moves) // the handover never counted as a move
        assertEquals(null, s.active) // player one was never started
    }

    @Test
    fun `B - after a timeout taps do nothing`() {
        val finished = ClockState.fresh(TimeControl(5_000, 2_000), 0)
            .tap(Side.TWO, 0)
            .tap(Side.ONE, 5_000)
        assertSame(finished, finished.tap(Side.TWO, 6_000))
        assertSame(finished, finished.pause(6_000))
        assertSame(finished, finished.resume(6_000))
    }

    // ------------------------------------------------------------------ timeout detection

    @Test
    fun `tick flags a timeout when the running side runs out with no input at all`() {
        var s = ClockState.fresh(TimeControl.PRESET_1_MIN, 0).tap(Side.TWO, 0)
        s = s.tick(59_900)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(100, s.remainingAt(Side.TWO, 59_900))
        s = s.tick(60_000)
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(Side.TWO, s.flagged)
        assertEquals(0, s.remainingMs(Side.TWO))
    }

    @Test
    fun `pause at exactly zero still counts as a timeout`() {
        val s = ClockState.fresh(TimeControl(5_000, 0), 0).tap(Side.TWO, 0).pause(5_000)
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(Side.TWO, s.flagged)
    }

    // ------------------------------------------------------------------ E. clock anomalies

    @Test
    fun `E - a single late refresh deducts the whole elapsed span exactly once`() {
        var s = ClockState.fresh(TimeControl.PRESET_5_MIN, 0).tap(Side.ONE, 0)
        s = s.tick(30_000) // no refresh for 30 s
        assertEquals(270_000, s.remainingMs(Side.ONE))
        s = s.tick(30_000) // idempotent: same reading twice must not double charge
        assertEquals(270_000, s.remainingMs(Side.ONE))
        s = s.tick(31_000)
        assertEquals(269_000, s.remainingMs(Side.ONE))
    }

    @Test
    fun `E - a reading before the anchor never invents negative time`() {
        val s = ClockState.fresh(TimeControl.PRESET_5_MIN, 10_000).tap(Side.ONE, 10_000)
        assertEquals(0, s.elapsedSinceAnchor(9_000))
        assertEquals(300_000, s.remainingAt(Side.ONE, 9_000))
        assertEquals(Phase.RUNNING, s.tick(9_000).phase)
    }

    @Test
    fun `E - the engine only depends on the injected monotonic readings`() {
        // Two states with identical anchors produce identical readings regardless of wall-clock time,
        // because no wall-clock value can reach the engine at all (see ClockState's signature).
        val a = ClockState.fresh(TimeControl.PRESET_1_MIN, 5_000).tap(Side.TWO, 5_000)
        assertEquals(55_000, a.remainingAt(Side.TWO, 10_000))
        assertEquals(55_000, a.remainingAt(Side.TWO, 10_000))
    }

    // ------------------------------------------------------------------ misc invariants

    @Test
    fun `only one side can ever be charged`() {
        var s = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0).tap(Side.TWO, 0)
        val before = s.remainingMs(Side.ONE)
        s = s.tick(10_000)
        assertEquals(before, s.remainingMs(Side.ONE)) // untouched side
        assertEquals(170_000, s.remainingMs(Side.TWO))
    }

    @Test
    fun `reset returns to full times with zero moves`() {
        var s = ClockState.fresh(TimeControl.PRESET_5_MIN, 0).tap(Side.TWO, 0).tap(Side.ONE, 1_000)
        s = s.reset(2_000)
        assertEquals(Phase.READY, s.phase)
        assertEquals(0, s.moves)
        assertEquals(300_000, s.remainingMs(Side.ONE))
        assertEquals(300_000, s.remainingMs(Side.TWO))
        assertEquals(null, s.active)
        assertEquals(null, s.flagged)
        assertTrue(!s.hasProgress)
    }
}
