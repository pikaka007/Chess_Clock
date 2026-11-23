package com.bara.chessclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.chessclock.core.ClockFormat
import com.bara.chessclock.core.Phase
import com.bara.chessclock.core.RestoreNotice
import com.bara.chessclock.core.Side

/**
 * The board: two large clock faces, the opponent's rotated 180 degrees, with a narrow control band
 * in the middle. The band is deliberately small and low contrast compared to the digits: pause,
 * reset and settings must be reachable but must never compete with the time.
 */
@Composable
fun BoardScreen(
    ui: ClockUiState,
    onTapSide: (Side) -> Unit,
    onTogglePause: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onAcknowledgeNotice: () -> Unit,
) {
    val clock = ui.clock
    val running = clock.phase == Phase.RUNNING
    val paused = clock.phase == Phase.PAUSED
    val hasStarted = clock.phase != Phase.READY
    val increment = clock.control.incrementMs

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().testTag("screenBoard")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Opponent half, rotated so it reads correctly from the other side of the table.
            ClockFace(
                side = Side.TWO,
                remainingMs = clock.remainingAt(Side.TWO, ui.nowElapsedMs),
                isRunning = running && clock.active == Side.TWO,
                isFlagged = clock.flagged == Side.TWO,
                isPaused = paused,
                hasStarted = hasStarted,
                incrementMs = increment,
                flashIncrementMs = ui.incrementFlashMs(Side.TWO),
                onTap = { onTapSide(Side.TWO) },
                modifier = Modifier.weight(1f).rotate(180f),
            )

            ControlBand(
                ui = ui,
                onTogglePause = onTogglePause,
                onOpenReset = onOpenReset,
                onOpenSettings = onOpenSettings,
                onAcknowledgeNotice = onAcknowledgeNotice,
            )

            ClockFace(
                side = Side.ONE,
                remainingMs = clock.remainingAt(Side.ONE, ui.nowElapsedMs),
                isRunning = running && clock.active == Side.ONE,
                isFlagged = clock.flagged == Side.ONE,
                isPaused = paused,
                hasStarted = hasStarted,
                incrementMs = increment,
                flashIncrementMs = ui.incrementFlashMs(Side.ONE),
                onTap = { onTapSide(Side.ONE) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Confirmations are rendered by [ClockDialogs] at the app level, not here: the "start a new
    // game?" prompt is triggered from the settings screen, so it must not depend on the board
    // being composed. (Found by ClockFlowTest.changingTheTimeControlMidGame.)
}

@Composable
private fun ControlBand(
    ui: ClockUiState,
    onTogglePause: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onAcknowledgeNotice: () -> Unit,
) {
    val clock = ui.clock
    val running = clock.phase == Phase.RUNNING
    val paused = clock.phase == Phase.PAUSED
    val finished = clock.phase == Phase.FINISHED

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ui.notice != RestoreNotice.NONE) {
            NoticeRow(notice = ui.notice, onAcknowledge = onAcknowledgeNotice)
        }

        if (finished) {
            val loser = if (clock.flagged == Side.ONE) "玩家一" else "玩家二"
            Text(
                text = "$loser 超时 · 双方时钟已停止",
                color = ClockColors.Danger,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("timeoutBanner"),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("步数", color = ClockColors.CreamDim, fontSize = 13.sp)
            Text(
                text = clock.moves.toString(),
                color = ClockColors.Cream,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp).testTag("moves"),
            )
            if (clock.control.incrementMs > 0) {
                Text(
                    text = "换手 +${clock.control.incrementMs / 1000} 秒",
                    color = ClockColors.Amber,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp).testTag("incrementHint"),
                )
            }
        }

        Text(
            text = statusText(ui),
            color = if (finished) ClockColors.Danger else ClockColors.CreamDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("statusText"),
        )

        Row(
            modifier = Modifier.height(52.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalButton(
                onClick = onTogglePause,
                enabled = running || paused,
                modifier = Modifier.testTag("pauseResume"),
            ) {
                Text(if (paused) "继续" else "暂停")
            }
            TextButton(
                onClick = onOpenReset,
                modifier = Modifier.padding(start = 8.dp).testTag("reset"),
            ) {
                Text("重置", color = ClockColors.CreamDim)
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings"),
            ) {
                Text("设置", color = ClockColors.CreamDim)
            }
        }
    }
}

private fun statusText(ui: ClockUiState): String {
    val clock = ui.clock
    val increment = clock.control.incrementMs
    return when (clock.phase) {
        Phase.READY -> {
            val control = ClockFormat.mmss(clock.control.baseMs)
            if (increment > 0) {
                "双方各 $control，每步 +${increment / 1000} 秒 · 点击任一方表盘开始"
            } else {
                "双方各 $control · 点击任一方表盘开始计时"
            }
        }
        Phase.RUNNING -> {
            val who = if (clock.active == Side.ONE) "玩家一" else "玩家二"
            if (increment > 0) "$who 行棋中 · 换手后该方 +${increment / 1000} 秒" else "$who 行棋中"
        }
        Phase.PAUSED -> "已暂停：双方时间冻结，点击“继续”由原计时方恢复"
        Phase.FINISHED -> "用时停在 0:00。点击“重置”开始新的一局。"
    }
}

@Composable
private fun NoticeRow(notice: RestoreNotice, onAcknowledge: () -> Unit) {
    val text = when (notice) {
        RestoreNotice.REBOOT_PAUSED ->
            "检测到设备重启：无法计算关机期间的时间，已按上次保存的读数暂停。请核对后点“继续”。"
        RestoreNotice.SNAPSHOT_DISCARDED ->
            "上次的对局记录已损坏，已重置为初始状态。"
        RestoreNotice.NONE -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp).testTag("noticeRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = ClockColors.Amber,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAcknowledge, modifier = Modifier.testTag("noticeDismiss")) {
            Text("知道了", color = ClockColors.Amber, fontSize = 12.sp)
        }
    }
}
