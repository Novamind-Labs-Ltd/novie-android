package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils.toHex

/** 修改文件夹颜色（底部弹层）：点击色板即应用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeFolderColorSheet(
    currentHex: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        ChangeFolderColorContent(currentHex = currentHex, onPick = onPick)
    }
}

/** 弹层内容（解耦出 ModalBottomSheet 以便预览）。 */
@Composable
private fun ChangeFolderColorContent(currentHex: String?, onPick: (String?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Folder colour",
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
                val selected = optionHex.equals(currentHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) ColorTextTitle else ColorBorder,
                            shape = CircleShape,
                        )
                        .clickable { onPick(optionHex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Library · ChangeFolderColor 内容")
@Composable
private fun ChangeFolderColorContentPreview() {
    AppTheme {
        ChangeFolderColorContent(currentHex = AppConfig.Folder.COLORS.first().toHex(), onPick = {})
    }
}
