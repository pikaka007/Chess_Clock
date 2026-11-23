package com.bara.chessclock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bara.chessclock.core.ClockController
import com.bara.chessclock.core.ClockState
import com.bara.chessclock.core.IncrementGrant
import com.bara.chessclock.core.Phase
import com.bara.chessclock.core.RestoreNotice
import com.bara.chessclock.core.Screen
import com.bara.chessclock.core.Side
import com.bara.chessclock.core.TimeControl
import com.bara.chessclock.core.TimeControlDraft
import com.bara.chessclock.data.AndroidBootIdSource
import com.bara.chessclock.data.ElapsedRealtimeSource
import com.bara.chessclock.data.PreferencesClockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the Compose layer needs. No clock rule lives here. */
data class ClockUiState(
    val screen: Screen,
    val clock: ClockState,
    val nowElapsedMs: Long,
    val notice: RestoreNotice,
    val resetDialogOpen: Boolean,
    val pendingNewGame: TimeControl?,
    val draft: TimeControlDraft,
    val settingsError: String?,
    /** Recent Fischer increment, shown for a moment next to the clock that received it. */
    val lastIncrement: IncrementGrant?,
) {
    val phase: Phase get() = clock.phase
    val isRunning: Boolean get() = clock.phase == Phase.RUNNING

    /** How long the "+2 秒" flash stays visible after a handover. */
    fun incrementFlashMs(side: Side): Long? {
        val grant = lastIncrement ?: return null
        if (grant.side != side) return null
        if (nowElapsedMs - grant.atElapsedMs < 0) return null
        if (nowElapsedMs - grant.atElapsedMs > INCREMENT_FLASH_MS) return null
        return grant.amountMs
    }

    companion object {
        const val INCREMENT_FLASH_MS = 2_500L
    }
}

/**
 * Thin Android shell around [ClockController]: it owns the lifecycle (ticker, ON_STOP snapshot) and
 * the settings draft, and exposes immutable UI state.
 *
 * Surviving configuration changes is handled by this ViewModel; surviving process death is handled
 * by the snapshot the controller writes. The ViewModel deliberately holds no other truth, so a
 * recreated process and a recreated Activity take the same code path.
 */
class ClockViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = ClockController(
        store = PreferencesClockStore(application),
        time = ElapsedRealtimeSource(),
        bootId = AndroidBootIdSource(application),
    )

    private var draft: TimeControlDraft = TimeControlDraft.from(controller.state.control)
    private var settingsError: String? = null

    private val _ui = MutableStateFlow(buildUi(controller.onFrame()))
    val ui: StateFlow<ClockUiState> = _ui.asStateFlow()

    private fun buildUi(nowElapsedMs: Long): ClockUiState = ClockUiState(
        screen = controller.screen,
        clock = controller.state,
        nowElapsedMs = nowElapsedMs,
        notice = controller.notice,
        resetDialogOpen = controller.resetDialogOpen,
        pendingNewGame = controller.pendingNewGameControl,
        draft = draft,
        settingsError = settingsError,
        lastIncrement = controller.lastIncrement,
    )

    /** One display refresh; also the only way a timeout is noticed without a tap. */
    fun onTick() {
        _ui.value = buildUi(controller.onFrame())
    }

    /** Called on ON_START: refreshes after the app was in the background or the screen was off. */
    fun onStart() {
        _ui.value = buildUi(controller.onFrame())
    }

    /** Called from ON_STOP: last chance to persist before the process may be killed. */
    fun onStop() {
        controller.onStop()
    }

    fun tap(side: Side) = mutate { controller.tap(side) }

    fun togglePause() = mutate { controller.togglePause() }

    fun openResetDialog() = mutate { controller.openResetDialog() }

    fun cancelResetDialog() = mutate { controller.cancelResetDialog() }

    fun confirmResetDialog() = mutate { controller.confirmResetDialog() }

    fun openSettings() = mutate {
        draft = TimeControlDraft.from(controller.state.control)
        settingsError = null
        controller.openSettings()
    }

    fun backToBoard() = mutate {
        settingsError = null
        controller.backToBoard()
    }

    fun updateDraft(next: TimeControlDraft) = mutate {
        draft = next
        settingsError = null
    }

    fun selectPreset(control: TimeControl) = mutate {
        draft = TimeControlDraft.from(control)
        settingsError = null
    }

    /** Validates the draft, then starts (or asks to confirm starting) a new game. */
    fun startGame() = mutate {
        when (val result = draft.validate()) {
            is TimeControlDraft.Validation.Invalid -> settingsError = result.message
            is TimeControlDraft.Validation.Valid -> {
                settingsError = null
                controller.requestNewGame(result.control)
            }
        }
    }

    fun confirmNewGame() = mutate { controller.confirmNewGame() }

    fun cancelNewGame() = mutate { controller.cancelNewGame() }

    fun acknowledgeNotice() = mutate { controller.acknowledgeNotice() }

    fun resetConfirmationMessage(): String = controller.resetConfirmationMessage()

    fun newGameConfirmationMessage(): String = controller.newGameConfirmationMessage()

    private inline fun mutate(block: () -> Unit) {
        block()
        _ui.value = buildUi(controller.onFrame())
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ClockViewModel(application) as T
    }
}
