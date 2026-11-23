package com.bara.chessclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.chessclock.core.ClockFormat
import com.bara.chessclock.core.TimeControl
import com.bara.chessclock.core.TimeControlDraft

/**
 * Setup screen. Its only job is to produce a [TimeControl] and hand it to the board, which is why
 * presets, the custom fields and the validation message all funnel into one "进入对局" action.
 */
@Composable
fun SettingsScreen(
    ui: ClockUiState,
    hasGameInProgress: Boolean,
    onDraftChange: (TimeControlDraft) -> Unit,
    onPresetSelected: (TimeControl) -> Unit,
    onStart: () -> Unit,
    onBackToBoard: () -> Unit,
) {
    val draft = ui.draft
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Keeps the start button reachable when the soft keyboard is open (the emulator showed
            // the keyboard covering it, which a player would hit too).
            .imePadding()
            .testTag("screenSettings"),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("棋钟", color = ClockColors.Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "两位棋手共用一个手机：上方的表盘朝向对手。",
                color = ClockColors.CreamDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Text("预设", color = ClockColors.CreamDim, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PresetCard(
                    title = "1 分钟",
                    subtitle = "闪电战 · 不加秒",
                    testTag = "preset1",
                    control = TimeControl.PRESET_1_MIN,
                    selected = draft.validate().let { it is TimeControlDraft.Validation.Valid && it.control == TimeControl.PRESET_1_MIN },
                    onClick = { onPresetSelected(TimeControl.PRESET_1_MIN) },
                    modifier = Modifier.weight(1f),
                )
                PresetCard(
                    title = "3 + 2",
                    subtitle = "3 分钟 + 每步 2 秒",
                    testTag = "preset3plus2",
                    control = TimeControl.PRESET_3_PLUS_2,
                    selected = draft.validate().let { it is TimeControlDraft.Validation.Valid && it.control == TimeControl.PRESET_3_PLUS_2 },
                    onClick = { onPresetSelected(TimeControl.PRESET_3_PLUS_2) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PresetCard(
                    title = "5 分钟",
                    subtitle = "标准 · 不加秒",
                    testTag = "preset5",
                    control = TimeControl.PRESET_5_MIN,
                    selected = draft.validate().let { it is TimeControlDraft.Validation.Valid && it.control == TimeControl.PRESET_5_MIN },
                    onClick = { onPresetSelected(TimeControl.PRESET_5_MIN) },
                    modifier = Modifier.weight(1f),
                )
                PresetCard(
                    title = "10 分钟",
                    subtitle = "快速棋 · 不加秒",
                    testTag = "preset10",
                    control = TimeControl.PRESET_10_MIN,
                    selected = draft.validate().let { it is TimeControlDraft.Validation.Valid && it.control == TimeControl.PRESET_10_MIN },
                    onClick = { onPresetSelected(TimeControl.PRESET_10_MIN) },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                "自定义时长",
                color = ClockColors.CreamDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberField(
                    label = "分",
                    value = draft.minutesText,
                    onChange = { onDraftChange(draft.copy(minutesText = it)) },
                    testTag = "minutesField",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "秒",
                    value = draft.secondsText,
                    onChange = { onDraftChange(draft.copy(secondsText = it)) },
                    testTag = "secondsField",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "每步加秒",
                    value = draft.incrementText,
                    onChange = { onDraftChange(draft.copy(incrementText = it)) },
                    testTag = "incrementField",
                    modifier = Modifier.weight(1f),
                )
            }

            val validation = draft.validate()
            val summary = when (validation) {
                is TimeControlDraft.Validation.Valid -> TimeControlDraft.describe(validation.control)
                is TimeControlDraft.Validation.Invalid -> ui.settingsError ?: validation.message
            }
            val isError = validation is TimeControlDraft.Validation.Invalid
            Text(
                text = summary,
                color = if (isError) ClockColors.Danger else ClockColors.Amber,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp).testTag("settingsSummary"),
            )
            if (validation is TimeControlDraft.Validation.Valid && validation.control.incrementMs > 0) {
                Text(
                    "加秒说明：每走完一步并按下对方表盘后，刚走完这一步的一方获得 " +
                        "${validation.control.incrementMs / 1000} 秒。暂停、继续、重复点击都不会加秒。",
                    color = ClockColors.CreamDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).testTag("incrementExplanation"),
                )
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .testTag("startGame"),
            ) {
                Text("进入对局", fontSize = 16.sp)
            }

            if (hasGameInProgress) {
                TextButton(
                    onClick = onBackToBoard,
                    modifier = Modifier.fillMaxWidth().testTag("backToBoard"),
                ) {
                    Text("返回当前对局", color = ClockColors.CreamDim)
                }
            }

            Text(
                "计时使用设备开机以来的单调时钟：改日期、改时区都不会影响对局，" +
                    "手机重启后则以暂停状态恢复。",
                color = ClockColors.CreamDim.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun PresetCard(
    title: String,
    subtitle: String,
    testTag: String,
    control: TimeControl,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) ClockColors.Amber else ClockColors.Outline
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ClockColors.SurfaceRaised else ClockColors.Surface)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "选择 $title") { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
    ) {
        Text(
            ClockFormat.mmss(control.baseMs) + if (control.incrementMs > 0) " +${control.incrementMs / 1000}" else "",
            color = ClockColors.Cream,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Text(title, color = ClockColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = ClockColors.CreamDim, fontSize = 11.sp)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            // Digits only: an emulator/soft keyboard can still insert separators, so filter here
            // instead of surfacing "只能填数字" for input the user could not avoid.
            onChange(raw.filter { it.isDigit() }.take(4))
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.testTag(testTag),
    )
}
