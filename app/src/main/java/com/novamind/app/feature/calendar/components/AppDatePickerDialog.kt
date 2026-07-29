package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headlineFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
// 周日起始（与 Figma S M T W T F S 一致）
private val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
private val yearRange = 1970..2100
// 日历/年份切换区固定高度，避免两态切换时弹窗高度跳动（6 周 × 约 44dp）
private val calendarBodyHeight = 264.dp

/**
 * 自定义日期选择弹窗（Figma 1032-42991）：不再用 Material3 DatePicker（其日历宽固定 360dp、
 * 无法随屏等比），改为自绘——7 列按 weight 均分，日历随弹窗宽度等比填满，贴合设计比例。
 *
 * 结构：头部「Mon, Jan 17」· 月份「January 2024」(点开切年份) + 上/下月箭头 ·
 * 周标题 · 日期网格（选中日 #242424 实心圆、今天绿色描边）· 底部 Cancel / Ok。
 *
 * 供 AddTaskScreen / EditMeetingScreen 等复用。
 */
@Composable
internal fun AppDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 按屏占比：左右各留 ~16dp（贴合设计蓝框），封顶避免平板过宽
    val dialogWidth = (screenWidthDp - 32).dp.coerceIn(320.dp, 460.dp)

    var selected by remember { mutableStateOf(initialDate) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var yearMode by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val dialogBg = BackgroundColors.Interactive.tertiary.current()
    val monthGreen = TextColors.Success.default.current()
    val selectedBg = ButtonColors.Primary.backgroundSecondary.current()
    val onSelected = ButtonColors.Primary.text.current()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(dialogWidth),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                // ── 头部：选中日期 ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected.format(headlineFormatter),
                        fontSize = 32.sp,
                        color = ColorTextTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── 控制条：月份标签（点开/收起年份）+ 上/下月箭头 ──────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { yearMode = !yearMode }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = visibleMonth.format(monthFormatter),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = monthGreen,
                        )
                    }
                    if (!yearMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NavArrow(flip = true, desc = "Previous month") {
                                visibleMonth = visibleMonth.minusMonths(1)
                            }
                            NavArrow(flip = false, desc = "Next month") {
                                visibleMonth = visibleMonth.plusMonths(1)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.padding(top = 4.dp)) {
                    if (yearMode) {
                        YearGrid(
                            currentYear = visibleMonth.year,
                            selectedBg = selectedBg,
                            onSelected = onSelected,
                            onPick = { year ->
                                visibleMonth = visibleMonth.withYear(year)
                                yearMode = false
                            },
                        )
                    } else {
                        Column {
                            // 周标题
                            Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                                weekdayLabels.forEach { label ->
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ColorTextTitle,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            // 日期网格
                            MonthGrid(
                                month = visibleMonth,
                                selected = selected,
                                today = today,
                                selectedBg = selectedBg,
                                onSelected = onSelected,
                                onPick = { selected = it },
                            )
                        }
                    }
                }

                // ── 底部 CTA：Cancel（描边）/ Ok（深底白字）等宽 ─────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp, ButtonColors.Secondary.border.current(),
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ButtonColors.Secondary.text.current(),
                        ),
                    ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = { onConfirm(selected) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonColors.Primary.background.current(),
                            contentColor = ButtonColors.Primary.text.current(),
                        ),
                    ) { Text("Ok", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/** 上/下月箭头（复用 ic_chevron_right，上一月水平翻转）。 */
@Composable
private fun NavArrow(flip: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = desc,
            tint = ColorTextTitle,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { if (flip) scaleX = -1f },
        )
    }
}

/** 月份日期网格：7 列 weight 均分，随弹窗宽等比填满；选中日实心圆，今天绿色描边。 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    selectedBg: androidx.compose.ui.graphics.Color,
    onSelected: androidx.compose.ui.graphics.Color,
    onPick: (LocalDate) -> Unit,
) {
    // 周日起始的前置空格数：SUNDAY(7)%7=0 … SATURDAY(6)=6
    val leading = month.atDay(1).dayOfWeek.value % 7
    val length = month.lengthOfMonth()
    val cells: List<Int?> = buildList {
        repeat(leading) { add(null) }
        for (d in 1..length) add(d)
        while (size % 7 != 0) add(null)
    }
    Box(modifier = Modifier.height(calendarBodyHeight)) {
        Column {
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val date = month.atDay(day)
                                DayCircle(
                                    day = day,
                                    isSelected = date == selected,
                                    isToday = date == today,
                                    selectedBg = selectedBg,
                                    onSelected = onSelected,
                                    onClick = { onPick(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个日期圆：选中=实心 #242424 白字；今天(未选中)=绿色描边+绿字；其余=普通深色。 */
@Composable
private fun DayCircle(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    selectedBg: androidx.compose.ui.graphics.Color,
    onSelected: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(selectedBg)
                    isToday -> Modifier.border(1.dp, ColorPrimary, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            fontSize = 14.sp,
            color = when {
                isSelected -> onSelected
                isToday -> ColorPrimary
                else -> ColorTextTitle
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** 年份选择（点月份标签展开）：3 列网格，选中年高亮，选中即跳转并收起。 */
@Composable
private fun YearGrid(
    currentYear: Int,
    selectedBg: androidx.compose.ui.graphics.Color,
    onSelected: androidx.compose.ui.graphics.Color,
    onPick: (Int) -> Unit,
) {
    val years = remember { yearRange.toList() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        val idx = years.indexOf(currentYear).coerceAtLeast(0)
        // 居中当前年（每行 3 个，往上偏 2 行）
        gridState.scrollToItem((idx - 3).coerceAtLeast(0))
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(calendarBodyHeight + 40.dp),
    ) {
        items(years) { year ->
            val sel = year == currentYear
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .height(44.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100))
                    .then(if (sel) Modifier.background(selectedBg) else Modifier)
                    .clickable { onPick(year) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = year.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    color = if (sel) onSelected else ColorTextTitle,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF808080)
@Composable
private fun AppDatePickerDialogPreview() {
    AppTheme {
        AppDatePickerDialog(
            initialDate = LocalDate.of(2024, 1, 17),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
