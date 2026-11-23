package com.bara.chessclock.core

/** "Just granted" notification used by the UI to flash the increment next to the mover's clock. */
data class IncrementGrant(val side: Side, val amountMs: Long, val atElapsedMs: Long)

/**
 * Orchestrates one session: clock state, screen, the two confirmation flows ("reset" and
 * "start a new game") and snapshot persistence. No Android types, no coroutines — the UI only
 * reads [state] and calls these methods, which is what makes the whole rule set unit testable.
 *
 * Persistence policy (docs/acceptance.md §D): write on every state transition, on [onStop], and at
 * most once per [persistIntervalMs] while running. Rendering frames never write.
 */
class ClockController(
    private val store: ClockStore,
    private val time: TimeSource,
    private val bootId: BootIdSource,
    private val defaultControl: TimeControl = TimeControl.PRESET_1_MIN,
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
    private val persistIntervalMs: Long = 10_000L,
) {

    var state: ClockState = ClockState.fresh(defaultControl)
        private set

    var screen: Screen = Screen.SETTINGS
        private set

    var notice: RestoreNotice = RestoreNotice.NONE
        private set

    var resetDialogOpen: Boolean = false
        private set

    /** Non-null while the "start a new game?" dialog is waiting for an answer. */
    var pendingNewGameControl: TimeControl? = null
        private set

    /** How many snapshots were written, used by tests to prove "not per frame". */
    var persistCount: Int = 0
        private set

    /** Last storage error (read or write), kept for diagnosis instead of pretending success. */
    var lastStorageError: RuntimeException? = null
        private set

    /** Last Fischer increment actually granted, so the UI can show where the 2 s went. */
    var lastIncrement: IncrementGrant? = null
        private set

    private var resumeAfterResetDialog = false
    private var resumeAfterSettings = false
    private var resumeAfterNewGamePrompt = false
    private var lastPersistElapsedMs = Long.MIN_VALUE

    init {
        restoreFromDisk()
    }

    // ---------------------------------------------------------------- restore

    private fun restoreFromDisk() {
        val stored = try {
            store.read()
        } catch (e: RuntimeException) {
            lastStorageError = e
            null
        }
        val snapshot = ClockSnapshotCodec.decode(stored)
        val now = time.elapsedMs()
        val result = ClockRestore.restore(snapshot, now, bootId.current(), defaultControl)
        state = result.state
        notice = when {
            stored != null && snapshot == null -> RestoreNotice.SNAPSHOT_DISCARDED
            else -> result.notice
        }
        screen = when {
            !result.hadSnapshot -> Screen.SETTINGS
            snapshot != null -> snapshot.screen
            else -> Screen.SETTINGS
        }
        resetDialogOpen = false
        pendingNewGameControl = null
    }

    fun acknowledgeNotice() {
        notice = RestoreNotice.NONE
    }

    // ---------------------------------------------------------------- clock actions

    /** Whole-face tap. Ignored while a dialog owns the screen or while settings is open. */
    fun tap(side: Side) {
        if (screen != Screen.BOARD) return
        if (resetDialogOpen || pendingNewGameControl != null) return
        val now = time.elapsedMs()
        val previous = state
        val next = previous.tap(side, now)
        if (next == previous) return // repeated tap / frozen clock: nothing changes, nothing written
        state = next
        val grantedTo = previous.active
        // A valid handover is the only transition that increases the move count; a timeout does not.
        if (grantedTo != null && next.moves > previous.moves && next.phase != Phase.FINISHED &&
            previous.control.incrementMs > 0
        ) {
            lastIncrement = IncrementGrant(grantedTo, previous.control.incrementMs, now)
        }
        persist(now)
    }

    /** The single pause/resume control. READY and FINISHED are no-ops. */
    fun togglePause() {
        if (resetDialogOpen || pendingNewGameControl != null) return
        val now = time.elapsedMs()
        val next = when (state.phase) {
            Phase.RUNNING -> state.pause(now)
            Phase.PAUSED -> state.resume(now)
            Phase.READY, Phase.FINISHED -> return
        }
        if (next == state) return
        state = next
        persist(now)
    }

    /** Display refresh and timeout detection. Returns the monotonic reading it used. */
    fun onFrame(): Long {
        val now = time.elapsedMs()
        val ticked = state.tick(now)
        if (ticked.phase != state.phase || ticked.flagged != state.flagged) {
            state = ticked
            persist(now)
        } else if (state.phase == Phase.RUNNING && now - lastPersistElapsedMs >= persistIntervalMs) {
            // Throttled safety net so a kill without onStop loses at most ~10 s of bookkeeping.
            persist(now)
        }
        return now
    }

    // ---------------------------------------------------------------- reset flow

    /** Opening the dialog freezes the clock, so reading the text cannot cost time. */
    fun openResetDialog() {
        if (resetDialogOpen || pendingNewGameControl != null) return
        val now = time.elapsedMs()
        resumeAfterResetDialog = state.phase == Phase.RUNNING
        if (resumeAfterResetDialog) state = state.pause(now)
        resetDialogOpen = true
        persist(now)
    }

    /** Cancel restores exactly the pre-dialog running state (same side running). */
    fun cancelResetDialog() {
        if (!resetDialogOpen) return
        resetDialogOpen = false
        val now = time.elapsedMs()
        if (resumeAfterResetDialog && state.phase == Phase.PAUSED) state = state.resume(now)
        resumeAfterResetDialog = false
        persist(now)
    }

    fun confirmResetDialog() {
        if (!resetDialogOpen) return
        resetDialogOpen = false
        resumeAfterResetDialog = false
        val now = time.elapsedMs()
        state = state.reset(now)
        persist(now)
    }

    /** Text for the confirmation dialog, stating the concrete consequence. */
    fun resetConfirmationMessage(): String {
        val base = ClockFormat.mmss(state.control.baseMs)
        return "双方恢复至 $base，步数归零。"
    }

    // ---------------------------------------------------------------- navigation / new game

    /** Leaving the board for settings pauses a running clock (otherwise setting up costs time). */
    fun openSettings() {
        val now = time.elapsedMs()
        resumeAfterSettings = state.phase == Phase.RUNNING
        if (resumeAfterSettings) state = state.pause(now)
        screen = Screen.SETTINGS
        persist(now)
    }

    fun backToBoard() {
        if (screen == Screen.BOARD) return
        screen = Screen.BOARD
        val now = time.elapsedMs()
        if (resumeAfterSettings && state.phase == Phase.PAUSED) state = state.resume(now)
        resumeAfterSettings = false
        persist(now)
    }

    /**
     * Starting a game with an unfinished one on the board always needs an explicit answer:
     * changing the time control mid-game therefore never silently wipes the game.
     */
    fun requestNewGame(control: TimeControl) {
        if (resetDialogOpen) return
        val now = time.elapsedMs()
        if (!state.hasProgress) {
            startNewGame(control, now)
            return
        }
        resumeAfterNewGamePrompt = state.phase == Phase.RUNNING
        if (resumeAfterNewGamePrompt) state = state.pause(now)
        pendingNewGameControl = control
        persist(now)
    }

    fun confirmNewGame() {
        val control = pendingNewGameControl ?: return
        pendingNewGameControl = null
        resumeAfterNewGamePrompt = false
        startNewGame(control, time.elapsedMs())
    }

    fun cancelNewGame() {
        if (pendingNewGameControl == null) return
        pendingNewGameControl = null
        val now = time.elapsedMs()
        if (resumeAfterNewGamePrompt && state.phase == Phase.PAUSED) state = state.resume(now)
        resumeAfterNewGamePrompt = false
        persist(now)
    }

    fun newGameConfirmationMessage(): String {
        val control = pendingNewGameControl ?: state.control
        val moves = state.moves
        return "将开始新对局（${TimeControlDraft.describe(control)}），当前对局与 $moves 步记录会被清空。"
    }

    private fun startNewGame(control: TimeControl, now: Long) {
        state = state.newGame(control, now)
        screen = Screen.BOARD
        resumeAfterSettings = false
        persist(now)
    }

    // ---------------------------------------------------------------- persistence

    /** Called from the Activity's ON_STOP: the last chance to survive process death. */
    fun onStop() {
        persist(time.elapsedMs())
    }

    private fun persist(nowElapsedMs: Long) {
        lastPersistElapsedMs = nowElapsedMs
        // The snapshot is settled to the write instant, so the recorded remaining/anchor pair is
        // always self-consistent: restore then only has to add the time elapsed *after* the save.
        val snapshot = ClockSnapshot(
            state = state.tick(nowElapsedMs),
            bootId = bootId.current(),
            savedAtWallClockMs = wallClockMs(),
            screen = screen,
        )
        val encoded = ClockSnapshotCodec.encode(snapshot)
        try {
            store.write(encoded)
            persistCount += 1
        } catch (e: RuntimeException) {
            // A failed write only degrades recovery after a kill; the in-memory clock stays
            // authoritative. Deliberately swallowed here but recorded in [lastStorageError]
            // rather than being reported as success.
            lastStorageError = e
        }
    }
}
