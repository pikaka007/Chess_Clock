package com.bara.chessclock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.bara.chessclock.core.Phase
import com.bara.chessclock.core.Screen
import com.bara.chessclock.ui.BoardScreen
import com.bara.chessclock.ui.ChessClockTheme
import com.bara.chessclock.ui.ClockDialogs
import com.bara.chessclock.ui.ClockViewModel
import com.bara.chessclock.ui.SettingsScreen
import kotlinx.coroutines.delay

/**
 * Single-activity host. It owns only platform concerns that cannot live in the pure core:
 *  - the ON_STOP snapshot hook (process death),
 *  - `FLAG_KEEP_SCREEN_ON` while a side is on the move, released when leaving that state,
 *  - the display ticker (rendering only; all arithmetic is in the core),
 *  - back handling: from the board, back goes to settings instead of killing a running game.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ClockViewModel by viewModels { ClockViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessClockTheme {
                val ui by viewModel.ui.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            // Returning from background/screen-off: settle once from the anchor.
                            Lifecycle.Event.ON_START -> viewModel.onStart()
                            // Last chance to persist; also the moment we stop keeping the screen on.
                            Lifecycle.Event.ON_STOP -> viewModel.onStop()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val keepScreenOn = ui.screen == Screen.BOARD && ui.phase == Phase.RUNNING
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                // Display refresh only. 80 ms keeps the tenths readable without spinning the CPU;
                // the clock is correct without this loop (it is derived from the monotonic anchor).
                LaunchedEffect(ui.isRunning) {
                    if (!ui.isRunning) return@LaunchedEffect
                    while (true) {
                        viewModel.onTick()
                        delay(80)
                    }
                }

                BackHandler(enabled = ui.screen == Screen.BOARD) {
                    // Never lose time to a stray back gesture: the board goes to settings, which
                    // pauses first. Leaving the app from settings still saves on ON_STOP.
                    viewModel.openSettings()
                }

                when (ui.screen) {
                    Screen.SETTINGS -> SettingsScreen(
                        ui = ui,
                        hasGameInProgress = ui.clock.hasProgress,
                        onDraftChange = viewModel::updateDraft,
                        onPresetSelected = viewModel::selectPreset,
                        onStart = viewModel::startGame,
                        onBackToBoard = viewModel::backToBoard,
                    )

                    Screen.BOARD -> BoardScreen(
                        ui = ui,
                        onTapSide = viewModel::tap,
                        onTogglePause = viewModel::togglePause,
                        onOpenReset = viewModel::openResetDialog,
                        onOpenSettings = viewModel::openSettings,
                        onAcknowledgeNotice = viewModel::acknowledgeNotice,
                    )
                }

                // App-level so the new-game prompt is visible from either screen.
                ClockDialogs(
                    ui = ui,
                    resetConfirmationMessage = viewModel.resetConfirmationMessage(),
                    newGameConfirmationMessage = viewModel.newGameConfirmationMessage(),
                    onConfirmReset = viewModel::confirmResetDialog,
                    onCancelReset = viewModel::cancelResetDialog,
                    onConfirmNewGame = viewModel::confirmNewGame,
                    onCancelNewGame = viewModel::cancelNewGame,
                )
            }
        }
    }
}
