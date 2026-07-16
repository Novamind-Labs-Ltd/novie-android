package com.novamind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// ─── 配色：统一引用 ui/colors 设计令牌，随主题深浅自动解析（不使用硬编码颜色） ──────
private val DialogBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.tertiary.current()   // #fcfaf6
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // #333
private val TextBody: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()             // #656565
private val ConfirmBg: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // #333 深色
private val DangerBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.background.current()       // 红
private val DismissBg: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()              // #a3a3a3
private val OnButton: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()               // 常白

/**
 * 通用居中提醒弹窗（Figma: AlertDialog）。
 *
 * 标题 + 可选说明 + 一到两个按钮。默认双按钮：左「取消」（灰）/ 右「确认」（深色，[destructive] 时为红）。
 * 传 [dismissLabel] = null 则只显示单个确认按钮（铺满一行）。
 *
 * @param onDismissRequest 点击遮罩/返回键关闭时回调
 * @param title            标题
 * @param message          说明文案（可空）
 * @param confirmLabel     确认按钮文案
 * @param onConfirm        确认回调（调用方负责关闭弹窗）
 * @param dismissLabel     取消按钮文案；为 null 时隐藏取消按钮
 * @param onDismiss        取消回调，默认等于 [onDismissRequest]
 * @param destructive      确认按钮是否用危险色（红）
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String = "OK",
    dismissLabel: String? = "Cancel",
    onDismiss: () -> Unit = onDismissRequest,
    destructive: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(24.dp),
            color = DialogBg,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 24.dp)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextTitle,
                    textAlign = TextAlign.Center,
                )
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextBody,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dismissLabel != null) {
                        DialogButton(
                            label = dismissLabel,
                            background = DismissBg,
                            textColor = OnButton,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DialogButton(
                        label = confirmLabel,
                        background = if (destructive) DangerBg else ConfirmBg,
                        textColor = OnButton,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
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
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(name = "AlertDialog · 双按钮")
@Composable
private fun AlertDialogPreview() {
    AppTheme {
        AlertDialog(
            onDismissRequest = {},
            title = "Move to Recycle Bin?",
            message = "You can restore this note from the Bin within 30 days.",
            confirmLabel = "Move",
            onConfirm = {},
        )
    }
}

@Preview(name = "AlertDialog · 危险态")
@Composable
private fun AlertDialogDangerPreview() {
    AppTheme {
        AlertDialog(
            onDismissRequest = {},
            title = "Delete permanently?",
            message = "This note will be deleted and can't be restored.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {},
        )
    }
}

@Preview(name = "AlertDialog · 单按钮")
@Composable
private fun AlertDialogSinglePreview() {
    AppTheme {
        AlertDialog(
            onDismissRequest = {},
            title = "Saved",
            message = "Your note has been synced to the cloud.",
            confirmLabel = "Got it",
            dismissLabel = null,
            onConfirm = {},
        )
    }
}
