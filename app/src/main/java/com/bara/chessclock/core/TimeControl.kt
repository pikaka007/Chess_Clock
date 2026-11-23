package com.bara.chessclock.core

/**
 * Time control: base time per side plus a Fischer increment granted per completed move.
 *
 * The increment is added to the side that *finished* a move (i.e. the side being stopped),
 * which is the rule the UI, the demo and the tests all use.
 */
data class TimeControl(
    val baseMs: Long,
    val incrementMs: Long,
) {
    init {
        require(baseMs in MIN_BASE_MS..MAX_BASE_MS) { "baseMs out of range: $baseMs" }
        require(incrementMs in MIN_INCREMENT_MS..MAX_INCREMENT_MS) { "incrementMs out of range: $incrementMs" }
    }

    val label: String
        get() = buildString {
            append(ClockFormat.mmss(baseMs))
            if (incrementMs > 0) append(" +").append(incrementMs / 1000).append("s")
        }

    companion object {
        /** Lower bound keeps a game meaningfully playable; 5 s is the shortest control we accept. */
        const val MIN_BASE_MS: Long = 5_000
        const val MAX_BASE_MS: Long = 60 * 60_000L
        const val MIN_INCREMENT_MS: Long = 0
        const val MAX_INCREMENT_MS: Long = 60_000

        /** AppForge's "1 min" preset. Zero increment by design. */
        val PRESET_1_MIN = TimeControl(60_000, 0)

        /** The course-required Fischer preset: 3 minutes + 2 s per completed move. */
        val PRESET_3_PLUS_2 = TimeControl(180_000, 2_000)

        val PRESET_5_MIN = TimeControl(300_000, 0)
        val PRESET_10_MIN = TimeControl(600_000, 0)

        val PRESETS: List<TimeControl> = listOf(PRESET_1_MIN, PRESET_3_PLUS_2, PRESET_5_MIN, PRESET_10_MIN)

        /** @return null when either value is out of range, instead of throwing. */
        fun createOrNull(baseMs: Long, incrementMs: Long): TimeControl? =
            if (baseMs in MIN_BASE_MS..MAX_BASE_MS && incrementMs in MIN_INCREMENT_MS..MAX_INCREMENT_MS) {
                TimeControl(baseMs, incrementMs)
            } else {
                null
            }
    }
}
