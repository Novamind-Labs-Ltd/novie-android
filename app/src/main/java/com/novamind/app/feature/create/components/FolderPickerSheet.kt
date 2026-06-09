package com.novamind.app.feature.create.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.model.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<Folder>,
    selectedFolder: Folder?,
    onFolderSelect: (Folder?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF0EFEA),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Add to folder",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // 无文件夹选项
            FolderRow(
                emoji = "✕",
                name = "No folder",
                isSelected = selectedFolder == null,
                onClick = { onFolderSelect(null) },
            )

            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)

            folders.forEach { folder ->
                FolderRow(
                    emoji = folder.iconEmoji,
                    name = folder.name,
                    isSelected = selectedFolder?.id == folder.id,
                    onClick = { onFolderSelect(folder) },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    emoji: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primary = Color(0xFF3D7A5A)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) primary.copy(alpha = 0.08f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = emoji, fontSize = 20.sp)
                Text(
                    text = name,
                    fontSize = 15.sp,
                    color = if (isSelected) primary else Color(0xFF1A1A1A),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
