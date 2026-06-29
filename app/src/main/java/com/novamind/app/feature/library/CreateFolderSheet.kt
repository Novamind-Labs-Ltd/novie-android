package com.novamind.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.theme.AppTheme

private val SheetBg = Color(0xFFF0EFEA)
private val TextDark = Color(0xFF1A1A1A)
private val TextHint = Color(0xFFAAAAAA)
private val FieldBg = Color(0xFFFFFFFF)
private val Border = Color(0xFFE3E0D8)

/** 文件夹颜色选项：圆形底色 + 文件夹图标着色。hex 用于回传持久化。 */
internal data class FolderColorOption(val hex: String, val bg: Color, val icon: Color)

internal val folderColorOptions = listOf(
    FolderColorOption("", FieldBg, TextDark),                       // 默认（无着色）
    FolderColorOption("#388E64", Palette.forrest100, Palette.forrest600),
    FolderColorOption("#FF8C00", Palette.orange100, Palette.orange700),
    FolderColorOption("#808080", Palette.neutral100, Palette.neutral700),
    FolderColorOption("#C8391A", Palette.red100, Palette.red500),
    FolderColorOption("#4A8292", Palette.teal100, Palette.teal600),
)

/**
 * 「创建新文件夹」底部弹层：名称输入 + 文件夹颜色选择 + 创建 / 取消。
 * [onCreate] 回传文件夹名与所选颜色 hex（空串表示默认色）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderSheet(
    onCreate: (name: String, colorHex: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        CreateFolderContent(onCreate = onCreate, onCancel = onDismiss)
    }
}

@Composable
private fun CreateFolderContent(
    onCreate: (name: String, colorHex: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }
    val trimmed = name.trim()
    val canCreate = trimmed.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Create new folder",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 名称输入框（白底圆角，带清除按钮）
        Surface(shape = RoundedCornerShape(12.dp), color = FieldBg, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextDark),
                    cursorBrush = SolidColor(TextDark),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Folder name", fontSize = 16.sp, color = TextHint)
                        inner()
                    },
                )
                if (name.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = TextHint,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { name = "" },
                            ),
                    )
                }
            }
        }

        // 文件夹颜色
        Text(
            text = "Folder colour",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            folderColorOptions.forEachIndexed { index, option ->
                FolderColorSwatch(
                    option = option,
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 创建（黑色胶囊；名称为空时禁用）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    enabled = canCreate,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onCreate(trimmed, folderColorOptions[selectedIndex].hex.ifEmpty { null }) },
                ),
            shape = RoundedCornerShape(50),
            color = if (canCreate) Color(0xFF111111) else Color(0xFFBFBDB8),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("Create new folder", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 取消（描边胶囊）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onCancel,
                ),
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, TextDark),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FolderColorSwatch(
    option: FolderColorOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(option.bg)
            // 选中态：深色描边环
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TextDark else Border,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = option.icon,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun CreateFolderContentPreview() {
    AppTheme {
        Surface(color = SheetBg) {
            CreateFolderContent(onCreate = { _, _ -> }, onCancel = {})
        }
    }
}
