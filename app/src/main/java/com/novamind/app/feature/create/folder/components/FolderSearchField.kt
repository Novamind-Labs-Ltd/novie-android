package com.novamind.app.feature.create.folder.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.theme.AppTheme

/**
 * 文件夹「搜索 / 新建」输入框：白底圆角，空态显示占位文案。
 * 该输入同时作为新建文件夹的名称，故限制最大长度为 [AppConfig.Folder.NAME_MAX_CHARS]。
 */
@Composable
internal fun FolderSearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ItemBg,
        shadowElevation = 1.dp,
    ) {
        BasicTextField(
            value = query,
            // 兼作新建文件夹名，限制最大长度（超出即不接受新增字符）
            onValueChange = { if (it.length <= AppConfig.Folder.NAME_MAX_CHARS) onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = TextDark),
            cursorBrush = SolidColor(TextDark),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search or create new folder", fontSize = 15.sp, color = TextHint)
                }
                inner()
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Folder · Search field")
@Composable
private fun FolderSearchFieldPreview() {
    AppTheme {
        FolderSearchField(query = "", onQueryChange = {})
    }
}
