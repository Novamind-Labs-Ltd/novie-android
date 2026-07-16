package com.novamind.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/** 通用按钮样式变体。 */
enum class ButtonVariant {
    /** 深色实心（#333），白字。 */
    Primary,
    /** 描边胶囊：浅底 + 深色描边 + 深字。 */
    Secondary,
    /** 危险实心（红），白字。 */
    Destructive,
}

// ─── 配色：统一引用 ui/colors 设计令牌，随主题深浅自动解析（不使用硬编码颜色） ──────
private val PrimaryBg: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // #333
private val SecondaryBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.tertiary.current()     // #fcfaf6
private val SecondaryBorder: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Secondary.border.current()             // 黑 / 深色主题白
private val DangerBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.background.current()        // 红
private val OnFilled: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()                // 常白
private val OnSecondary: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()                // #333

/**
 * 通用按钮：默认胶囊形，支持 [Primary]/[Secondary]/[Destructive] 三种样式，可禁用。
 * 由 AlertDialog、确认弹窗等场景共用。
 *
 * @param text      按钮文案
 * @param onClick   点击回调
 * @param variant   样式变体
 * @param enabled   是否可点击（禁用时降低透明度且不响应点击）
 * @param height    按钮高度（默认 40dp）
 * @param shape     形状（默认全胶囊）
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(100.dp),
) {
    val background = when (variant) {
        ButtonVariant.Primary -> PrimaryBg
        ButtonVariant.Secondary -> SecondaryBg
        ButtonVariant.Destructive -> DangerBg
    }
    val textColor = if (variant == ButtonVariant.Secondary) OnSecondary else OnFilled
    val border = if (variant == ButtonVariant.Secondary) BorderStroke(1.5.dp, SecondaryBorder) else null

    Box(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(name = "Button · 变体", showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun ButtonPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button("Cancel", onClick = {}, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
            Button("Move", onClick = {}, variant = ButtonVariant.Primary, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Button · 危险 / 禁用", showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun ButtonDangerPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button("Delete", onClick = {}, variant = ButtonVariant.Destructive, modifier = Modifier.weight(1f))
            Button("Disabled", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
        }
    }
}
