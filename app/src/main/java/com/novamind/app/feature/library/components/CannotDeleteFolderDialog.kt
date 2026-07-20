package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 「无法删除文件夹」提示弹窗（Figma 879-26452）：文件夹内仍有笔记时,不允许删除。
 * 与删除确认同款居中卡片；单个「Got it」深色胶囊按钮关闭。
 */
@Composable
internal fun CannotDeleteFolderDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CannotDeleteFolderCard(onDismiss = onDismiss)
    }
}

@Composable
private fun CannotDeleteFolderCard(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BgRow,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 38.dp, bottom = 24.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Cannot delete folder",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "This folder contains notes. Please move or delete the notes " +
                        "inside before attempting to delete the folder.",
                    fontSize = 14.sp,
                    color = ColorTextSub,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Got it（深色实心胶囊）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(100))
                    .background(ButtonColors.Primary.background.current())
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Got it",
                    color = ButtonColors.Primary.text.current(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF888888, name = "Library · CannotDeleteFolder")
@Composable
private fun CannotDeleteFolderCardPreview() {
    AppTheme {
        CannotDeleteFolderCard(onDismiss = {})
    }
}
