package com.bara.chessclock.core

/** Lifecycle of one game. */
enum class Phase {
    /** Times are full, nobody has tapped yet. */
    READY,

    /** Exactly one side is losing time. */
    RUNNING,

    /** Frozen; [ClockState.active] remembers who was running so "resume" restores only that side. */
    PAUSED,

    /** A side hit zero. Times are frozen at zero for the flagged side. */
    FINISHED,
}
