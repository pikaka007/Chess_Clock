package com.bara.chessclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.chessclock.core.ClockFormat
import com.bara.chessclock.core.Side
import kotlin.math.min

/**
 * One player's half of the board.
 *
 * Product requirements behind this layout (docs/requirements.md §3 U8/U9):
 *  - the whole half is one big tap target, because a player's hand lands anywhere on it;
 *  - the opponent's half is rotated 180 degrees so it reads correctly from the far side of the table;
 *  - state is carried by **luminance + a filled/outlined shape + a short text label**, never by colour
 *    alone: "计时中" / "等待" / "已暂停" / "超时";
 *  - the remaining time is the first thing read (largest element), the side label and state are second.
 */
@Composable
fun ClockFace(
    side: Side,
    remainingMs: Long,
    isRunning: Boolean,
    isFlagged: Boolean,
    isPaused: Boolean,
    hasStarted: Boolean,
    incrementMs: Long,
    /** Non-null for ~1.6 s after this side received a Fischer increment. */
    flashIncrementMs: Long?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerLabel = if (side == Side.ONE) "玩家一" else "玩家二"
    val stateLabel = when {
        isFlagged -> "超时"
        isRunning -> "计时中"
        isPaused && hasStarted -> "已暂停"
        hasStarted -> "未计时"
        else -> "点击开始"
    }

    val faceColor = when {
        isFlagged -> ClockColors.FrozenFace
        isRunning -> ClockColors.ActiveFace
        isPaused && hasStarted -> ClockColors.FrozenFace
        else -> ClockColors.InactiveFace
    }
    val contentColor = if (isRunning && !isFlagged) ClockColors.OnCream else ClockColors.Cream
    val borderColor = when {
        isFlagged -> ClockColors.Danger
        isRunning -> ClockColors.ActiveFaceBorder
        else -> ClockColors.Outline
    }
    val borderWidth = if (isRunning || isFlagged) 3.dp else 1.dp
    val spoken = ClockFormat.spoken(remainingMs)
    val tag = if (side == Side.ONE) "clockP1" else "clockP2"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(faceColor)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClickLabel = if (isRunning) "$playerLabel 正在计时" else "$playerLabel 表盘") { onTap() }
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$playerLabel 表盘，剩余 $spoken，$stateLabel"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Digits are sized from the available space so the same layout works on a 5" phone in
        // portrait, in landscape, and with a larger system font (fontScale is capped below).
        val byHeight = maxHeight.value * 0.34f
        val byWidth = maxWidth.value / 6.4f
        val digitSize = min(byHeight, byWidth).coerceIn(24f, 132f)
        val capScale = min(LocalDensity.current.fontScale, 1.15f)

        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, capScale)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playerLabel,
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (incrementMs > 0) {
                        Text(
                            text = "  +${incrementMs / 1000}s",
                            color = if (isRunning) ClockColors.OnCream.copy(alpha = 0.65f) else ClockColors.Amber,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Text(
                    text = ClockFormat.display(remainingMs),
                    color = contentColor,
                    fontSize = digitSize.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(if (side == Side.ONE) "clockP1Time" else "clockP2Time"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatePill(
                        label = stateLabel,
                        filled = isRunning && !isFlagged,
                        danger = isFlagged,
                    )
                    if (flashIncrementMs != null && flashIncrementMs > 0) {
                        Text(
                            text = "+${flashIncrementMs / 1000} 秒",
                            color = ClockColors.Amber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag(if (side == Side.ONE) "incrementFlashP1" else "incrementFlashP2"),
                        )
                    }
                }
            }
        }
    }
}

/** Text label plus a shape change (filled vs outlined), so colour is never the only signal. */
@Composable
private fun StatePill(label: String, filled: Boolean, danger: Boolean) {
    val bg = when {
        danger -> Color.Transparent
        filled -> ClockColors.OnCream.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val fg = when {
        danger -> ClockColors.Danger
        filled -> ClockColors.OnCream
        else -> ClockColors.CreamDim
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, fg.copy(alpha = if (filled || danger) 0.9f else 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
