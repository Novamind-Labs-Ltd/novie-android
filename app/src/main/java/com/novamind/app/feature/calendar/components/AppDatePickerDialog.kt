package com.novamind.app.feature.calendar.components

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.current
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val datePickerHeadlineFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * 统一日期选择弹窗（Figma My-Novie 959-62386）：Material3 [DatePicker] 套设计系统配色。
 * - 弹窗底 background/interactive/tertiary（#fcfaf6）、圆角 24dp；
 * - 头部日期「Mon, Jan 17」、月份/箭头品牌绿、选中日 CTA 深底白字；
 * - 底部 Cancel（描边）/ Ok（深色）两个等宽按钮并排。
 *
 * 强制英文（US）区域：月份/星期英文、周日起始，与全 App 文案及设计稿一致。必须包裹到
 * [rememberDatePickerState]（内部 CalendarModel 建 state 时即按区域定型）并同时覆盖
 * LocalContext（部分 Material 内部按 context.resources 取区域）。
 * 日期换算统一走 UTC 毫秒，避免时区偏一天。
 *
 * 供 AddTaskScreen / EditMeetingScreen 等复用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val baseConfig = LocalConfiguration.current
    val baseContext = LocalContext.current
    val enConfig = remember(baseConfig) {
        Configuration(baseConfig).apply { setLocales(LocaleList(Locale.US)) }
    }
    val enContext = remember(baseContext, enConfig) { baseContext.createConfigurationContext(enConfig) }

    CompositionLocalProvider(
        LocalConfiguration provides enConfig,
        LocalContext provides enContext,
    ) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.toUtcMillis(),
        )
        // 弹窗宽度按屏占比（Figma：左右各留 ~16dp）。M3 DatePicker 日历内容固定 360dp，故不低于此值，
        // 且封顶避免平板上过宽；配 usePlatformDefaultWidth=false 覆盖 M3 默认固定宽。
        val dialogWidth = (enConfig.screenWidthDp - 32).dp.coerceIn(360.dp, 420.dp)
        val ctaBg = ButtonColors.Primary.background.current()
        val onCta = ButtonColors.Primary.text.current()
        // 选中日/年圆底：Figma button/primary/background-secondary(#242424)，比 Ok 按钮(纯黑)略浅
        val selectionBg = ButtonColors.Primary.backgroundSecondary.current()
        val pickerColors = DatePickerDefaults.colors(
            containerColor = BackgroundColors.Interactive.tertiary.current(),
            headlineContentColor = ColorTextTitle,
            weekdayContentColor = ColorTextSub,
            // 月份标签 +上/下月箭头共用 navigationContentColor（M3 无法分开），设计稿取品牌绿。
            subheadContentColor = ColorPrimary,
            navigationContentColor = ColorPrimary,
            yearContentColor = ColorTextSub,
            currentYearContentColor = ColorTextTitle,
            selectedYearContentColor = onCta,
            selectedYearContainerColor = selectionBg,
            dayContentColor = ColorTextTitle,
            selectedDayContentColor = onCta,
            selectedDayContainerColor = selectionBg,
            todayContentColor = ColorPrimary,
            todayDateBorderColor = ColorPrimary,
            dividerColor = ColorBorder,
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.width(dialogWidth),
            // 覆盖平台默认宽（约束到较窄），改用上面按屏占比算出的宽度
            properties = DialogProperties(usePlatformDefaultWidth = false),
            colors = pickerColors,
            shape = RoundedCornerShape(24.dp),
            // 两个等宽 CTA 并排（Figma）：全放 confirmButton 槽自定义布局，dismissButton 置空。
            dismissButton = null,
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100),
                        border = BorderStroke(1.5.dp, ButtonColors.Secondary.border.current()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ButtonColors.Secondary.text.current(),
                        ),
                    ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = {
                            pickerState.selectedDateMillis?.let { onConfirm(it.toLocalDateUtc()) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100),
                        colors = ButtonDefaults.buttonColors(containerColor = ctaBg, contentColor = onCta),
                    ) { Text("Ok", fontWeight = FontWeight.Medium) }
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                colors = pickerColors,
                // 头部只显示日期，无「Select date」小标题（title=null）。
                title = null,
                // 自定义 headline：固定英文「Mon, Jan 17」，随选中日更新。
                headline = {
                    Text(
                        text = pickerState.selectedDateMillis
                            ?.let { it.toLocalDateUtc().format(datePickerHeadlineFormatter) }
                            .orEmpty(),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp),
                    )
                },
            )
        }
    }
}
