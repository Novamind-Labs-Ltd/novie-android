package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.feature.asknovie.AttachType
import com.novamind.app.feature.asknovie.Attachment
import com.novamind.app.ui.theme.AppTheme
import java.io.File

/** 已选附件 chip：图片显示圆角预览缩略图（不展示文件名）；文件显示图标 + 文件名。 */
@Composable
internal fun AttachmentChip(att: Attachment, onRemove: () -> Unit, onClick: () -> Unit = {}) {
    if (att.type == AttachType.Image) {
        ImageAttachmentPreview(att = att, onRemove = onRemove, onClick = onClick)
    } else {
        FileAttachmentChip(att = att, onRemove = onRemove)
    }
}

/** 图片附件：胶囊预览缩略图 + 右端移除按钮，不展示文件名；点击打开全屏预览。 */
@Composable
private fun ImageAttachmentPreview(att: Attachment, onRemove: () -> Unit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 40.dp)
            // 胶囊型裁剪：圆角半径 = 高度的一半，两端呈半圆
            .clip(RoundedCornerShape(50))
            .background(PlaceholderBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = File(att.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 右端移除按钮（白底圆形，叠在预览图上，垂直居中）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 5.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Card.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Remove",
                tint = TextTitle,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/** 文件 / 语音附件：图标 + 文件名 + 移除。 */
@Composable
private fun FileAttachmentChip(att: Attachment, onRemove: () -> Unit) {
    Surface(color = AttachChipBg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PlaceholderBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_document),
                    contentDescription = null,
                    tint = TextSub,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = att.name,
                color = TextTitle,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Remove",
                    tint = TextSub,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · Attachment Chip")
@Composable
private fun AttachmentChipPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(12.dp)) {
            AttachmentChip(
                att = Attachment(AttachType.File, "/tmp/report.pdf", "quarterly-report.pdf"),
                onRemove = {},
            )
            Spacer(Modifier.width(10.dp))
            // 图片路径在预览中不存在，AsyncImage 显示占位底色
            AttachmentChip(
                att = Attachment(AttachType.Image, "/tmp/photo.jpg", "photo.jpg"),
                onRemove = {},
            )
        }
    }
}
