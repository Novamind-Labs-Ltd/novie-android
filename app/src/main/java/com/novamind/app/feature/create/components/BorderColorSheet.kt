package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import androidx.core.graphics.toColorInt

/**
 * 笔记边框颜色调色板。第一项为 null = 默认（灰）边框，其余直接取自设计系统 [Palette] 的基础色。
 * 渲染时直接用 Color，仅在选中入库时转成 #RRGGBB，不来回转换。
 * 这些是用户选定的强调色，深浅模式下保持一致（红就是红），故为绝对色，不随主题翻转。
 */
val NoteBorderColors: List<Color?> = listOf(
    null,                 // 默认（灰）
    Palette.forrest600,   // 品牌绿
    Palette.fern500,      // 柔绿
    Palette.green500,     // 橄榄绿
    Palette.teal500,      // 青
    Palette.slate600,     // 蓝灰
    Palette.orange600,    // 橙
    Palette.red500,       // 红
    Palette.sand700,      // 灰褐
    Palette.neutral600,   // 灰
)
/**
 * 「Select border colour」底部弹窗：一排可选色圈，当前选中项显示对勾。
 * 点击某色即回调 [onSelect]（null 表示恢复默认边框）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderColorSheet(
    selectedColor: Color?,
    onSelect: (Color?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColors.Surface.elevated.current(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        BorderColorContent(selectedColor = selectedColor, onSelect = onSelect)
    }
}

/** 选色内容（与 sheet 容器解耦，便于 @Preview / 复用）。 */
@Composable
private fun BorderColorContent(
    selectedColor: Color?,
    onSelect: (Color?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Select border colour",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextColors.Primary.default.current(),   // 标题随主题
        )
        // 「默认」色圈用语义边框色（随主题）
        val defaultSwatch = BorderColors.Default.default.current()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteBorderColors.forEach { color ->
                ColorSwatch(
                    color = color ?: defaultSwatch,
                    selected = color == selectedColor,
                    onClick = { onSelect(color) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 细描边随主题：浅色时偏深、深色时偏浅，保证色圈与背景有分隔
    val ring = TextColors.Primary.default.current().copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, ring, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // 对勾颜色取决于「色圈本身」明暗（绝对色，与主题无关）：浅底深勾、深底白勾
            val checkColor = if (color.luminance() > 0.6f) Palette.gray900 else Color.White
            Text(text = "✓", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = checkColor)
        }
    }
}

/** 估算颜色明度（0~1），用于决定对勾用深色还是白色。 */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF7F6F2)
@Composable
private fun BorderColorContentPreview() {
    var selected by remember { mutableStateOf(NoteBorderColors[1]) }
    AppTheme {
        Surface(color = BackgroundColors.Surface.elevated.current()) {
            BorderColorContent(
                selectedColor = selected,
                onSelect = { selected = it },
            )
        }
    }
}
