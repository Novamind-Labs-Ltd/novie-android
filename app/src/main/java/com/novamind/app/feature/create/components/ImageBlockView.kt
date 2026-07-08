package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.ui.theme.AppTheme
import java.io.File

@Composable
internal fun ImageBlockView(
    block: ImageBlock,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // 点击图片进入预览。已知宽高时用 aspectRatio 预留高度，避免加载完成后高度突变导致滚动跳动。
        AsyncImage(
            model = File(block.path),
            contentDescription = "Note image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (block.width > 0 && block.height > 0) {
                        Modifier.aspectRatio(block.width.toFloat() / block.height)
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8E7E2))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                ),
        )
        // 右上角删除按钮（与 PDF/文件块一致的删除能力，悬浮于图片之上）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 16.sp)
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Create · ImageBlockView")
@Composable
private fun ImageBlockViewPreview() {
    AppTheme {
        // AsyncImage 预览不加载真实图，占位即可
        ImageBlockView(
            block = ImageBlock(path = "/none.jpg", width = 1200, height = 800),
            onClick = {},
            onDelete = {},
        )
    }
}
