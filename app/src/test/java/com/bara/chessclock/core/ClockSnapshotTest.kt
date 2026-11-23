package com.bara.chessclock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSnapshotCodecTest {

    private fun sample(screen: Screen = Screen.BOARD): ClockSnapshot {
        val state = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 1_000)
            .tap(Side.TWO, 1_000)
            .tap(Side.ONE, 4_000)
        return ClockSnapshot(state, bootId = "boot:7", savedAtWallClockMs = 1_700_000_000_000L, screen = screen)
    }

    @Test
    fun `round trip keeps every field`() {
        val original = sample()
        val decoded = ClockSnapshotCodec.decode(ClockSnapshotCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(original.state, decoded!!.state)
        assertEquals("boot:7", decoded.bootId)
        assertEquals(1_700_000_000_000L, decoded.savedAtWallClockMs)
        assertEquals(Screen.BOARD, decoded.screen)
    }

    @Test
    fun `truncated text is rejected`() {
        val text = ClockSnapshotCodec.encode(sample())
        assertNull(ClockSnapshotCodec.decode(text.substring(0, text.length / 2)))
    }

    @Test
    fun `unknown version is rejected`() {
        val text = ClockSnapshotCodec.encode(sample()).replace("version=1", "version=2")
        assertNull(ClockSnapshotCodec.decode(text))
    }

    @Test
    fun `unknown format header is rejected`() {
        val text = ClockSnapshotCodec.encode(sample()).replace("format=chessclock-snapshot", "format=other")
        assertNull(ClockSnapshotCodec.decode(text))
    }

    @Test
    fun `duplicated key is rejected`() {
        val text = ClockSnapshotCodec.encode(sample()) + "moves=3\n"
        assertNull(ClockSnapshotCodec.decode(text))
    }

    @Test
    fun `negative and absurd values are rejected`() {
        assertNull(ClockSnapshotCodec.decode(ClockSnapshotCodec.encode(sample()).replace("moves=1", "moves=-5")))
        assertNull(
            ClockSnapshotCodec.decode(
                ClockSnapshotCodec.encode(sample()).replace("oneRemainingMs=180000", "oneRemainingMs=999999999999")
            )
        )
        assertNull(ClockSnapshotCodec.decode(ClockSnapshotCodec.encode(sample()).replace("baseMs=180000", "baseMs=1")))
    }

    @Test
    fun `running without an active side is rejected`() {
        val text = ClockSnapshotCodec.encode(sample()).replace("active=ONE", "active=-")
        assertNull(ClockSnapshotCodec.decode(text))
    }

    @Test
    fun `blank or garbage text yields null instead of throwing`() {
        assertNull(ClockSnapshotCodec.decode(null))
        assertNull(ClockSnapshotCodec.decode(""))
        assertNull(ClockSnapshotCodec.decode("   \n\n "))
        assertNull(ClockSnapshotCodec.decode("not a snapshot at all"))
        assertNull(ClockSnapshotCodec.decode("format=chessclock-snapshot\nversion=1\nmoves=abc\n"))
    }

    @Test
    fun `a long Fischer game may exceed the base time and still decode`() {
        var state = ClockState.fresh(TimeControl.PRESET_3_PLUS_2, 0).tap(Side.ONE, 0)
        var now = 0L
        repeat(40) {
            val target = if (state.active == Side.ONE) Side.TWO else Side.ONE
            now += 100
            state = state.tap(target, now)
        }
        assertTrue(state.remainingMs(Side.ONE) > 180_000)
        val decoded = ClockSnapshotCodec.decode(
            ClockSnapshotCodec.encode(ClockSnapshot(state, "boot:1", 0L, Screen.BOARD))
        )
        assertNotNull(decoded)
        assertEquals(state, decoded!!.state)
    }

    @Test
    fun `restore on the same boot fast-forwards a running clock exactly once`() {
        val snapshot = ClockSnapshot(
            state = ClockState.fresh(TimeControl.PRESET_5_MIN, 10_000).tap(Side.ONE, 10_000),
            bootId = "boot:3",
            savedAtWallClockMs = 0L,
            screen = Screen.BOARD,
        )
        val first = ClockRestore.restore(snapshot, nowElapsedMs = 40_000, currentBootId = "boot:3", defaultControl = TimeControl.PRESET_1_MIN)
        assertEquals(270_000, first.state.remainingMs(Side.ONE))
        assertEquals(RestoreNotice.NONE, first.notice)
        assertEquals(Phase.RUNNING, first.state.phase)
    }

    @Test
    fun `restore on the same boot after the clock expired flags the timeout`() {
        val snapshot = ClockSnapshot(
            state = ClockState.fresh(TimeControl.PRESET_1_MIN, 0).tap(Side.TWO, 0),
            bootId = "boot:3",
            savedAtWallClockMs = 0L,
            screen = Screen.BOARD,
        )
        val result = ClockRestore.restore(snapshot, nowElapsedMs = 61_000, currentBootId = "boot:3", defaultControl = TimeControl.PRESET_1_MIN)
        assertEquals(Phase.FINISHED, result.state.phase)
        assertEquals(Side.TWO, result.state.flagged)
    }

    @Test
    fun `restore after a reboot pauses a running clock and says so`() {
        val snapshot = ClockSnapshot(
            state = ClockState.fresh(TimeControl.PRESET_5_MIN, 1_000).tap(Side.TWO, 1_000),
            bootId = "boot:3",
            savedAtWallClockMs = 0L,
            screen = Screen.BOARD,
        )
        // nowElapsedMs is from a *different* boot and must not be compared with the anchor.
        val result = ClockRestore.restore(snapshot, nowElapsedMs = 5_000, currentBootId = "boot:4", defaultControl = TimeControl.PRESET_1_MIN)
        assertEquals(Phase.PAUSED, result.state.phase)
        assertEquals(Side.TWO, result.state.active)
        assertEquals(300_000, result.state.remainingMs(Side.TWO)) // nothing subtracted
        assertEquals(RestoreNotice.REBOOT_PAUSED, result.notice)
        assertTrue(result.hadSnapshot)
    }

    @Test
    fun `restore after a reboot leaves a paused game paused without a notice`() {
        val paused = ClockState.fresh(TimeControl.PRESET_5_MIN, 1_000).tap(Side.TWO, 1_000).pause(2_000)
        val snapshot = ClockSnapshot(paused, "boot:3", 0L, Screen.BOARD)
        val result = ClockRestore.restore(snapshot, 9_000, "boot:4", TimeControl.PRESET_1_MIN)
        assertEquals(Phase.PAUSED, result.state.phase)
        assertEquals(299_000, result.state.remainingMs(Side.TWO))
        assertEquals(RestoreNotice.NONE, result.notice)
    }

    @Test
    fun `restore without a snapshot starts fresh on the default control`() {
        val result = ClockRestore.restore(null, 123, "boot:1", TimeControl.PRESET_1_MIN)
        assertEquals(Phase.READY, result.state.phase)
        assertEquals(60_000, result.state.remainingMs(Side.ONE))
        assertEquals(RestoreNotice.NONE, result.notice)
        assertEquals(false, result.hadSnapshot)
    }

    @Test
    fun `a snapshot anchored in the future is clamped instead of granting time`() {
        val snapshot = ClockSnapshot(
            ClockState.fresh(TimeControl.PRESET_1_MIN, 100_000).tap(Side.ONE, 100_000),
            bootId = "boot:1",
            savedAtWallClockMs = 0L,
            screen = Screen.BOARD,
        )
        val result = ClockRestore.restore(snapshot, nowElapsedMs = 90_000, currentBootId = "boot:1", defaultControl = TimeControl.PRESET_1_MIN)
        assertEquals(60_000, result.state.remainingMs(Side.ONE)) // no bonus, no negative
    }
}
