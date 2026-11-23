package com.bara.chessclock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test doubles: the controller talks only to these seams, so no Android/emulator is needed. */
private class MemoryStore(var text: String? = null) : ClockStore {
    var writes = 0
    var failWrites = false
    override fun read(): String? = text
    override fun write(text: String) {
        if (failWrites) throw IllegalStateException("disk full")
        this.text = text
        writes += 1
    }
}

private class FakeTime(var now: Long = 0) : TimeSource {
    override fun elapsedMs(): Long = now
    fun advance(ms: Long) {
        now += ms
    }
}

private class FakeBoot(var id: String = "boot:1") : BootIdSource {
    override fun current(): String = id
}

private fun controller(
    store: ClockStore = MemoryStore(),
    time: FakeTime = FakeTime(),
    boot: FakeBoot = FakeBoot(),
    default: TimeControl = TimeControl.PRESET_1_MIN,
): ClockController = ClockController(store, time, boot, default, wallClockMs = { 1_700_000_000_000L })

/**
 * C / D / E acceptance cases driven through [ClockController] — the same object the UI uses.
 * See docs/acceptance.md for the predicted values.
 */
class ClockControllerTest {

    // ------------------------------------------------------------------ C. settings & reset

    @Test
    fun `C - a fresh install starts on settings and starts the configured game on request`() {
        val c = controller()
        assertEquals(Screen.SETTINGS, c.screen)
        assertEquals(Phase.READY, c.state.phase)

        c.requestNewGame(TimeControl.PRESET_5_MIN)
        assertEquals(Screen.BOARD, c.screen)
        assertEquals(Phase.READY, c.state.phase)
        assertEquals(300_000, c.state.remainingMs(Side.ONE))
        assertEquals(300_000, c.state.remainingMs(Side.TWO))
        assertEquals(0, c.state.moves)
        assertEquals(0, c.state.control.incrementMs)
    }

    @Test
    fun `C - the 1 minute preset keeps a zero increment`() {
        val c = controller()
        c.requestNewGame(TimeControl.PRESET_1_MIN)
        assertEquals(60_000, c.state.remainingMs(Side.ONE))
        assertEquals(0, c.state.control.incrementMs)
    }

    @Test
    fun `C - confirmed reset returns a 5 minute game to 5-00, zero moves, waiting for a tap`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(4_000)
        c.tap(Side.ONE)
        time.advance(2_000)
        assertEquals(1, c.state.moves)

        c.openResetDialog()
        assertEquals(true, c.resetDialogOpen)
        assertEquals("双方恢复至 5:00，步数归零。", c.resetConfirmationMessage())
        c.confirmResetDialog()

        assertEquals(false, c.resetDialogOpen)
        assertEquals(Phase.READY, c.state.phase)
        assertEquals(0, c.state.moves)
        assertEquals(300_000, c.state.remainingMs(Side.ONE))
        assertEquals(300_000, c.state.remainingMs(Side.TWO))
        assertNull(c.state.active)
    }

    @Test
    fun `C - opening the reset dialog freezes the clock, cancelling restores the running side`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(3_000)
        c.tap(Side.ONE) // player two 297 000, player one running

        c.openResetDialog()
        assertEquals(Phase.PAUSED, c.state.phase)

        // five seconds of reading the dialog must not cost anything
        time.advance(5_000)
        assertEquals(297_000, c.state.remainingAt(Side.TWO, time.now))
        assertEquals(300_000, c.state.remainingAt(Side.ONE, time.now))

        c.cancelResetDialog()
        assertEquals(Phase.RUNNING, c.state.phase)
        assertEquals(Side.ONE, c.state.active) // same side as before the dialog
        assertEquals(1, c.state.moves)

        time.advance(1_000)
        assertEquals(299_000, c.state.remainingAt(Side.ONE, time.now))
        assertEquals(297_000, c.state.remainingAt(Side.TWO, time.now))
    }

    @Test
    fun `C - cancelling a reset that was opened while paused leaves the game paused`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(1_000)
        c.togglePause()
        assertEquals(Phase.PAUSED, c.state.phase)

        c.openResetDialog()
        c.cancelResetDialog()
        assertEquals(Phase.PAUSED, c.state.phase)
        assertEquals(299_000, c.state.remainingMs(Side.TWO))
    }

    @Test
    fun `C - tapping through the reset dialog is ignored`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_3_PLUS_2)
        c.tap(Side.TWO)
        time.advance(1_000)
        c.openResetDialog()
        val before = c.state
        c.tap(Side.ONE)
        assertEquals(before, c.state)
        assertEquals(0, c.state.moves)
    }

    @Test
    fun `C - changing the time control mid game needs confirmation and can be cancelled`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(2_000)
        c.tap(Side.ONE)

        c.requestNewGame(TimeControl.PRESET_3_PLUS_2)
        assertEquals(TimeControl.PRESET_3_PLUS_2, c.pendingNewGameControl)
        assertEquals(Phase.PAUSED, c.state.phase) // frozen while the answer is pending
        assertEquals(TimeControl.PRESET_5_MIN, c.state.control)
        assertTrue(c.newGameConfirmationMessage().contains("3:00"))

        c.cancelNewGame()
        assertNull(c.pendingNewGameControl)
        assertEquals(Phase.RUNNING, c.state.phase)
        assertEquals(TimeControl.PRESET_5_MIN, c.state.control)
        assertEquals(1, c.state.moves)

        c.requestNewGame(TimeControl.PRESET_3_PLUS_2)
        c.confirmNewGame()
        assertNull(c.pendingNewGameControl)
        assertEquals(Screen.BOARD, c.screen)
        assertEquals(Phase.READY, c.state.phase)
        assertEquals(TimeControl.PRESET_3_PLUS_2, c.state.control)
        assertEquals(0, c.state.moves)
        assertEquals(180_000, c.state.remainingMs(Side.ONE))
    }

    @Test
    fun `B - the increment grant is reported only for a valid handover`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_3_PLUS_2)

        c.tap(Side.TWO) // first start is not a move
        assertNull(c.lastIncrement)

        time.advance(3_000)
        c.tap(Side.ONE) // valid handover: player two receives the 2 s
        assertEquals(Side.TWO, c.lastIncrement?.side)
        assertEquals(2_000L, c.lastIncrement?.amountMs)
        assertEquals(3_000L, c.lastIncrement?.atElapsedMs)

        c.tap(Side.ONE) // repeated tap on the running side
        assertEquals(3_000L, c.lastIncrement?.atElapsedMs)
        c.togglePause()
        c.togglePause() // pause/resume must not grant anything
        assertEquals(3_000L, c.lastIncrement?.atElapsedMs)
        assertEquals(1, c.state.moves)
    }

    @Test
    fun `B - a timeout at the handover grants no increment and no move`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl(5_000, 2_000))
        c.tap(Side.TWO)
        time.advance(5_000)
        c.tap(Side.ONE)
        assertEquals(Phase.FINISHED, c.state.phase)
        assertEquals(Side.TWO, c.state.flagged)
        assertEquals(0, c.state.remainingMs(Side.TWO))
        assertEquals(0, c.state.moves)
        assertNull(c.lastIncrement)
    }

    // ------------------------------------------------------------------ D. recovery

    @Test
    fun `D - a snapshot taken while running is fast-forwarded exactly once after process death`() {
        val store = MemoryStore()
        val time = FakeTime(now = 1_000)
        val boot = FakeBoot("boot:9")
        val first = controller(store, time, boot)
        first.requestNewGame(TimeControl.PRESET_5_MIN)
        first.tap(Side.TWO) // starts at 1 000
        time.advance(5_000)
        first.onStop() // snapshot written at 6 000, player two still running

        // process death: a brand new controller over the same storage, 7 s later
        time.advance(7_000)
        val second = controller(store, time, boot)
        assertEquals(Phase.RUNNING, second.state.phase)
        assertEquals(Side.TWO, second.state.active)
        assertEquals(300_000 - 12_000 - 0, second.state.remainingAt(Side.TWO, time.now))
        assertEquals(0, second.state.moves)
        assertEquals(Screen.BOARD, second.screen)

        // restoring twice must not charge twice
        val third = controller(store, time, boot)
        assertEquals(second.state.remainingAt(Side.TWO, time.now), third.state.remainingAt(Side.TWO, time.now))
    }

    @Test
    fun `D - a snapshot taken while paused deducts nothing on restore`() {
        val store = MemoryStore()
        val time = FakeTime(now = 0)
        val boot = FakeBoot("boot:9")
        val first = controller(store, time, boot)
        first.requestNewGame(TimeControl.PRESET_5_MIN)
        first.tap(Side.TWO)
        time.advance(3_000)
        first.togglePause()
        first.onStop()

        time.advance(60_000) // one minute in the background
        val second = controller(store, time, boot)
        assertEquals(Phase.PAUSED, second.state.phase)
        assertEquals(297_000, second.state.remainingAt(Side.TWO, time.now))
        assertEquals(Side.TWO, second.state.active)
    }

    @Test
    fun `D - a snapshot taken before the first tap restores a ready board`() {
        val store = MemoryStore()
        val time = FakeTime()
        val boot = FakeBoot("boot:9")
        val first = controller(store, time, boot)
        first.requestNewGame(TimeControl.PRESET_3_PLUS_2)
        first.onStop()

        time.advance(10_000)
        val second = controller(store, time, boot)
        assertEquals(Phase.READY, second.state.phase)
        assertEquals(Screen.BOARD, second.screen) // lands where the user left off
        assertEquals(TimeControl.PRESET_3_PLUS_2, second.state.control)
        assertEquals(180_000, second.state.remainingMs(Side.ONE))
    }

    @Test
    fun `D - after a reboot the game comes back paused with a notice instead of guessing`() {
        val store = MemoryStore()
        val time = FakeTime(now = 10_000)
        val boot = FakeBoot("boot:9")
        val first = controller(store, time, boot)
        first.requestNewGame(TimeControl.PRESET_5_MIN)
        first.tap(Side.ONE)
        time.advance(4_000)
        first.onStop()

        boot.id = "boot:10" // device rebooted: elapsedRealtime restarted from ~0
        time.now = 2_000
        val second = controller(store, time, boot)
        assertEquals(Phase.PAUSED, second.state.phase)
        assertEquals(RestoreNotice.REBOOT_PAUSED, second.notice)
        assertEquals(296_000, second.state.remainingMs(Side.ONE)) // only the 4 s observed before the save
        assertEquals(Side.ONE, second.state.active)
    }

    @Test
    fun `D - a corrupt snapshot is discarded and the app opens on settings`() {
        val store = MemoryStore(text = "format=chessclock-snapshot\nversion=1\nphase=RUNNING\n")
        val c = controller(store)
        assertEquals(RestoreNotice.SNAPSHOT_DISCARDED, c.notice)
        assertEquals(Screen.SETTINGS, c.screen)
        assertEquals(Phase.READY, c.state.phase)
        assertEquals(60_000, c.state.remainingMs(Side.ONE))
        c.acknowledgeNotice()
        assertEquals(RestoreNotice.NONE, c.notice)
    }

    @Test
    fun `D - a failing store never breaks the clock in memory`() {
        val store = MemoryStore()
        store.failWrites = true
        val time = FakeTime()
        val c = controller(store, time)
        c.requestNewGame(TimeControl.PRESET_1_MIN)
        c.tap(Side.TWO)
        time.advance(1_000)
        assertEquals(Phase.RUNNING, c.state.phase) // still usable
        assertNotNull(c.lastStorageError) // and the failure is not hidden
        assertEquals(0, c.persistCount)
    }

    // ------------------------------------------------------------------ persistence policy

    @Test
    fun `frames do not write to disk`() {
        val store = MemoryStore()
        val time = FakeTime()
        val c = controller(store, time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        val writesAfterStart = store.writes
        c.tap(Side.TWO)
        val writesAfterTap = store.writes
        repeat(120) { // ~10 s of 80 ms frames
            time.advance(80)
            c.onFrame()
        }
        // 120 frames over 9.6 s: at most the 10 s throttle may fire, never one write per frame
        assertTrue(store.writes - writesAfterTap <= 1)
        assertTrue(store.writes >= writesAfterStart)
    }

    @Test
    fun `a throttled safety snapshot happens about every ten seconds while running`() {
        val store = MemoryStore()
        val time = FakeTime()
        val c = controller(store, time)
        c.requestNewGame(TimeControl.PRESET_10_MIN)
        c.tap(Side.TWO)
        val baseline = store.writes
        repeat(60) {
            time.advance(1_000)
            c.onFrame()
        }
        assertEquals(baseline + 6, store.writes) // one per 10 s
    }

    @Test
    fun `onStop always writes and a timeout is noticed without any tap`() {
        val store = MemoryStore()
        val time = FakeTime()
        val c = controller(store, time)
        c.requestNewGame(TimeControl.PRESET_1_MIN)
        c.tap(Side.ONE)
        time.advance(61_000)
        c.onFrame()
        assertEquals(Phase.FINISHED, c.state.phase)
        assertEquals(Side.ONE, c.state.flagged)
        val before = store.writes
        c.onStop()
        assertEquals(before + 1, store.writes)
        assertEquals(Phase.FINISHED, ClockSnapshotCodec.decode(store.text)!!.state.phase)
    }

    // ------------------------------------------------------------------ navigation

    @Test
    fun `E - going to settings pauses and coming back resumes the same side`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(2_000)

        c.openSettings()
        assertEquals(Screen.SETTINGS, c.screen)
        assertEquals(Phase.PAUSED, c.state.phase)
        time.advance(30_000) // time spent adjusting settings
        assertEquals(298_000, c.state.remainingAt(Side.TWO, time.now))

        c.tap(Side.ONE) // taps on the board are not possible from settings, and are ignored anyway
        assertEquals(Screen.SETTINGS, c.screen)
        assertEquals(0, c.state.moves)

        c.backToBoard()
        assertEquals(Screen.BOARD, c.screen)
        assertEquals(Phase.RUNNING, c.state.phase)
        assertEquals(Side.TWO, c.state.active)
        assertEquals(298_000, c.state.remainingAt(Side.TWO, time.now))
    }

    @Test
    fun `E - a 30 second gap with no refresh is charged once, not twice`() {
        val time = FakeTime()
        val c = controller(time = time)
        c.requestNewGame(TimeControl.PRESET_5_MIN)
        c.tap(Side.TWO)
        time.advance(30_000) // app was not rendering (screen off)
        c.onFrame() // the UI refreshes on return; no extra settle path exists
        assertEquals(270_000, c.state.remainingAt(Side.TWO, time.now))
        c.tap(Side.ONE)
        assertEquals(270_000, c.state.remainingMs(Side.TWO))
        assertEquals(1, c.state.moves)
        assertEquals(300_000, c.state.remainingMs(Side.ONE))
    }
}
