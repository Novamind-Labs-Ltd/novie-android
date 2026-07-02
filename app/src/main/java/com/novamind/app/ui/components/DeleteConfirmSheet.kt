package com.novamind.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）。
// @Composable 函数的默认参数在组合上下文中求值，可直接引用这些 getter。
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val Danger: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.background.current()
private val OnDanger: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.text.current()

/**
 * 通用「二次确认」底部弹窗：标题 + 说明 + Cancel（描边）/ 确认（红色实心）。
 * 可用于删除笔记、删除图片等任意需要二次确认的场景。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Delete note?",
    message: String = "This will permanently delete the note.",
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
    confirmBackground: Color = Danger,
    confirmTextColor: Color = OnDanger,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        DeleteConfirmContent(
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            confirmBackground = confirmBackground,
            confirmTextColor = confirmTextColor,
        )
    }
}

/** 确认弹窗的纯内容（不含 sheet 容器），便于在其它容器/预览中复用。 */
@Composable
fun DeleteConfirmContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Delete note?",
    message: String = "This will permanently delete the note.",
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
    confirmBackground: Color = Danger,
    confirmTextColor: Color = OnDanger,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            fontSize = 15.sp,
            color = TextSub,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // Cancel —— 白底描边胶囊
        PillButton(
            label = dismissLabel,
            textColor = TextTitle,
            background = SheetBg,
            border = BorderStroke(1.5.dp, TextTitle),
            onClick = onDismiss,
        )
        // 确认 —— 实心胶囊（默认红色，可定制为黑色等）
        PillButton(
            label = confirmLabel,
            textColor = confirmTextColor,
            background = confirmBackground,
            border = null,
            onClick = onConfirm,
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    textColor: Color,
    background: Color,
    border: BorderStroke?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(50),
        color = background,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DeleteConfirmContentPreview() {
    AppTheme {
        DeleteConfirmContent(
            onConfirm = {},
            onDismiss = {},
            title = "Delete image?",
            message = "This will remove the image from the note.",
        )
    }
}
