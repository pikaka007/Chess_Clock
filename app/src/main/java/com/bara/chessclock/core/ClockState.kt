package com.bara.chessclock.core

/**
 * Immutable game state. It carries no Android types and never reads the system clock: every
 * transition takes `nowElapsedMs`, a value from a monotonic source (`SystemClock.elapsedRealtime()`
 * on Android). That is what makes rules A/B/E verifiable in plain JVM tests.
 *
 * Money-time invariants enforced here:
 *  1. At most one side loses time (only [active] while [Phase.RUNNING]).
 *  2. [settle] is the only place that subtracts elapsed time, so a pause/tap/restore sequence can
 *     never double-charge: it deducts, clamps at zero, re-anchors, and flags a timeout in one step.
 *  3. A timeout always wins over the increment: the flag check happens before `incrementMs` is added.
 */
data class ClockState(
    val control: TimeControl,
    val phase: Phase,
    val active: Side?,
    val moves: Int,
    val oneRemainingMs: Long,
    val twoRemainingMs: Long,
    /** Monotonic reading at which [oneRemainingMs]/[twoRemainingMs] were last true. */
    val anchorElapsedMs: Long,
    val flagged: Side?,
) {

    fun remainingMs(side: Side): Long = when (side) {
        Side.ONE -> oneRemainingMs
        Side.TWO -> twoRemainingMs
    }

    fun withRemaining(side: Side, value: Long): ClockState = when (side) {
        Side.ONE -> copy(oneRemainingMs = value.coerceAtLeast(0))
        Side.TWO -> copy(twoRemainingMs = value.coerceAtLeast(0))
    }

    /** Milliseconds elapsed since the anchor, never negative (see E/clock-anomaly case). */
    fun elapsedSinceAnchor(nowElapsedMs: Long): Long =
        (nowElapsedMs - anchorElapsedMs).coerceAtLeast(0)

    /**
     * Read-only projection for rendering: subtracts the running side's elapsed time without
     * mutating anything. Rendering this every frame is cheap and writes nothing to disk.
     */
    fun remainingAt(side: Side, nowElapsedMs: Long): Long {
        if (phase != Phase.RUNNING || active != side) return remainingMs(side)
        return (remainingMs(side) - elapsedSinceAnchor(nowElapsedMs)).coerceAtLeast(0)
    }

    /**
     * Deducts elapsed time for the running side exactly once, and flags a timeout if it reached
     * zero. Called by every transition that stops time (tap/pause/tick/restore).
     */
    private fun settle(nowElapsedMs: Long): ClockState {
        if (phase != Phase.RUNNING) return this
        val running = active ?: return this
        val left = (remainingMs(running) - elapsedSinceAnchor(nowElapsedMs)).coerceAtLeast(0)
        val settled = withRemaining(running, left).copy(anchorElapsedMs = nowElapsedMs)
        return if (left == 0L) {
            settled.copy(phase = Phase.FINISHED, flagged = running, active = null)
        } else {
            settled
        }
    }

    /**
     * Display/refresh path: settles and flags a timeout when the running side runs out while the
     * user is just looking at the screen. No increment, no move count.
     */
    fun tick(nowElapsedMs: Long): ClockState = settle(nowElapsedMs)

    /**
     * Whole-clock-face tap.
     *
     * - READY  -> starts the tapped side. Move count stays 0, no increment (first start is not a move).
     * - RUNNING and the tapped side is already running -> ignored (repeated taps must not add time or moves).
     * - RUNNING and the other side is tapped -> valid handover: settle the mover, timeout check,
     *   `increment` to the mover, `moves + 1`, tap side starts.
     * - PAUSED/FINISHED -> ignored, so a hand resting on the phone cannot restart a frozen clock.
     */
    fun tap(side: Side, nowElapsedMs: Long): ClockState {
        when (phase) {
            Phase.READY -> return copy(phase = Phase.RUNNING, active = side, anchorElapsedMs = nowElapsedMs)
            Phase.RUNNING -> {
                if (active == side) return this // repeated tap while running: no-op
                val settled = settle(nowElapsedMs)
                if (settled.phase == Phase.FINISHED) return settled // timeout wins, no increment/move
                val mover = settled.active ?: return settled
                val boosted = settled.withRemaining(mover, settled.remainingMs(mover) + control.incrementMs)
                return boosted.copy(
                    phase = Phase.RUNNING,
                    active = side,
                    moves = settled.moves + 1,
                    anchorElapsedMs = nowElapsedMs,
                )
            }
            Phase.PAUSED, Phase.FINISHED -> return this
        }
    }

    /** Freezes both clocks. A clock that hits zero at the moment of pausing is still a timeout. */
    fun pause(nowElapsedMs: Long): ClockState {
        val settled = settle(nowElapsedMs)
        if (settled.phase != Phase.RUNNING) return settled
        return settled.copy(phase = Phase.PAUSED, anchorElapsedMs = nowElapsedMs)
    }

    /** Resumes only the side that was running before the pause. */
    fun resume(nowElapsedMs: Long): ClockState {
        if (phase != Phase.PAUSED || active == null) return this
        return copy(phase = Phase.RUNNING, anchorElapsedMs = nowElapsedMs)
    }

    /** Back to the start of the same time control. */
    fun reset(nowElapsedMs: Long): ClockState = fresh(control, nowElapsedMs)

    /** A brand new game with a possibly different time control. */
    fun newGame(control: TimeControl, nowElapsedMs: Long): ClockState = fresh(control, nowElapsedMs)

    /** True when something was actually played or the clock was touched, used for confirm prompts. */
    val hasProgress: Boolean
        get() = moves > 0 || phase == Phase.FINISHED || remainingMs(Side.ONE) != control.baseMs ||
            remainingMs(Side.TWO) != control.baseMs

    companion object {
        fun fresh(control: TimeControl, nowElapsedMs: Long = 0): ClockState = ClockState(
            control = control,
            phase = Phase.READY,
            active = null,
            moves = 0,
            oneRemainingMs = control.baseMs,
            twoRemainingMs = control.baseMs,
            anchorElapsedMs = nowElapsedMs,
            flagged = null,
        )
    }
}
