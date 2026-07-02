package com.novamind.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import com.novamind.app.R
import com.novamind.app.common.config.FunConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val IconDefault: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()
private val IconDisabled: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.disabled.current()
private val LabelDefault: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val LabelDisabled: Color
    @Composable @ReadOnlyComposable get() = TextColors.Disabled.default.current()

/**
 * 通用「插入附件」选择弹窗：Image / Camera / Document 三列。
 * 可用于笔记编辑、Ask Novie 等任意需要选择附件的场景。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        AttachmentContent(
            onPickImage = { onDismiss(); onPickImage() },
            onTakePhoto = { onDismiss(); onTakePhoto() },
            onPickDocument = { onDismiss(); onPickDocument() },
        )
    }
}

@Composable
private fun AttachmentContent(
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickDocument: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AttachmentItem(R.drawable.ic_image, "Image", onClick = onPickImage)
        AttachmentItem(R.drawable.ic_camera, "Camera", onClick = onTakePhoto)
        // 上传文档由 FunConfig 分期控制：未开放时置灰不可点击（入口保留占位）。
        AttachmentItem(
            R.drawable.ic_document,
            "Document",
            enabled = FunConfig.UPLOAD_DOCUMENT_ENABLED,
            onClick = onPickDocument,
        )
    }
}

@Composable
private fun AttachmentItem(
    iconResId: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = if (enabled) IconDefault else IconDisabled,
            modifier = Modifier.size(26.dp),
        )
        Text(label, fontSize = 14.sp, color = if (enabled) LabelDefault else LabelDisabled)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AttachmentContentPreview() {
    AppTheme {
        AttachmentContent(onPickImage = {}, onTakePhoto = {}, onPickDocument = {})
    }
}
