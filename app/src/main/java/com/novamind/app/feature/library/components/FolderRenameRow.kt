package com.novamind.app.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/** 行内重命名行：× 取消 + 自动聚焦输入框 + 绿色 ✓ 确认（回传非空且变化后的新名）。 */
@Composable
internal fun FolderRenameRow(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用 TextFieldValue 让初始光标停在文本末尾
    var value by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    val trimmed = value.text.trim()
    // 非空且与原名不同才可确认
    val canConfirm = trimmed.isNotEmpty() && trimmed != initialName
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun confirm() {
        if (canConfirm) onConfirm(trimmed)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
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
                .clickable(onClick = onCancel)
                .padding(2.dp),
        )
        // 输入框（白底圆角，自动聚焦）
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ColorTextTitle),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        // ✓ 确认（绿色）
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = "Confirm",
            tint = if (canConfirm) ColorAccent else ColorTextSub,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = canConfirm) { confirm() },
        )
    }
}

@Preview(showBackground = true, name = "Library · FolderRenameRow")
@Composable
private fun FolderRenameRowPreview() {
    AppTheme {
        FolderRenameRow(initialName = "Work", onConfirm = {}, onCancel = {})
    }
}
