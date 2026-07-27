package com.novamind.app.feature.create.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.Button
import com.novamind.app.ui.components.ButtonVariant
import com.novamind.app.ui.theme.AppTheme

/**
 * 笔记边框颜色调色板。第一项为 null = 默认（灰）边框，其余直接取自设计系统 [Palette] 的基础色。
 * 渲染时直接用 Color，仅在选中入库时转成 #RRGGBB，不来回转换。
 * 这些是用户选定的强调色，深浅模式下保持一致，故为绝对色，不随主题翻转。
 * 顺序与配色对齐 Figma「Note border」弹窗（默认灰 → 绿 / 橙 / 蓝灰 / 米棕 / 青）。
 */
val NoteBorderColors: List<Color?> = listOf(
    null,                 // 默认（灰）
    Palette.forrest500,   // 绿  #257550
    Palette.orange600,    // 橙  #ff8c00
    Palette.slate600,     // 蓝灰 #708090
    Palette.chart01,      // 米棕 #c9b99e
    Palette.teal400,      // 青  #32b4d9
)

/**
 * 「Note border」选色弹窗（居中 Alert Dialog，对齐 Figma）：
 * 标题 + 说明 + 6 色圈网格（当前选中显示深色描边 + 对勾）+ Cancel / Apply。
 *
 * 两段式：点色圈仅更新本地选中态，点 [onApply] 才提交（回传 null 表示恢复默认边框）；
 * [onDismiss]（Cancel / 点外部）不改动颜色。
 */
@Composable
fun BorderColorDialog(
    selectedColor: Color?,
    onApply: (Color?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 本地选中态：进入时取当前颜色，点 Apply 才回传
    var selected by remember(selectedColor) { mutableStateOf(selectedColor) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BorderColorDialogContent(
            selected = selected,
            onSelect = { selected = it },
            onApply = { onApply(selected) },
            onCancel = onDismiss,
        )
    }
}

/** 弹窗内容（与 Dialog 容器解耦，便于 @Preview / 复用）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BorderColorDialogContent(
    selected: Color?,
    onSelect: (Color?) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth(0.88f)
            .widthIn(max = 360.dp)
            .shadow(elevation = 16.dp, shape = cardShape)
            .clip(cardShape)
            .background(BackgroundColors.Interactive.tertiary.current())   // #fcfaf6
            .padding(top = 40.dp, bottom = 24.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Note border",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextColors.Primary.default.current(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Choose a colour for this note.",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            color = TextColors.Primary.secondary.current(),
            textAlign = TextAlign.Center,
        )

        // 「默认」色圈用语义边框色（随主题）；其余为绝对强调色
        val defaultSwatch = BorderColors.Default.default.current()
        FlowRow(
            modifier = Modifier
                .width(204.dp)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = 3,
        ) {
            NoteBorderColors.forEach { color ->
                ColorSwatch(
                    color = color ?: defaultSwatch,
                    selected = color == selected,
                    onClick = { onSelect(color) },
                )
            }
        }

        // CTAs：与通用 AppAlertDialog 保持一致（描边取消 + 实心确认胶囊按钮）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                text = "Cancel",
                onClick = onCancel,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = "Apply",
                onClick = onApply,
                variant = ButtonVariant.Primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 选中：黑色描边（Figma border/outline/primary）+ 对勾；未选中：与自身同色描边（不可见）
    val ring = if (selected) BorderColors.Outline.primary.current() else color
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
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
            Checkmark(color = checkColor, modifier = Modifier.size(18.dp))
        }
    }
}

/** 对勾（两段圆头折线，对齐 Figma tick）。 */
@Composable
private fun Checkmark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        val p1 = Offset(w * 0.20f, h * 0.52f)
        val p2 = Offset(w * 0.42f, h * 0.72f)
        val p3 = Offset(w * 0.78f, h * 0.30f)
        drawLine(color, p1, p2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, p2, p3, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** 估算颜色明度（0~1），用于决定对勾用深色还是白色。 */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF8A8A8A)
@Composable
private fun BorderColorDialogPreview() {
    var selected by remember { mutableStateOf(NoteBorderColors[0]) }
    AppTheme {
        Surface(color = Color(0x80000000)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                BorderColorDialogContent(
                    selected = selected,
                    onSelect = { selected = it },
                    onApply = {},
                    onCancel = {},
                )
            }
        }
    }
}
