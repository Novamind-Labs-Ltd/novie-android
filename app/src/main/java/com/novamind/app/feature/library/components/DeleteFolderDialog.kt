package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 删除文件夹二次确认（Figma 879-26439「Alert dialog」）：居中弹窗。
 * 圆角 24、#fcfaf6 卡片 + 柔和投影；标题「Delete folder?」、正文含斜体文件夹名；
 * 底部并排两枚胶囊按钮：Cancel（描边）/ Delete（红色实心）。
 */
@Composable
internal fun DeleteFolderDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DeleteFolderCard(folderName = folderName, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/** 弹窗卡片（解耦出 Dialog 以便预览）。 */
@Composable
private fun DeleteFolderCard(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BgRow, // background/interactive/tertiary #fcfaf6
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
                    text = "Delete folder?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // 文件夹名以斜体强调（对齐设计稿）
                    text = buildAnnotatedString {
                        append("This will permanently delete the ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(folderName) }
                        append(" folder.")
                    },
                    fontSize = 14.sp,
                    color = ColorTextSub,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cancel（描边胶囊）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(100))
                        .background(BgRow)
                        .border(1.5.dp, ButtonColors.Secondary.border.current(), RoundedCornerShape(100))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", color = ColorTextTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                // Delete（红色实心胶囊）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(100))
                        .background(ButtonColors.Destructive.background.current())
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Delete",
                        color = ButtonColors.Destructive.text.current(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF888888, name = "Library · DeleteFolder Dialog")
@Composable
private fun DeleteFolderCardPreview() {
    AppTheme {
        DeleteFolderCard(folderName = "Q3 KPIs", onConfirm = {}, onDismiss = {})
    }
}
