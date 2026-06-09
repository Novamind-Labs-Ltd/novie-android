package com.novamind.app.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

private val BgPage = Color(0xFFF0EFEA)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextHint = Color(0xFFAAAAAA)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorChipBg = Color(0xFFFFFFFF)
private val ColorChipBorder = Color(0xFFE0E0E0)

@Composable
fun CreateScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ── 返回按钮 ──────────────────────────────────────────────────────────
        Box(modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Surface(
                shape = CircleShape,
                color = ColorChipBg,
                shadowElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = ColorTextTitle,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── 标题输入 ──────────────────────────────────────────────────────────
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ColorTextTitle,
            ),
            cursorBrush = SolidColor(ColorTextTitle),
            decorationBox = { inner ->
                if (title.isEmpty()) {
                    Text(
                        text = "New note",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ColorTextTitle,
                    )
                }
                inner()
            },
        )

        // ── Meta 操作行 ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaChip(iconResId = R.drawable.ic_nav_library, label = "Add to folder")
            MetaChip(iconResId = R.drawable.ic_nav_brand, label = "Tags")
            MetaChip(iconResId = R.drawable.ic_nav_calendar, label = "Today 08:31")
        }

        // ── 正文输入 ──────────────────────────────────────────────────────────
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = ColorTextTitle,
                lineHeight = 24.sp,
            ),
            cursorBrush = SolidColor(ColorTextTitle),
            decorationBox = { inner ->
                if (body.isEmpty()) {
                    Text(
                        text = "Type here...",
                        fontSize = 16.sp,
                        color = ColorTextHint,
                    )
                }
                inner()
            },
        )

        // ── 格式工具栏（键盘弹起时才显示）────────────────────────────────────
        if (imeVisible) {
            FormattingToolbar()
        }
    }
}

// ─── Meta Chip ────────────────────────────────────────────────────────────────

@Composable
private fun MetaChip(iconResId: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = ColorChipBg,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = ColorTextSub,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = ColorTextSub,
            )
        }
    }
}

// ─── 格式工具栏 ───────────────────────────────────────────────────────────────

@Composable
private fun FormattingToolbar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 左侧主工具组
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            color = ColorChipBg,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIcon(R.drawable.ic_mic, "Voice")
                ToolbarIcon(R.drawable.ic_attach, "Attach")
                ToolbarIcon(R.drawable.ic_magic, "Magic")
                // Bold
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("B", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitle)
                }
                // Italic
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("I", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = ColorTextTitle)
                }
                ToolbarIcon(R.drawable.ic_format_list, "List")
            }
        }

        // 右侧收起键盘按钮
        Surface(
            shape = CircleShape,
            color = ColorChipBg,
            shadowElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_hide),
                    contentDescription = "Hide keyboard",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(iconResId: Int, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun CreateRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CreateScreen(onBack = onBack, modifier = modifier)
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CreateScreenPreview() {
    AppTheme { CreateScreen() }
}
