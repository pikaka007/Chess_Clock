package com.bara.chessclock.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.bara.chessclock.core.BootIdSource
import com.bara.chessclock.core.ClockStore
import com.bara.chessclock.core.TimeSource
import java.util.UUID

/**
 * Snapshot storage. `commit()` is used instead of `apply()`: the snapshot is written on ON_STOP,
 * where the process may be killed immediately afterwards, and the payload is a few hundred bytes
 * written only on state transitions.
 */
class PreferencesClockStore(context: Context) : ClockStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_SNAPSHOT, null)

    override fun write(text: String) {
        val ok = prefs.edit().putString(KEY_SNAPSHOT, text).commit()
        if (!ok) throw IllegalStateException("SharedPreferences commit failed")
    }

    private companion object {
        const val PREFS_NAME = "chess_clock"
        const val KEY_SNAPSHOT = "snapshot_v1"
    }
}

/**
 * `Settings.Global.BOOT_COUNT` changes on every device boot, which is exactly the discriminator
 * needed before trusting a stored monotonic anchor.
 *
 * If it cannot be read (should not happen on API 26+), the fallback is a per-process random id:
 * conservative, because a snapshot from another process will then be treated as "other boot" and
 * the clock comes back paused rather than silently subtracting incomparable readings.
 */
class AndroidBootIdSource(private val context: Context) : BootIdSource {

    private val fallbackId: String by lazy { "process:${UUID.randomUUID()}" }

    override fun current(): String {
        val count = try {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
        } catch (e: RuntimeException) {
            -1
        }
        return if (count >= 0) "boot:$count" else fallbackId
    }

    /** Exposed for the verification notes: which build the id belongs to. */
    val buildFingerprint: String get() = Build.FINGERPRINT
}

/** Monotonic source: unaffected by date/time/timezone changes within one boot. */
class ElapsedRealtimeSource : TimeSource {
    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
}
