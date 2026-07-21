package com.novamind.app.feature.calendar

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
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
private val ColorSuccess: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()
private val ColorError: Color
    @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()
// 主要 CTA（黑底白字胶囊，对应设计稿 Ok 按钮 / 选中日）
private val ColorCtaBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val ColorCtaText: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
// 次级按钮（描边胶囊，对应设计稿 Cancel 按钮）
private val ColorSecBorder: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Secondary.border.current()
private val ColorSecText: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Secondary.text.current()

private val dueFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
// 选择器头部日期（设计稿「Mon, Jan 17」）：固定英文，绕开 Material 头部本地化的不确定性。
private val pickerHeadlineFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

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
    /** 编辑模式的完成状态；null = 新增模式（不显示完成状态切换按钮）。 */
    completed: Boolean? = null,
    /** 点击完成状态切换按钮（仅编辑模式显示）。 */
    onToggleCompleted: () -> Unit = {},
    /** 删除任务；null = 不显示删除按钮（新增模式）。 */
    onDelete: (() -> Unit)? = null,
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    // LocalDate 非 Bundle 类型，以 epochDay(Long) 持久化，进程重建后可恢复。
    var dueEpochDay by rememberSaveable { mutableStateOf(initialDue.toEpochDay()) }
    val due = LocalDate.ofEpochDay(dueEpochDay)
    // 编辑已有描述时直接展开输入框。
    var showNotesField by rememberSaveable { mutableStateOf(initialNotes.isNotBlank()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // 内容是否有变更：驱动 Save 可用性与返回时的放弃确认。
    val dirty = title != initialTitle || notes != initialNotes || dueEpochDay != initialDue.toEpochDay()
    // 标题非空且有变更才可保存（内容没变时 Save 置灰）。
    val canSave = title.isNotBlank() && dirty

    // 关闭页面前**立即**收起键盘：AnimatedVisibility 退出动画期间文本框仍持有焦点，
    // 若等其销毁后 IME 才开始收起，会与页面滑出串行、观感迟滞（Activity 为
    // adjustNothing，键盘不参与布局重排，滞留更明显）。主动收起让两者并行。
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboard: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // 内容有变更时，返回需二次确认（左上角返回与系统返回同一路径）。
    val attemptClose = {
        hideKeyboard()
        if (dirty) showDiscardConfirm = true else onBack()
    }
    BackHandler(onBack = attemptClose)

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
            Text("To-do", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = canSave) {
                            hideKeyboard()
                            onSave(title.trim(), notes.trim().ifBlank { null }, due)
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
                    .clickable {
                        hideKeyboard()
                        showDatePicker = true
                    }
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

        // 完成状态切换（仅编辑模式）：已完成 → 标记未完成；未完成 → 标记已完成。
        if (completed != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ColorSurface)
                    .clickable {
                        hideKeyboard()
                        onToggleCompleted()
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = if (completed) ColorTextSub else ColorSuccess,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (completed) "Mark as not completed" else "Mark as completed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed) ColorTextSub else ColorSuccess,
                )
            }
        }

        // 删除任务（仅编辑模式）：不可恢复，需二次确认。
        if (onDelete != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ColorSurface)
                    .clickable {
                        hideKeyboard()
                        showDeleteConfirm = true
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = ColorError,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Delete task",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorError,
                )
            }
        }
    }

    // 放弃变更确认：内容已修改但未保存时，返回先确认。
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onBack()
                }) { Text("Discard", color = ColorError) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }

    // 删除确认：Google Tasks 无回收站，删除不可恢复。
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this task?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = ColorError) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // 日期选择弹窗：DatePicker 用 UTC 毫秒，转换固定走 ZoneOffset.UTC 避免时区偏一天。
    if (showDatePicker) {
        // 强制英文（US）区域：与全 App 英文文案及设计稿一致（月份/星期英文、周日起始）。
        // 必须包裹到 rememberDatePickerState —— 内部 CalendarModel（月份标签「July 2026」、
        // 星期名与起始日）在建 state 时即按区域定型，仅包裹 DatePicker 无效。
        // 同时覆盖 LocalContext：部分 Material 内部按 context.resources 取区域，只改
        // LocalConfiguration 不够，需提供 en-US 的 configuration context 一并生效。
        val baseConfig = LocalConfiguration.current
        val baseContext = LocalContext.current
        val enConfig = remember(baseConfig) {
            Configuration(baseConfig).apply { setLocales(LocaleList(Locale.US)) }
        }
        val enContext = remember(baseContext, enConfig) {
            baseContext.createConfigurationContext(enConfig)
        }
        CompositionLocalProvider(
            LocalConfiguration provides enConfig,
            LocalContext provides enContext,
        ) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        // 显式配色还原设计稿：主题走系统动态取色，不显式指定会取到壁纸色而非品牌绿/黑。
        // 品牌绿用于月份选择与「今天」；选中日 / 选中年为黑底白字（对应 CTA）。
        val brandGreen = ColorPrimary
        val ctaBg = ColorCtaBg
        val onCta = ColorCtaText
        val pickerColors = DatePickerDefaults.colors(
            containerColor = ColorSurface,
            headlineContentColor = ColorTextTitle,
            weekdayContentColor = ColorTextSub,
            // 月份标签「2026年7月」+ 上/下月箭头共用 navigationContentColor（M3 无法分开），
            // 设计稿以绿色月份为品牌重点，故整行取品牌绿。
            subheadContentColor = brandGreen,
            navigationContentColor = brandGreen,
            yearContentColor = ColorTextSub,
            currentYearContentColor = ColorTextTitle,
            selectedYearContentColor = onCta,
            selectedYearContainerColor = ctaBg,
            dayContentColor = ColorTextTitle,
            selectedDayContentColor = onCta,
            selectedDayContainerColor = ctaBg,
            todayContentColor = brandGreen,
            todayDateBorderColor = brandGreen,
            dividerColor = ColorBorder,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = pickerColors,
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dueEpochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        showDatePicker = false
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ctaBg,
                        contentColor = onCta,
                    ),
                ) { Text("Ok", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDatePicker = false },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, ColorSecBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorSecText),
                ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
            },
        ) {
            DatePicker(
                state = pickerState,
                colors = pickerColors,
                // 头部只显示日期，无「Select date」小标题（title=null）。
                title = null,
                // 自定义 headline：固定英文格式「Mon, Jan 17」，随选中日更新（读 selectedDateMillis）。
                headline = {
                    val millis = pickerState.selectedDateMillis
                    Text(
                        text = millis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                .format(pickerHeadlineFormatter)
                        }.orEmpty(),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp),
                    )
                },
            )
        }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "AddTask · Empty Form")
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

@Preview(showBackground = true, showSystemUi = true, name = "AddTask · Editing (Incomplete)")
@Composable
private fun AddTaskScreenEditPreview() {
    AppTheme {
        AddTaskScreen(
            initialDue = LocalDate.now(),
            onSave = { _, _, _ -> },
            onBack = {},
            initialTitle = "Submit expense report",
            initialNotes = "Include taxi receipts",
            completed = false,
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "AddTask · Editing (Completed)")
@Composable
private fun AddTaskScreenEditCompletedPreview() {
    AppTheme {
        AddTaskScreen(
            initialDue = LocalDate.now(),
            onSave = { _, _, _ -> },
            onBack = {},
            initialTitle = "Reply to Alice",
            completed = true,
            onDelete = {},
        )
    }
}
