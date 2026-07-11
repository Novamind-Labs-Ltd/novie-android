package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils.toHex

/** 修改标签颜色（底部弹层）：点击色板即应用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeTagColorSheet(
    currentHex: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        ChangeTagColorContent(currentHex = currentHex, onPick = onPick)
    }
}

/** 弹层的纯内容（不含 sheet 容器），便于 @Preview。 */
@Composable
private fun ChangeTagColorContent(
    currentHex: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Tag colour",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppConfig.Folder.COLORS.forEach { color ->
                val optionHex = color.toHex()
                TagColorSwatch(
                    color = color,
                    selected = optionHex.equals(currentHex, ignoreCase = true),
                    onClick = { onPick(optionHex) },
                )
            }
        }
    }
}

// ModalBottomSheet 为窗口层，静态预览不渲染；预览内容层。
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Tag · Change color sheet")
@Composable
private fun ChangeTagColorContentPreview() {
    AppTheme {
        ChangeTagColorContent(
            currentHex = AppConfig.Folder.COLORS.first().toHex(),
            onPick = {},
        )
    }
}
