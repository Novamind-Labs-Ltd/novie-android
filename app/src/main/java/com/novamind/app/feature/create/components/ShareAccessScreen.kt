package com.novamind.app.feature.create.components

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme

/**
 * 分享访问全屏页（从笔记编辑页「更多 → Share」进入）。
 *
 * 当前调试阶段：UI + 本地状态，无后端。
 * - 邮箱合法才点亮 Send；
 * - 点击 Send 把该邮箱加入「Manage access」列表（去重、忽略大小写），并清空输入框；
 * - Cancel 清空输入框；列表项可单独移除。
 *
 * @param onBack 返回（系统返回 / 左上角箭头）
 */
@Composable
fun ShareAccessScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    val accessList = remember { mutableStateListOf<String>() }

    ShareAccessContent(
        email = email,
        onEmailChange = { email = it },
        accessList = accessList,
        onSend = {
            val v = email.trim()
            if (v.isValidEmail() && accessList.none { it.equals(v, ignoreCase = true) }) {
                accessList.add(v)
            }
            email = ""
        },
        onCancel = { email = "" },
        onRemoveAccess = { accessList.remove(it) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun ShareAccessContent(
    email: String,
    onEmailChange: (String) -> Unit,
    accessList: List<String>,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onRemoveAccess: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = email.trim().isValidEmail()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.Page.default.current())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── 顶部：返回 + 标题 ──────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(
                onClick = onBack,
                background = BackgroundColors.Surface.default.current(),
                tint = TextColors.Primary.default.current(),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Share access",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextColors.Primary.default.current(),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Give access ───────────────────────────────────────────────
        Text(
            "Give access",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextColors.Primary.default.current(),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Email address",
            fontSize = 13.sp,
            color = TextColors.Primary.secondary.current(),
        )
        Spacer(Modifier.height(8.dp))

        // 邮箱输入框
        BasicTextField(
            value = email,
            onValueChange = onEmailChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = TextColors.Primary.default.current(),
            ),
            cursorBrush = SolidColor(TextColors.Primary.default.current()),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSend) onSend() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundColors.Surface.default.current())
                .border(1.dp, BorderColors.Input.default.current(), RoundedCornerShape(12.dp)),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )

        Spacer(Modifier.height(20.dp))

        // Send（合法邮箱才可点）
        PillButton(
            text = "Send",
            onClick = onSend,
            enabled = canSend,
            filled = true,
        )
        Spacer(Modifier.height(12.dp))
        // Cancel（描边）
        PillButton(
            text = "Cancel",
            onClick = onCancel,
            enabled = true,
            filled = false,
        )

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderColors.Default.default.current()),
        )
        Spacer(Modifier.height(24.dp))

        // ── Manage access ─────────────────────────────────────────────
        Text(
            "Manage access",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextColors.Primary.default.current(),
        )
        Spacer(Modifier.height(12.dp))

        if (accessList.isEmpty()) {
            Text(
                "No one else has access.",
                fontSize = 15.sp,
                color = TextColors.Primary.secondary.current(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(accessList, key = { it }) { addr ->
                    AccessRow(email = addr, onRemove = { onRemoveAccess(addr) })
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        !filled -> Color.Transparent // 描边按钮：与页面同色
        enabled -> ButtonColors.Primary.background.current()
        else -> ButtonColors.Primary.backgroundDisabled.current()
    }
    val textColor = when {
        !filled -> TextColors.Primary.default.current()
        enabled -> ButtonColors.Primary.text.current()
        else -> ButtonColors.Primary.textDisabled.current()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (!filled) Modifier.border(1.5.dp, BorderColors.Outline.primary.current(), shape)
                else Modifier
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun AccessRow(
    email: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundColors.Surface.default.current())
            .border(1.dp, BorderColors.Default.default.current(), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            email,
            fontSize = 15.sp,
            color = TextColors.Primary.default.current(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove access",
                tint = TextColors.Primary.secondary.current(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun String.isValidEmail(): Boolean =
    isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(this).matches()

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ShareAccessDisabledPreview() {
    AppTheme {
        ShareAccessContent(
            email = "",
            onEmailChange = {},
            accessList = emptyList(),
            onSend = {},
            onCancel = {},
            onRemoveAccess = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ShareAccessWithListPreview() {
    AppTheme {
        ShareAccessContent(
            email = "alex@novamind.ai",
            onEmailChange = {},
            accessList = listOf("sam@novamind.ai", "jordan@novamind.ai"),
            onSend = {},
            onCancel = {},
            onRemoveAccess = {},
            onBack = {},
        )
    }
}
