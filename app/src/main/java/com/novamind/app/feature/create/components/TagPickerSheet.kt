package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.model.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onTagToggle: (Tag) -> Unit,
    onNewTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTagInput by remember { mutableStateOf("") }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tags",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
            )

            // 新建标签输入框
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_brand),
                        contentDescription = null,
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(16.dp),
                    )
                    BasicTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1A1A1A)),
                        cursorBrush = SolidColor(Color(0xFF1A1A1A)),
                        decorationBox = { inner ->
                            if (newTagInput.isEmpty()) {
                                Text("New tag...", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                            }
                            inner()
                        },
                    )
                    if (newTagInput.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF3D7A5A),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    onNewTag(newTagInput.trim())
                                    newTagInput = ""
                                },
                            ),
                        ) {
                            Text(
                                text = "Add",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            // 标签列表
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableTags.forEach { tag ->
                    val isSelected = selectedTags.any { it.id == tag.id }
                    TagChip(
                        tag = tag,
                        isSelected = isSelected,
                        onClick = { onTagToggle(tag) },
                    )
                }
            }
        }
    }
}

@Composable
fun TagChip(
    tag: Tag,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagColor = parseHexColor(tag.colorHex)
    val bgColor = if (isSelected) tagColor.copy(alpha = 0.15f) else Color.White
    val borderColor = if (isSelected) tagColor else Color(0xFFE0E0E0)

    Surface(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(tagColor, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
            Text(
                text = tag.name,
                fontSize = 13.sp,
                color = if (isSelected) tagColor else Color(0xFF6B6B6B),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF3D7A5A)
    }
}
