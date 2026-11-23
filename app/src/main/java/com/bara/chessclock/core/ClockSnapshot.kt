package com.bara.chessclock.core

/**
 * Persisted snapshot: the minimum needed to rebuild a game after the process (or the device) was
 * killed. Deliberately *not* a serialisation of the whole app: settings drafts, dialog state and
 * derived display values are all reconstructible.
 *
 * [bootId] is the reason this type exists as more than the state. A monotonic reading such as
 * `SystemClock.elapsedRealtime()` is only comparable inside one boot, so we store which boot the
 * anchor belongs to and refuse to subtract across boots.
 */
data class ClockSnapshot(
    val state: ClockState,
    val bootId: String,
    /** Wall-clock time, only ever shown to the user ("saved at ..."), never used for arithmetic. */
    val savedAtWallClockMs: Long,
    /** Which screen the user was on, so a restore lands where they left off. */
    val screen: Screen = Screen.SETTINGS,
)

/** Result of trying to bring a snapshot back to life. */
enum class RestoreNotice {
    NONE,

    /** The device rebooted while a clock was running: we paused it because the elapsed time is unknowable. */
    REBOOT_PAUSED,

    /** Stored text existed but could not be trusted (truncated / unknown version / corrupt). */
    SNAPSHOT_DISCARDED,
}

data class RestoreResult(
    val state: ClockState,
    val notice: RestoreNotice,
    val hadSnapshot: Boolean,
)

/**
 * Versioned line-based encoding.
 *
 * Why not JSON: `org.json` is only a stub on the JVM, and the pure core is unit tested without
 * Robolectric. A strict `key=value` format keeps the parser dependency-free, diffable in git and
 * trivially fuzzable, while still being validated field by field.
 */
object ClockSnapshotCodec {

    const val VERSION = 1
    private const val HEADER = "chessclock-snapshot"
    private const val SNAPSHOT_MAX_REMAINING_MS =
        TimeControl.MAX_BASE_MS + TimeControl.MAX_INCREMENT_MS * 100 // ≈ 2.7 h, absurd by design

    fun encode(snapshot: ClockSnapshot): String {
        val s = snapshot.state
        return buildString {
            appendLine("format=$HEADER")
            appendLine("version=$VERSION")
            appendLine("boot=${escape(snapshot.bootId)}")
            appendLine("savedAtWallClockMs=${snapshot.savedAtWallClockMs}")
            appendLine("phase=${s.phase.name}")
            appendLine("active=${s.active?.name ?: "-"}")
            appendLine("flagged=${s.flagged?.name ?: "-"}")
            appendLine("moves=${s.moves}")
            appendLine("baseMs=${s.control.baseMs}")
            appendLine("incrementMs=${s.control.incrementMs}")
            appendLine("oneRemainingMs=${s.oneRemainingMs}")
            appendLine("twoRemainingMs=${s.twoRemainingMs}")
            appendLine("anchorElapsedMs=${s.anchorElapsedMs}")
            appendLine("screen=${snapshot.screen.name}")
        }
    }

    /** Strict decode: any missing, unknown or out-of-range field yields null (caller discards). */
    fun decode(text: String?): ClockSnapshot? {
        if (text.isNullOrBlank()) return null
        val fields = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return null
            val key = trimmed.substring(0, idx)
            val value = trimmed.substring(idx + 1)
            if (fields.put(key, value) != null) return null // duplicated key => corrupt
        }
        if (fields["format"] != HEADER) return null
        if (fields["version"]?.toIntOrNull() != VERSION) return null

        val required = listOf(
            "boot", "savedAtWallClockMs", "phase", "active", "flagged", "moves",
            "baseMs", "incrementMs", "oneRemainingMs", "twoRemainingMs", "anchorElapsedMs", "screen",
        )
        if (!fields.keys.containsAll(required)) return null

        return try {
            val phase = Phase.valueOf(fields.getValue("phase"))
            val active = fields.getValue("active").toSideOrNull()
            val flagged = fields.getValue("flagged").toSideOrNull()
            val moves = fields.getValue("moves").toInt()
            val baseMs = fields.getValue("baseMs").toLong()
            val incrementMs = fields.getValue("incrementMs").toLong()
            val oneRemainingMs = fields.getValue("oneRemainingMs").toLong()
            val twoRemainingMs = fields.getValue("twoRemainingMs").toLong()
            val anchorElapsedMs = fields.getValue("anchorElapsedMs").toLong()
            val savedAt = fields.getValue("savedAtWallClockMs").toLong()

            val control = TimeControl.createOrNull(baseMs, incrementMs) ?: return null
            if (moves < 0) return null
            if (oneRemainingMs < 0 || twoRemainingMs < 0) return null
            // Sanity bound only (a Fischer clock legitimately grows past the base time, so this is
            // "absurd" rather than "bigger than base"): guards against corrupted huge numbers.
            if (oneRemainingMs > SNAPSHOT_MAX_REMAINING_MS) return null
            if (twoRemainingMs > SNAPSHOT_MAX_REMAINING_MS) return null
            if (anchorElapsedMs < 0) return null
            // Cross-field consistency: RUNNING needs an active side, PAUSED keeps one for resume,
            // FINISHED needs a flagged loser and no active side.
            when (phase) {
                Phase.RUNNING -> if (active == null) return null
                Phase.PAUSED -> if (active == null) return null
                Phase.FINISHED -> if (flagged == null || active != null) return null
                Phase.READY -> Unit
            }
            ClockSnapshot(
                state = ClockState(
                    control = control,
                    phase = phase,
                    active = active,
                    moves = moves,
                    oneRemainingMs = oneRemainingMs,
                    twoRemainingMs = twoRemainingMs,
                    anchorElapsedMs = anchorElapsedMs,
                    flagged = flagged,
                ),
                bootId = unescape(fields.getValue("boot")),
                savedAtWallClockMs = savedAt,
                screen = when (fields.getValue("screen")) {
                    Screen.BOARD.name -> Screen.BOARD
                    Screen.SETTINGS.name -> Screen.SETTINGS
                    else -> return null
                },
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun String.toSideOrNull(): Side? = when (this) {
        "-" -> null
        Side.ONE.name -> Side.ONE
        Side.TWO.name -> Side.TWO
        else -> throw IllegalArgumentException("unknown side: $this")
    }

    // bootId is "B:1234" or "unknown"; escaping keeps the line format unambiguous for any value.
    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\\\", "\\")
}

/**
 * Brings a snapshot back. The only two hard rules:
 *  - same boot  -> a running clock is fast-forwarded **once** by the elapsed time (which may flag a timeout);
 *  - other boot -> never subtract two monotonic readings from different boots. A running clock comes
 *    back paused with [RestoreNotice.REBOOT_PAUSED] so the players restart it deliberately.
 */
object ClockRestore {

    fun restore(
        snapshot: ClockSnapshot?,
        nowElapsedMs: Long,
        currentBootId: String,
        defaultControl: TimeControl,
    ): RestoreResult {
        if (snapshot == null) {
            return RestoreResult(
                state = ClockState.fresh(defaultControl, nowElapsedMs),
                notice = RestoreNotice.NONE,
                hadSnapshot = false,
            )
        }
        val state = snapshot.state
        if (snapshot.bootId != currentBootId) {
            return if (state.phase == Phase.RUNNING) {
                val paused = state.copy(phase = Phase.PAUSED, anchorElapsedMs = nowElapsedMs)
                RestoreResult(paused, RestoreNotice.REBOOT_PAUSED, hadSnapshot = true)
            } else {
                RestoreResult(state.copy(anchorElapsedMs = nowElapsedMs), RestoreNotice.NONE, hadSnapshot = true)
            }
        }
        // Same boot. Guard against a snapshot whose anchor is in the future (clock anomaly, or a
        // corrupted record): treat it as "no time passed" instead of inventing time.
        val safe = if (state.anchorElapsedMs > nowElapsedMs) {
            state.copy(anchorElapsedMs = nowElapsedMs)
        } else {
            state
        }
        val restored = if (safe.phase == Phase.RUNNING) safe.tick(nowElapsedMs) else safe
        return RestoreResult(restored, RestoreNotice.NONE, hadSnapshot = true)
    }
}
