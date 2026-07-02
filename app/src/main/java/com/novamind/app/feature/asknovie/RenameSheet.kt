package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val TitleColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val SubColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val FieldBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
private val FieldBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val DarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val OnDarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()

/** 重命名会话标题的底部弹窗。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 初始光标定位到文末
    var text by remember {
        mutableStateOf(TextFieldValue(initialTitle, TextRange(initialTitle.length)))
    }
    val focusRequester = remember { FocusRequester() }
    // 等底部弹窗完全展开后再聚焦，避免“键盘先于弹窗出现”
    LaunchedEffect(Unit) {
        snapshotFlow { sheetState.currentValue }
            .filter { it == SheetValue.Expanded }
            .first()
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        RenameContent(
            text = text,
            onTextChange = { text = it },
            onSave = { onSave(text.text.trim()) },
            onCancel = onDismiss,
            focusRequester = focusRequester,
        )
    }
}

/** 弹窗的纯内容（不含 sheet 容器与聚焦时序），便于复用与 @Preview。 */
@Composable
private fun RenameContent(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("Rename", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TitleColor)
        Spacer(20)

        Text("Title", fontSize = 13.sp, color = SubColor)
        Spacer(8)

        // 标题输入框
        Surface(color = FieldBg, shape = RoundedCornerShape(12.dp)) {
            BasicTextField(
                value = text,
                // 重建不带 composition 的值，去掉输入法的 composing 下划线
                onValueChange = { onTextChange(TextFieldValue(it.text, it.selection)) },
                textStyle = TextStyle(color = TitleColor, fontSize = 15.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                ),
                cursorBrush = SolidColor(TitleColor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FieldBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .focusRequester(focusRequester),
            )
        }

        Spacer(24)

        // Save（黑色实心）
        PillButton(
            text = "Save",
            filled = true,
            enabled = text.text.isNotBlank(),
            onClick = onSave,
        )
        Spacer(12)
        // Cancel（描边）
        PillButton(text = "Cancel", filled = false, enabled = true, onClick = onCancel)
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) DarkPill else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (filled) Modifier.background(if (enabled) bg else bg.copy(alpha = 0.4f))
                else Modifier.border(1.5.dp, DarkPill, RoundedCornerShape(26.dp)),
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) OnDarkPill else DarkPill,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Spacer(dp: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(dp.dp))
}

// ── Preview（预览内容层；ModalBottomSheet 为窗口层，静态预览不渲染） ──

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "Rename · 有标题")
@Composable
private fun RenameContentPreview() {
    AppTheme {
        RenameContent(
            text = TextFieldValue("Trip planning for Tokyo"),
            onTextChange = {},
            onSave = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "Rename · 空标题（Save 置灰）")
@Composable
private fun RenameContentEmptyPreview() {
    AppTheme {
        RenameContent(
            text = TextFieldValue(""),
            onTextChange = {},
            onSave = {},
            onCancel = {},
        )
    }
}
