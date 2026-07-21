package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.Button
import com.novamind.app.ui.components.ButtonVariant
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.time.LocalTime
import kotlin.math.abs

// ─── 配色：统一引用 ui/colors 设计令牌，随主题深浅自动解析（不使用硬编码颜色） ──────
private val DialogBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.tertiary.current()   // #fcfaf6
private val HighlightBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()           // 选中带底
private val SelectedText: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()               // 选中：品牌绿
private val NearText: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // 相邻：主文字
private val FarText: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()              // 远处：弱化

// 滚轮几何：奇数可见行数，中间一行为选中位。
private const val VISIBLE_COUNT = 5
private val ItemHeight = 44.dp

/** 上午 / 下午。用枚举而非裸字符串，避免 12 小时制拼接时的语义歧义。 */
enum class DayPeriod(val label: String) { AM("AM"), PM("PM") }

/**
 * 时间选择弹窗（Figma: Alert dialog / 时间滚轮）。
 *
 * 三列滚轮：小时(1–12) · 分钟(00–59) · 上午/下午。中间高亮带为选中位，
 * 选中值用品牌绿加粗、相邻行正常、远处行弱化，贴合设计稿的纵深层次。
 * 底部复用通用 [Button]：左「Cancel」(描边) / 右「Ok」(深色实心)。
 *
 * 采用 12 小时制 + AM/PM，[onConfirm] 统一回传 24 小时制的 [LocalTime]，
 * 调用方无需关心显示态与存储态的换算。
 *
 * 交互最佳实践：
 * - 惯性滑动后自动吸附到整行（[rememberSnapFlingBehavior]）；
 * - 选中行变化时给一次轻触感反馈（[LocalHapticFeedback]）；
 * - 每列有语义描述，可被无障碍服务朗读。
 *
 * @param initial     初始时间（默认当前时间）
 * @param onConfirm   点击 Ok 回传所选时间（24 小时制）
 * @param onDismiss   点击 Cancel / 遮罩 / 返回键关闭
 * @param minuteStep  分钟步进（默认 1；传 5 则为 00/05/10…）
 */
@Composable
fun TimeWheelPickerDialog(
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initial: LocalTime = LocalTime.now(),
    minuteStep: Int = 1,
) {
    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }

    // 12 小时制拆解：0 点与 12 点均显示为 12。
    val initialHour12 = ((initial.hour + 11) % 12) + 1
    val initialPeriod = if (initial.hour < 12) DayPeriod.AM else DayPeriod.PM
    // 分钟按步进就近对齐到可选项，避免 07 分在 5 分步进下无对应行。
    val initialMinuteIndex = minutes.indexOfFirst { it >= initial.minute }.coerceAtLeast(0)

    // 弹窗内的瞬态选择，进程重建后可恢复。
    var hour12 by rememberSaveable { mutableIntStateOf(initialHour12) }       // 1..12
    var minuteIndex by rememberSaveable { mutableIntStateOf(initialMinuteIndex) }
    var periodOrdinal by rememberSaveable { mutableIntStateOf(initialPeriod.ordinal) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(24.dp),
            color = DialogBg,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 滚轮区：中间高亮带铺满宽度，三列滚轮叠在其上。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ItemHeight * VISIBLE_COUNT),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ItemHeight)
                            .clip(RoundedCornerShape(100.dp))
                            .background(HighlightBg),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WheelPickerColumn(
                            itemCount = 12,
                            selectedIndex = hour12 - 1,
                            onSelectedIndexChange = { hour12 = it + 1 },
                            label = { "%d".format(it + 1) },
                            contentDescription = "Hour",
                            modifier = Modifier.width(72.dp),
                        )
                        WheelPickerColumn(
                            itemCount = minutes.size,
                            selectedIndex = minuteIndex,
                            onSelectedIndexChange = { minuteIndex = it },
                            label = { "%02d".format(minutes[it]) },
                            contentDescription = "Minute",
                            modifier = Modifier.width(72.dp),
                        )
                        WheelPickerColumn(
                            itemCount = DayPeriod.entries.size,
                            selectedIndex = periodOrdinal,
                            onSelectedIndexChange = { periodOrdinal = it },
                            label = { DayPeriod.entries[it].label },
                            contentDescription = "AM or PM",
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = "Ok",
                        onClick = {
                            val period = DayPeriod.entries[periodOrdinal]
                            val hour24 = when {
                                period == DayPeriod.AM && hour12 == 12 -> 0
                                period == DayPeriod.PM && hour12 != 12 -> hour12 + 12
                                else -> hour12
                            }
                            onConfirm(LocalTime.of(hour24, minutes[minuteIndex]))
                        },
                        variant = ButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 单列滚轮：垂直吸附滚动，中间行为选中位。
 *
 * 用首尾各 [PAD] 个空行把首/末真实项也能滚到正中；由此选中项索引恰等于
 * [androidx.compose.foundation.lazy.LazyListState.firstVisibleItemIndex]（推导见文件说明）。
 * 停止滚动后回传选中索引，滚动中按与选中行的距离分级着色（0 绿 / 1 主 / ≥2 弱）。
 */
@Composable
private fun WheelPickerColumn(
    itemCount: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (Int) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val pad = VISIBLE_COUNT / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptics = LocalHapticFeedback.current

    // 停在正中的真实项索引（吸附后 offset≈0，故等于 firstVisibleItemIndex）。
    val centerIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, itemCount - 1) }
    }

    // 选中行变化：轻触感 + 回传。drop(1) 跳过初始值，避免开屏即回调/震动。
    LaunchedEffect(listState, itemCount) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .drop(1)
            .collect { idx ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectedIndexChange(idx)
            }
    }

    val selected = label(centerIndex)
    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .height(ItemHeight * VISIBLE_COUNT)
            .semantics { this.contentDescription = "$contentDescription: $selected" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count = pad) { Spacer(Modifier.height(ItemHeight)) }
        items(count = itemCount) { index ->
            val distance = abs(index - centerIndex)
            val color = when (distance) {
                0 -> SelectedText
                1 -> NearText
                else -> FarText
            }
            Box(
                modifier = Modifier
                    .height(ItemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(index),
                    fontSize = if (distance == 0) 22.sp else 20.sp,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(count = pad) { Spacer(Modifier.height(ItemHeight)) }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(name = "TimeWheelPicker · 7:40 PM", showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun TimeWheelPickerPreview() {
    AppTheme {
        TimeWheelPickerDialog(
            onConfirm = {},
            onDismiss = {},
            initial = LocalTime.of(19, 40),
        )
    }
}

@Preview(name = "TimeWheelPicker · 5 分步进", showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun TimeWheelPickerStepPreview() {
    AppTheme {
        TimeWheelPickerDialog(
            onConfirm = {},
            onDismiss = {},
            initial = LocalTime.of(8, 0),
            minuteStep = 5,
        )
    }
}
