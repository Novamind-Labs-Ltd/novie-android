package com.novamind.app.feature.calendar

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.feature.calendar.components.AppDatePickerDialog
import com.novamind.app.feature.calendar.components.BgPage
import com.novamind.app.feature.calendar.components.ColorBorder
import com.novamind.app.feature.calendar.components.ColorPrimary
import com.novamind.app.feature.calendar.components.ColorSurface
import com.novamind.app.feature.calendar.components.ColorTextFaint
import com.novamind.app.feature.calendar.components.ColorTextSub
import com.novamind.app.feature.calendar.components.ColorTextTitle
import com.novamind.app.feature.calendar.components.TimeWheelPickerDialog
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val editDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val editTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 编辑会议页（由 [MeetingDetailScreen] 铅笔进入）：可改标题 / 日期 / 起止时间 / 地点 / 描述，
 * Save 通过 [onSave] 回传更新后的 [CalendarEvent]，由 Route 派发 UpdateMeeting 写回 Google 日历。
 *
 * 与 [AddTaskScreen] 同构：白色卡片表单 + 顶栏返回/Save；日期用 Material DatePicker（强制英文），
 * 时间用 [TimeWheelPickerDialog]。全天事件隐藏时间行（仅改日期/文本字段）。
 * 编辑态为瞬态 UI 状态，用 rememberSaveable 留在组件内，Save 时一次性上报。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMeetingScreen(
    event: CalendarEvent,
    onSave: (CalendarEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf(event.title) }
    var location by rememberSaveable { mutableStateOf(event.location.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(event.description.orEmpty()) }
    var dateEpochDay by rememberSaveable { mutableStateOf(event.start.toLocalDate().toEpochDay()) }
    // 时间以「一天中的分钟数」持久化，进程重建后可恢复。
    var startMinute by rememberSaveable { mutableStateOf(event.start.toLocalTime().let { it.hour * 60 + it.minute }) }
    var endMinute by rememberSaveable { mutableStateOf(event.end.toLocalTime().let { it.hour * 60 + it.minute }) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val startTime = LocalTime.of(startMinute / 60, startMinute % 60)
    val endTime = LocalTime.of(endMinute / 60, endMinute % 60)

    // 组装更新后的事件（全天事件时间取 00:00 占位，isAllDay 不变）。
    val updated = if (event.isAllDay) {
        event.copy(
            title = title.trim(),
            start = date.atStartOfDay(),
            end = date.atStartOfDay(),
            location = location.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
        )
    } else {
        event.copy(
            title = title.trim(),
            start = date.atTime(startTime),
            end = date.atTime(endTime),
            location = location.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
        )
    }
    val dirty = updated != event
    val canSave = title.isNotBlank() && dirty

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboard: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val attemptClose = {
        hideKeyboard()
        if (dirty) showDiscardConfirm = true else onBack()
    }
    BackHandler(onBack = attemptClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
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
                        .clickable(onClick = attemptClose),
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
            Text("Meeting", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = canSave) {
                            hideKeyboard()
                            onSave(updated)
                        }
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
            // 标题
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(fontSize = 18.sp, color = ColorTextTitle),
                cursorBrush = SolidColor(ColorPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("Add a title", fontSize = 18.sp, color = ColorTextFaint)
                    inner()
                },
            )

            Spacer(Modifier.height(20.dp))

            // 日期
            FieldRow(
                iconRes = R.drawable.ic_nav_calendar,
                label = "Date",
                value = date.format(editDateFormatter),
                onClick = { hideKeyboard(); showDatePicker = true },
            )

            // 起止时间（全天事件不显示）
            if (!event.isAllDay) {
                FieldRow(
                    iconRes = R.drawable.ic_clock,
                    label = "Starts",
                    value = startTime.format(editTimeFormatter),
                    onClick = { hideKeyboard(); showStartPicker = true },
                )
                FieldRow(
                    iconRes = R.drawable.ic_clock,
                    label = "Ends",
                    value = endTime.format(editTimeFormatter),
                    onClick = { hideKeyboard(); showEndPicker = true },
                )
            }

            HorizontalDivider(color = ColorBorder, modifier = Modifier.padding(vertical = 12.dp))

            // 地点
            EditableIconField(
                iconRes = R.drawable.ic_location,
                value = location,
                placeholder = "Add location",
                onValueChange = { location = it },
            )

            Spacer(Modifier.height(12.dp))

            // 描述
            EditableIconField(
                iconRes = R.drawable.ic_document,
                value = description,
                placeholder = "Add description",
                onValueChange = { description = it },
                singleLine = false,
            )
        }
    }

    // 放弃变更确认
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text("Discard", color = ColorTextTitle)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }

    // 时间选择（起 / 止）
    if (showStartPicker) {
        TimeWheelPickerDialog(
            initial = startTime,
            onConfirm = { picked ->
                startMinute = picked.hour * 60 + picked.minute
                // 保持 起 ≤ 止：若开始晚于结束，结束顺延为开始 + 原时长（至少同刻）。
                if (startMinute > endMinute) endMinute = startMinute
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimeWheelPickerDialog(
            initial = endTime,
            onConfirm = { picked ->
                endMinute = picked.hour * 60 + picked.minute
                if (endMinute < startMinute) startMinute = endMinute
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }

    // 日期选择（Figma 959-62386，统一组件；强制英文 US、选中日回写 epochDay）
    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onConfirm = { picked ->
                dateEpochDay = picked.toEpochDay()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** 只读字段行（点击打开选择器）：图标 + 标签 + 右侧值。 */
@Composable
private fun FieldRow(iconRes: Int, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), null, tint = ColorTextSub, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, color = ColorTextSub)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ColorTextTitle)
    }
}

/** 可编辑字段行：图标 + 内联输入框（空时显示 placeholder）。 */
@Composable
private fun EditableIconField(
    iconRes: Int,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(iconRes), null, tint = ColorTextSub, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle, lineHeight = 22.sp),
            cursorBrush = SolidColor(ColorPrimary),
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = ColorTextFaint)
                inner()
            },
        )
    }
}

// ── Preview ──

@Preview(showBackground = true, showSystemUi = true, name = "Edit meeting")
@Composable
private fun EditMeetingScreenPreview() {
    val day = LocalDate.now()
    AppTheme {
        EditMeetingScreen(
            event = CalendarEvent(
                id = "1",
                title = "Team stand-up",
                isAllDay = false,
                start = day.atTime(10, 0),
                end = day.atTime(11, 0),
                location = "Novamind Labs",
                eventType = CalendarEventType.DEFAULT,
                isMeeting = true,
                description = "Align on priorities and uncover roadblocks.",
            ),
            onSave = {},
            onBack = {},
        )
    }
}
