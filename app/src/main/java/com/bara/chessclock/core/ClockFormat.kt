package com.bara.chessclock.core

/**
 * Formatting used by the clock faces. Pure functions so the display rules are unit tested.
 *
 * Rules:
 *  - 20 s or more -> `m:ss` (minutes are not zero padded, e.g. `5:00`, `60:00`)
 *  - under 20 s  -> `m:ss.t` with one decimal, so the last seconds are readable and the
 *    increment of a 3+2 game is visible in a recording
 *  - never prints a negative value
 */
object ClockFormat {

    /** Truncates towards zero; a clock reading `0:59` when 59.7 s remain is what players expect. */
    private fun clamp(ms: Long): Long = if (ms < 0) 0 else ms

    fun mmss(ms: Long): String {
        val totalSeconds = clamp(ms) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    fun display(ms: Long): String {
        val safe = clamp(ms)
        // A flagged clock reads a plain "0:00" (and never "0:00.0"); tenths only while time is left.
        if (safe == 0L || safe >= TENTHS_THRESHOLD_MS) return mmss(safe)
        val minutes = safe / 60_000
        val seconds = (safe % 60_000) / 1000
        val tenths = (safe % 1000) / 100
        return "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
    }

    fun totalSeconds(ms: Long): Long = clamp(ms) / 1000

    /**
     * Spoken form used in accessibility labels, e.g. `3 分 05 秒`.
     *
     * Truncates exactly like [display] so a screen reader never says a different second than the
     * digits on screen (rounding produced "2 分 59 秒" for a face showing 2:58).
     */
    fun spoken(ms: Long): String {
        val safe = clamp(ms)
        val minutes = safe / 60_000
        val seconds = safe % 60_000 / 1000
        val parts = mutableListOf<String>()
        if (minutes > 0) parts += "$minutes 分"
        parts += "$seconds 秒"
        return parts.joinToString(" ")
    }

    const val TENTHS_THRESHOLD_MS: Long = 20_000
}
