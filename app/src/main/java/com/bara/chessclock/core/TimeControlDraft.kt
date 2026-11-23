package com.bara.chessclock.core

/**
 * Text-field state of the settings screen, kept pure so the validation rules are unit tested
 * instead of being duplicated in a Composable.
 *
 * Rules (docs/acceptance.md §C):
 *  - base duration must be filled with digits: minutes 0..60, seconds 0..59
 *  - total base duration 5 s .. 60 min
 *  - increment 0..60 s; an empty increment field means "no increment" (a normal way to play)
 */
data class TimeControlDraft(
    val minutesText: String,
    val secondsText: String,
    val incrementText: String,
) {

    sealed interface Validation {
        data class Valid(val control: TimeControl) : Validation
        data class Invalid(val message: String) : Validation
    }

    fun validate(): Validation {
        val minutes = minutesText.trim()
        val seconds = secondsText.trim()
        val increment = incrementText.trim()

        if (minutes.isEmpty() && seconds.isEmpty()) {
            return Validation.Invalid("请填写基础时长（例如 5 分 0 秒）")
        }
        val minutesValue = minutes.ifEmpty { "0" }.toIntOrNull()
            ?: return Validation.Invalid("分钟只能填数字")
        val secondsValue = seconds.ifEmpty { "0" }.toIntOrNull()
            ?: return Validation.Invalid("秒只能填数字")
        if (minutesValue < 0 || secondsValue < 0) {
            return Validation.Invalid("时长不能为负数")
        }
        if (minutesValue > MAX_MINUTES) {
            return Validation.Invalid("基础时长最多 $MAX_MINUTES 分钟")
        }
        if (secondsValue > 59) {
            return Validation.Invalid("秒数应在 0 到 59 之间")
        }
        val baseMs = minutesValue * 60_000L + secondsValue * 1_000L
        if (baseMs < TimeControl.MIN_BASE_MS) {
            return Validation.Invalid("基础时长至少 ${TimeControl.MIN_BASE_MS / 1000} 秒")
        }

        val incrementValue = increment.ifEmpty { "0" }.toIntOrNull()
            ?: return Validation.Invalid("每步加秒只能填数字")
        if (incrementValue < 0) {
            return Validation.Invalid("每步加秒不能为负数")
        }
        if (incrementValue > MAX_INCREMENT_SECONDS) {
            return Validation.Invalid("每步加秒最多 $MAX_INCREMENT_SECONDS 秒")
        }

        val control = TimeControl.createOrNull(baseMs, incrementValue * 1000L)
            ?: return Validation.Invalid("时长超出允许范围")
        return Validation.Valid(control)
    }

    /** Human summary shown above the start button, e.g. `双方各 5:00，无加秒`. */
    fun summary(): String {
        return when (val result = validate()) {
            is Validation.Invalid -> result.message
            is Validation.Valid -> describe(result.control)
        }
    }

    companion object {
        const val MAX_MINUTES = 60
        const val MAX_INCREMENT_SECONDS = 60

        fun from(control: TimeControl): TimeControlDraft = TimeControlDraft(
            minutesText = (control.baseMs / 60_000).toString(),
            secondsText = (control.baseMs % 60_000 / 1000).toString(),
            incrementText = (control.incrementMs / 1000).toString(),
        )

        val EMPTY = TimeControlDraft("", "", "0")

        fun describe(control: TimeControl): String {
            val base = ClockFormat.mmss(control.baseMs)
            return if (control.incrementMs == 0L) {
                "双方各 $base，不加秒"
            } else {
                "双方各 $base，每走完一步 +${control.incrementMs / 1000} 秒"
            }
        }
    }
}
