package com.novamind.app.feature.create.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R

/**
 * 插入附件选择弹窗：Image / Camera / Document 三列。
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
        containerColor = ColorChipBg,
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AttachmentItem(R.drawable.ic_image, "Image", onClick = onPickImage)
        AttachmentItem(R.drawable.ic_camera, "Camera", onClick = onTakePhoto)
        AttachmentItem(R.drawable.ic_document, "Document", onClick = onPickDocument)
    }
}

@Composable
private fun AttachmentItem(
    iconResId: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = ColorTextTitle,
            modifier = Modifier.size(26.dp),
        )
        Text(label, fontSize = 14.sp, color = ColorTextTitle)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AttachmentContentPreview() {
    com.novamind.app.ui.theme.AppTheme {
        AttachmentContent(onPickImage = {}, onTakePhoto = {}, onPickDocument = {})
    }
}
