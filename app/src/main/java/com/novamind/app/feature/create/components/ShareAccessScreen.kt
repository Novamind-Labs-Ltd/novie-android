package com.novamind.app.feature.create.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
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
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.components.DeleteConfirmSheet
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.isValidEmail

/**
 * 分享访问全屏页（从笔记编辑页「更多 → Share」进入）。
 *
 * 当前调试阶段：UI + 本地状态，无后端。
 * - Email 框是「多 chip 输入」：输入邮箱后回车 / 空格 / 逗号 / 分号生成 chip；chip 可点 X 删除；
 * - Send 把所有 chip（含当前合法输入）加入「Manage access」并清空 share box（去重、忽略大小写）；
 * - Cancel 清空 share box；
 * - 返回拦截：share box 内有未发送的 chip 或文字时，系统返回 / 左上角箭头会弹「Discard changes?」确认。
 *
 * @param onBack 真正离开本页（确认丢弃或本无内容时调用）
 */
@Composable
fun ShareAccessScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val pending = remember { mutableStateListOf<String>() }
    val accessList = remember { mutableStateListOf<String>() }
    var showDiscard by remember { mutableStateOf(false) }

    // share box 是否有未发送内容（决定返回是否需要二次确认）
    val isDirty = pending.isNotEmpty() || input.isNotBlank()

    // 把当前输入尝试落为一个 chip：合法且未重复才加入
    fun commitInput(): Boolean {
        val v = input.trim()
        if (!v.isValidEmail()) return false
        val dup = pending.any { it.equals(v, ignoreCase = true) } ||
            accessList.any { it.equals(v, ignoreCase = true) }
        if (!dup) pending.add(v)
        input = ""
        return true
    }

    /** 键盘回车：校验当前输入的邮箱格式，合法则变成 chip，非法 Toast 提示（不发送）。 */
    fun submitInput() {
        val typed = input.trim()
        if (typed.isEmpty()) return
        if (!typed.isValidEmail()) {
            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        commitInput()
    }

    /**
     * 点击 Send：
     * 1) 若输入框有文字，先校验邮箱格式；
     * 2) 再校验发送后「已选择邮箱」总数不超过 [AppConfig.Share.MAX_ACCESS_EMAILS]；
     * 校验失败 Toast 提示并中止，全部通过才把 chip 加入 Manage access。
     */
    fun performSend() {
        val typed = input.trim()
        // 第一步：邮箱格式
        if (typed.isNotEmpty() && !typed.isValidEmail()) {
            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        // 第二步：数量上限（按去重后的最终选择数计算）
        val merged = LinkedHashSet(accessList.map { it.lowercase() })
        pending.forEach { merged.add(it.lowercase()) }
        if (typed.isNotEmpty()) merged.add(typed.lowercase())
        if (merged.size > AppConfig.Share.MAX_ACCESS_EMAILS) {
            Toast.makeText(
                context,
                "You can share with up to ${AppConfig.Share.MAX_ACCESS_EMAILS} people",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // 通过：落 chip + 入列表（去重，忽略大小写）
        commitInput()
        pending.forEach { p ->
            if (accessList.none { it.equals(p, ignoreCase = true) }) accessList.add(p)
        }
        pending.clear()
        input = ""
    }

    fun requestBack() {
        if (isDirty) showDiscard = true else onBack()
    }

    // 拦截系统返回：脏则确认，否则直接返回
    BackHandler { requestBack() }

    ShareAccessContent(
        input = input,
        onInputChange = { new ->
            // 含分隔符（空格/逗号/分号/换行）时切出一个 chip
            val sep = new.lastOrNull()
            if (sep != null && (sep == ' ' || sep == ',' || sep == ';' || sep == '\n')) {
                input = new.dropLast(1)
                if (!commitInput()) {
                    // 非法邮箱：去掉分隔符保留文字，便于用户修正
                    input = new.dropLast(1)
                }
            } else {
                input = new
            }
        },
        pending = pending,
        onRemovePending = { pending.remove(it) },
        accessList = accessList,
        canSend = pending.isNotEmpty() || input.isNotBlank(),
        onSubmitInput = { submitInput() },
        onSend = { performSend() },
        onCancel = {
            input = ""
            pending.clear()
        },
        onRemoveAccess = { accessList.remove(it) },
        onBack = { requestBack() },
        modifier = modifier,
    )

    if (showDiscard) {
        DeleteConfirmSheet(
            title = "Discard changes?",
            message = "You have unsent email addresses in the share box. " +
                "If you leave now, these will be cleared.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            confirmBackground = DiscardBlack,
            confirmTextColor = Color.White,
            onConfirm = {
                showDiscard = false
                input = ""
                pending.clear()
                onBack()
            },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun ShareAccessContent(
    input: String,
    onInputChange: (String) -> Unit,
    pending: List<String>,
    onRemovePending: (String) -> Unit,
    accessList: List<String>,
    canSend: Boolean,
    onSubmitInput: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onRemoveAccess: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.Page.default.current())
            .statusBarsPadding()
            // 键盘弹出时（窗口 ADJUST_NOTHING 不重排）抬升内容并可滚动，避免遮挡输入框/按钮
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
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

        EmailChipField(
            input = input,
            onInputChange = onInputChange,
            pending = pending,
            onRemovePending = onRemovePending,
            onSubmitInput = onSubmitInput,
        )

        Spacer(Modifier.height(20.dp))

        // Send（有 chip 或当前输入合法才可点）
        PillButton("Send", onClick = onSend, enabled = canSend, filled = true)
        Spacer(Modifier.height(12.dp))
        PillButton("Cancel", onClick = onCancel, enabled = true, filled = false)

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accessList.forEach { addr ->
                    AccessRow(email = addr, onRemove = { onRemoveAccess(addr) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 多邮箱 chip 输入框：bordered 容器内 FlowRow 排列 chip + 行内输入。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmailChipField(
    input: String,
    onInputChange: (String) -> Unit,
    pending: List<String>,
    onRemovePending: (String) -> Unit,
    onSubmitInput: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundColors.Surface.default.current())
            .border(1.dp, BorderColors.Input.default.current(), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pending.forEach { addr ->
                EmailChip(email = addr, onRemove = { onRemovePending(addr) })
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
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
                // 回车：校验格式后把当前输入变成 chip，键盘保持不收起以便连续输入
                keyboardActions = KeyboardActions(onDone = { onSubmitInput() }),
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 32.dp)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** 单个邮箱 chip：圆形首字母头像 + 邮箱 + X 删除。 */
@Composable
private fun EmailChip(email: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BackgroundColors.Surface.elevated.current())
            .border(1.dp, BorderColors.Default.default.current(), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BackgroundColors.Page.tertiary.current()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(email),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextColors.Primary.secondary.current(),
            )
        }
        Text(
            email,
            fontSize = 14.sp,
            color = TextColors.Primary.default.current(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                tint = TextColors.Primary.secondary.current(),
                modifier = Modifier.size(14.dp),
            )
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
private fun AccessRow(email: String, onRemove: () -> Unit) {
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

/** 取邮箱本地部分前两个字母作头像缩写。 */
private fun initialsOf(email: String): String {
    val local = email.substringBefore('@').filter { it.isLetterOrDigit() }
    return when {
        local.isEmpty() -> "?"
        local.length == 1 -> local.uppercase()
        else -> local.take(2).uppercase()
    }
}

// 仅本组件用：Discard 确认按钮的黑色（设计稿固定深色，非主题语义令牌）
private val DiscardBlack = Color(0xFF1A1A1A)

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ShareAccessChipsPreview() {
    AppTheme {
        ShareAccessContent(
            input = "",
            onInputChange = {},
            pending = listOf("apple@fruit.com", "banana@fruit.com", "grape@fruit.com"),
            onRemovePending = {},
            accessList = emptyList(),
            canSend = true,
            onSubmitInput = {},
            onSend = {},
            onCancel = {},
            onRemoveAccess = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ShareAccessEmptyPreview() {
    AppTheme {
        ShareAccessContent(
            input = "",
            onInputChange = {},
            pending = emptyList(),
            onRemovePending = {},
            accessList = listOf("sam@novamind.ai"),
            canSend = false,
            onSubmitInput = {},
            onSend = {},
            onCancel = {},
            onRemoveAccess = {},
            onBack = {},
        )
    }
}
