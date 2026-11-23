package com.bara.chessclock.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The two confirmations, hoisted to the app level.
 *
 * They are deliberately *not* part of [BoardScreen]: the "start a new game?" prompt is triggered
 * from the settings screen, and composing it inside the board made the prompt invisible there
 * (the clock paused and nothing happened). Found by
 * `ClockFlowTest.changingTheTimeControlMidGame_asksBeforeDiscardingTheGame`.
 */
@Composable
fun ClockDialogs(
    ui: ClockUiState,
    resetConfirmationMessage: String,
    newGameConfirmationMessage: String,
    onConfirmReset: () -> Unit,
    onCancelReset: () -> Unit,
    onConfirmNewGame: () -> Unit,
    onCancelNewGame: () -> Unit,
) {
    if (ui.resetDialogOpen) {
        AlertDialog(
            onDismissRequest = onCancelReset,
            modifier = Modifier.testTag("resetDialog"),
            title = { Text("重置对局？") },
            text = {
                Text(
                    "$resetConfirmationMessage\n\n" +
                        "计时已暂停：确认或取消都不会损失时间。取消后由原计时方继续。",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmReset, modifier = Modifier.testTag("resetConfirm")) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelReset, modifier = Modifier.testTag("resetCancel")) {
                    Text("取消")
                }
            },
        )
    }

    if (ui.pendingNewGame != null) {
        AlertDialog(
            onDismissRequest = onCancelNewGame,
            modifier = Modifier.testTag("newGameDialog"),
            title = { Text("开始新对局？") },
            text = { Text("$newGameConfirmationMessage\n\n当前计时已暂停。") },
            confirmButton = {
                TextButton(onClick = onConfirmNewGame, modifier = Modifier.testTag("newGameConfirm")) {
                    Text("开始新对局")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelNewGame, modifier = Modifier.testTag("newGameCancel")) {
                    Text("取消")
                }
            },
        )
    }
}
