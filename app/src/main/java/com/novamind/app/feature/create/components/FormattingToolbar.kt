package com.novamind.app.feature.create.components
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.BackgroundColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 输入时显示的格式工具栏（home_final / new conversation）：贴键盘的扁平米色条，
 * 左侧一组格式按钮（可横向滚动避免窄屏裁切），右侧固定「收起键盘」按钮。
 */
@Composable
fun FormattingToolbar(
    onHideKeyboard: () -> Unit = {},
    onVoice: () -> Unit = {},
    onBold: () -> Unit = {},
    isBoldActive: Boolean = false,
    onItalic: () -> Unit = {},
    isItalicActive: Boolean = false,
    onInsertImage: () -> Unit = {},
    onMagic: () -> Unit = {},
    onBulletList: () -> Unit = {},
    onNumberedList: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColors.Page.secondary.current())   // 设计：#fcfaf6 扁平条
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左组：格式按钮（窄屏可横向滚动）
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(R.drawable.ic_mic, "Voice", onClick = onVoice)
            ToolbarIcon(R.drawable.ic_attach, "Insert image", onClick = onInsertImage)
            ToolbarIcon(R.drawable.ic_magic, "Magic", onClick = onMagic)
            ToolbarTextBtn("B", FontWeight.ExtraBold, active = isBoldActive, onClick = onBold)
            ToolbarTextBtn("I", FontWeight.Bold, fontStyle = FontStyle.Italic, active = isItalicActive, onClick = onItalic)
            ToolbarIcon(R.drawable.ic_format_list, "Bullet list", onClick = onBulletList)
            ToolbarIcon(R.drawable.ic_format_list_numbered, "Numbered list", onClick = onNumberedList)
        }
        Spacer(Modifier.width(8.dp))
        // 右：收起键盘（固定）
        ToolbarIcon(R.drawable.ic_keyboard_hide, "Hide keyboard", onClick = onHideKeyboard)
    }
}

@Composable
private fun ToolbarIcon(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(100))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = TextColors.Primary.default.current(),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ToolbarTextBtn(
    text: String,
    fontWeight: FontWeight,
    fontStyle: FontStyle = FontStyle.Normal,
    active: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(100))
            .background(if (active) IconColors.Brand.default.current().copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            color = if (active) IconColors.Brand.default.current() else TextColors.Primary.default.current(),
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFCFAF6)
@Composable
private fun FormattingToolbarPreview() {
    AppTheme {
        FormattingToolbar(
            isBoldActive = true,
            isItalicActive = false,
        )
    }
}
