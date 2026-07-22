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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils.toHex

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val TextDark: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val FieldBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val Border: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()


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
        // 标题行：标题 + 右侧关闭
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Create new folder",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
            )
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close",
                tint = TextDark,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel,
                    ),
            )
        }

        // 名称输入框（描边圆角，带清除按钮）
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = FieldBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = name,
                    // 限制文件夹名最大长度（超出即不接受新增字符）
                    onValueChange = { if (it.length <= AppConfig.Folder.NAME_MAX_CHARS) name = it },
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
            AppConfig.Folder.COLORS.forEachIndexed { index, color ->
                FolderColorSwatch(
                    color = color,
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
                    onClick = { onCreate(trimmed, AppConfig.Folder.COLORS[selectedIndex].toHex()) },
                ),
            shape = RoundedCornerShape(50),
            color = if (canCreate) BackgroundColors.Primary.default.current() else BackgroundColors.Interactive.disabled.current(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Create", color = TextColors.Inverse.default.current(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FolderColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
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
            painter = painterResource(R.drawable.ic_folder_line),
            contentDescription = null,
            tint = color,
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

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Library · FolderColorSwatch")
@Composable
private fun FolderColorSwatchPreview() {
    AppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FolderColorSwatch(color = AppConfig.Folder.COLORS.first(), selected = true, onClick = {})
            FolderColorSwatch(color = AppConfig.Folder.COLORS.last(), selected = false, onClick = {})
        }
    }
}
