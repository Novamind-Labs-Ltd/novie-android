package com.novamind.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// 配色：统一引用 ui/colors 设计系统令牌（不使用硬编码颜色）
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val ColorSurface: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorTextFaint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val ColorPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()

private val dueFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * 新增/编辑任务（To-do）页：标题 + 截止日期 + 描述（对应 Google Tasks 的 title/due/notes）。
 *
 * 新增与编辑共用：编辑时由调用方传入 [initialTitle] / [initialNotes] 预填，
 * Save 统一通过 [onSave] 上报当前值，创建还是更新由调用方决定。
 *
 * 无状态入口由 [CalendarRoute] 作为覆盖层承载；编辑中的文本/日期是**瞬态 UI 状态**，
 * 用 rememberSaveable 留在本组件内，Save 时一次性上报。
 *
 * 设计稿中的时间段/提醒/附件 Google Tasks API 不支持，此版本裁剪（见 calendar-connection-design.md）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    initialDue: LocalDate,
    onSave: (title: String, notes: String?, due: LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialNotes: String = "",
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    // LocalDate 非 Bundle 类型，以 epochDay(Long) 持久化，进程重建后可恢复。
    var dueEpochDay by rememberSaveable { mutableStateOf(initialDue.toEpochDay()) }
    val due = LocalDate.ofEpochDay(dueEpochDay)
    // 编辑已有描述时直接展开输入框。
    var showNotesField by rememberSaveable { mutableStateOf(initialNotes.isNotBlank()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val canSave = title.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // 顶栏：返回 / 标题 / Save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = ColorSurface, shadowElevation = 1.dp) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = ColorTextTitle,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text("To-do", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = canSave) { onSave(title.trim(), notes.trim().ifBlank { null }, due) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Save",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSave) ColorTextTitle else ColorTextFaint,
                    )
                }
            }
        }

        // 表单卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ColorSurface)
                .padding(20.dp),
        ) {
            // 标题输入
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(fontSize = 18.sp, color = ColorTextTitle),
                cursorBrush = SolidColor(ColorPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text("Add a title", fontSize = 18.sp, color = ColorTextFaint)
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(24.dp))

            // 截止日期（Google Tasks 仅支持日期，无具体时间）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_calendar),
                    contentDescription = null,
                    tint = ColorTextSub,
                    modifier = Modifier.size(20.dp),
                )
                Text("Due date", fontSize = 15.sp, color = ColorTextSub)
                Spacer(Modifier.weight(1f))
                Text(
                    text = due.format(dueFormatter),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextTitle,
                )
            }

            HorizontalDivider(
                color = ColorBorder,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // 描述：默认显示入口行，点击展开输入框（存入 Google Tasks notes）
            if (showNotesField) {
                BasicTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle, lineHeight = 22.sp),
                    cursorBrush = SolidColor(ColorPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (notes.isEmpty()) {
                            Text("Description", fontSize = 15.sp, color = ColorTextFaint)
                        }
                        inner()
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showNotesField = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_document),
                        contentDescription = null,
                        tint = ColorTextSub,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("Add description", fontSize = 15.sp, color = ColorTextSub)
                }
            }
        }
    }

    // 日期选择弹窗：DatePicker 用 UTC 毫秒，转换固定走 ZoneOffset.UTC 避免时区偏一天。
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        dueEpochDay = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "AddTask · 空表单")
@Composable
private fun AddTaskScreenPreview() {
    AppTheme {
        AddTaskScreen(
            initialDue = LocalDate.now(),
            onSave = { _, _, _ -> },
            onBack = {},
        )
    }
}
