package com.novamind.app.feature.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

/**
 * 行内重命名行（Figma 879-26572）：× 取消 + 输入框（内含文件夹图标 + 自动聚焦文本框，强描边）
 * + 绿色实心圆形 ✓ 确认。确认回传非空的新名。
 */
@Composable
internal fun FolderRenameRow(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    colorHex: String? = null,
) {
    // 用 TextFieldValue 让初始光标停在文本末尾
    var value by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    val trimmed = value.text.trim()
    val canConfirm = trimmed.isNotEmpty()
    val accent = ColorUtils.parseHexColor(colorHex) ?: folderAccentFor(initialName)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun confirm() {
        if (canConfirm) onConfirm(trimmed)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // × 取消
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Cancel",
            tint = ColorTextTitle,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
        )
        // 输入框（#fcfaf6 圆角、强描边聚焦态）：文件夹图标 + 文本框
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            color = BgRow,
            border = BorderStroke(1.5.dp, BorderColors.Default.strong.current()),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_line),
                        contentDescription = null,
                        tint = ColorTextTitle,
                        modifier = Modifier.size(20.dp),
                    )
                }
                BasicTextField(
                    value = value,
                    // 限制文件夹名最大长度（超出即不接受新增字符）
                    onValueChange = { if (it.text.length <= AppConfig.Folder.NAME_MAX_CHARS) value = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                    cursorBrush = SolidColor(ColorTextTitle),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }
        }
        // ✓ 确认（绿色实心圆钮 + 白色对勾；名称为空时置灰）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canConfirm) IconColors.Brand.default.current() else ColorBorder)
                .clickable(enabled = canConfirm) { confirm() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Confirm",
                tint = IconColors.Default.onColor.current(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Library · FolderRenameRow")
@Composable
private fun FolderRenameRowPreview() {
    AppTheme {
        FolderRenameRow(initialName = "Work", onConfirm = {}, onCancel = {}, colorHex = null)
    }
}
