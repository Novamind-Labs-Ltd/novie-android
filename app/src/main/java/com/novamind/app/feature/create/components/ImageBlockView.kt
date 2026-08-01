package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.UploadState
import com.novamind.app.ui.theme.AppTheme
import java.io.File

@Composable
internal fun ImageBlockView(
    block: ImageBlock,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val isPreview = LocalInspectionMode.current
    // 已有笔记优先加载缩略图，未生成缩略图时回退原图；新插入图片用本地文件即时显示。
    val model = remember(isPreview, block.path, block.thumbnailUrl, block.remoteUrl) {
        if (isPreview) {
            null
        } else {
            block.thumbnailUrl ?: block.remoteUrl ?: File(block.path).takeIf { it.exists() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 356.dp)
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8E7E2)),
        ) {
            if (isPreview) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = "Note image",
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onClick,
                        ),
                )
            }
            // 上传态角标：上传中转圈；失败点击重试。UPLOADED/LOCAL 无遮罩。
            when (block.uploadState) {
                UploadState.UPLOADING -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                UploadState.FAILED -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onRetry,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Upload failed · tap to retry", color = Color.White, fontSize = 13.sp)
                }
                else -> Unit
            }
            // 右上角删除按钮（与 PDF/文件块一致的删除能力，悬浮于图片之上）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_note_image_close),
                    contentDescription = "Delete image",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 396, heightDp = 296, name = "Create · ImageBlockView")
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
