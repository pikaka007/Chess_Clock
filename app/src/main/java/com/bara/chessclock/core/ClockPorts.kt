package com.bara.chessclock.core

/** Where the user is. Kept in the core so a restored session can return to the same screen. */
enum class Screen { SETTINGS, BOARD }

/** Minimal key/value persistence seam, so the controller is testable without Android. */
interface ClockStore {
    fun read(): String?
    fun write(text: String)
}

/** Monotonic millisecond source. On Android this is `SystemClock.elapsedRealtime()`. */
fun interface TimeSource {
    fun elapsedMs(): Long
}

/** Identifies the current boot, so snapshots from a previous boot are never used for arithmetic. */
fun interface BootIdSource {
    fun current(): String
}
