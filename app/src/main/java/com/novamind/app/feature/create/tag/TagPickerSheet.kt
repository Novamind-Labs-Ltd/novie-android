package com.novamind.app.feature.create.tag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// 配色：统一引用 ui/colors 设计系统令牌（不使用硬编码颜色）
private val TextDark: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val FieldBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onTagToggle: (Tag) -> Unit,
    onNewTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        TagPickerContent(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onTagToggle = onTagToggle,
            onNewTag = onNewTag,
            // 固定高度：占屏幕约 85%，不随标签数量伸缩
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        )
    }
}

/** 标签选择的纯内容（与 sheet 容器解耦，便于 @Preview / 复用）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerContent(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onTagToggle: (Tag) -> Unit,
    onNewTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val filtered = remember(query, availableTags) {
        if (trimmed.isEmpty()) availableTags
        else availableTags.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
    // 没有同名标签时，允许「创建」
    val canCreate = trimmed.isNotEmpty() && availableTags.none { it.name.equals(trimmed, ignoreCase = true) }

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Select tags",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // 搜索 / 新建输入框
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = FieldBg,
            shadowElevation = 1.dp,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = TextDark),
                cursorBrush = SolidColor(TextDark),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search or create new tag", fontSize = 15.sp, color = TextHint)
                    }
                    inner()
                },
            )
        }

        // 标签列表（多选）：填满固定高度内的剩余空间，超出可滚动
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filtered.forEach { tag ->
                val isSelected = selectedTags.any { it.id == tag.id }
                TagChip(
                    tag = tag,
                    isSelected = isSelected,
                    onClick = { onTagToggle(tag) },
                )
            }
        }

        if (filtered.isEmpty() && !canCreate) {
            Text(
                text = "No matching tag",
                fontSize = 13.sp,
                color = TextHint,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        // 无精准匹配时，底部显示「创建新标签」按钮
        if (canCreate) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = {
                            onNewTag(trimmed)
                            query = ""
                        },
                    ),
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = BorderStroke(1.dp, TextDark),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create new tag ‘$trimmed’",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                    )
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

private val previewTags = listOf(
    Tag(id = "t1", name = "Research", colorHex = "#3D7A5A"),
    Tag(id = "t2", name = "Strategy", colorHex = "#7A6D3D"),
    Tag(id = "t3", name = "Design", colorHex = "#3D5A7A"),
)

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun TagPickerContentPreview() {
    var selected by remember { mutableStateOf(previewTags.take(2)) }
    AppTheme {
        Surface(color = SheetBg) {
            TagPickerContent(
                availableTags = previewTags,
                selectedTags = selected,
                onTagToggle = { tag ->
                    selected = if (selected.any { it.id == tag.id }) {
                        selected.filterNot { it.id == tag.id }
                    } else {
                        selected + tag
                    }
                },
                onNewTag = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp),
            )
        }
    }
}
