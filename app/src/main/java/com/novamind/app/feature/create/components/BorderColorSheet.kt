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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 笔记边框颜色调色板。第一项为 null = 默认（灰）边框，其余为自定义颜色 #RRGGBB。
 * 与首页 NoteCard 共用这套取值。
 */
val NoteBorderColors: List<String?> = listOf(
    null,        // 默认（灰）
    "#2E7D5B",   // 绿
    "#F5A623",   // 橙
    "#748AA0",   // 蓝灰
    "#C5402A",   // 红
    "#2BB3D6",   // 青
    "#7E57C2",   // 紫
    "#EC407A",   // 粉
    "#43A047",   // 亮绿
    "#5C6BC0",   // 靛蓝
    "#26A69A",   // 蓝绿
    "#8D6E63",   // 棕
    "#FB8C00",   // 深橙
    "#FDD835",   // 黄
)

/** 默认（null）色板展示用的灰色。 */
private val DefaultSwatch = Color(0xFFC9C9C9)
private val TextDark = Color(0xFF1A1A1A)

/** 把 #RRGGBB 解析为 Color；失败返回 null。供选色与卡片渲染共用。 */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

/**
 * 「Select border colour」底部弹窗：一排可选色圈，当前选中项显示对勾。
 * 点击某色即回调 [onSelect]（null 表示恢复默认边框）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderColorSheet(
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F6F2),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Select border colour",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteBorderColors.forEach { hex ->
                    ColorSwatch(
                        color = parseHexColor(hex) ?: DefaultSwatch,
                        selected = hex == selectedHex,
                        onClick = { onSelect(hex) },
                    )
                }
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
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // 浅色底用深色对勾，深色底用白色对勾，保证可读
            val checkColor = if (color.luminance() > 0.6f) TextDark else Color.White
            Text(text = "✓", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = checkColor)
        }
    }
}

/** 估算颜色明度（0~1），用于决定对勾用深色还是白色。 */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
