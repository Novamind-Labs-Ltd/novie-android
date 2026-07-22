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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.novamind.app.feature.calendar.components.AppDatePickerDialog
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
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
    // LocalDate 非 Bundle 类型，以 epochDay(Long) 持久化，进程重建后可恢复。
    var dueEpochDay by rememberSaveable { mutableStateOf(initialDue.toEpochDay()) }
    val due = LocalDate.ofEpochDay(dueEpochDay)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // 内容是否有变更：驱动 Save 可用性与返回时的放弃确认（本页只编辑标题与截止日）。
    val dirty = title != initialTitle || dueEpochDay != initialDue.toEpochDay()
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
        // 顶栏（Figma top_info）：返回（左）+ 删除垃圾桶（右，仅编辑模式）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                iconRes = R.drawable.ic_arrow_back,
                desc = "Back",
                onClick = attemptClose,
            )
            Spacer(Modifier.weight(1f))
            if (onDelete != null) {
                CircleIconButton(
                    iconRes = R.drawable.ic_delete,
                    desc = "Delete task",
                    onClick = { hideKeyboard(); showDeleteConfirm = true },
                )
            }
        }

        // 大标题「To-do」（Figma 32sp Medium）
        Text(
            text = "To-do",
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = ColorTextTitle,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 16.dp),
        )

        // 表单卡片：仅标题 + 截止日期。
        // Google Tasks 不支持时间段/提醒/描述/附件，故设计稿中的这些行均不呈现。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColorSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 标题输入
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(fontSize = 16.sp, color = ColorTextTitle),
                cursorBrush = SolidColor(ColorPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text("Add a title", fontSize = 16.sp, color = ColorTextFaint)
                    }
                    inner()
                },
            )

            // 截止日期（Google Tasks 仅支持日期，无具体时间/时间段）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        hideKeyboard()
                        showDatePicker = true
                    }
                    .padding(vertical = 4.dp),
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

        // 底部主操作（Figma）：整宽黑色胶囊 Save，标题非空且有改动才可点。
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(100))
                .background(if (canSave) ButtonColors.Primary.background.current() else ColorBorder)
                .clickable(enabled = canSave) {
                    hideKeyboard()
                    onSave(title.trim(), initialNotes.trim().ifBlank { null }, due)
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (canSave) ButtonColors.Primary.text.current() else ColorTextFaint,
            )
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

    // 日期选择弹窗（Figma 959-62386，统一组件；强制英文 US、选中日回写 epochDay）
    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = due,
            onConfirm = { picked ->
                dueEpochDay = picked.toEpochDay()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** 顶栏圆形图标按钮（白底圆 + 轻投影），返回 / 删除共用。 */
@Composable
private fun CircleIconButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = ColorSurface, shadowElevation = 1.dp) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = desc,
                tint = ColorTextTitle,
                modifier = Modifier.size(20.dp),
            )
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
