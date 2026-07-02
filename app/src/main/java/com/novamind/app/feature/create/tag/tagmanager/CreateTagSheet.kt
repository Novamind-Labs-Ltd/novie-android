package com.novamind.app.feature.create.tag.tagmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.feature.create.tag.tagmanager.components.BgCard
import com.novamind.app.feature.create.tag.tagmanager.components.BgPage
import com.novamind.app.feature.create.tag.tagmanager.components.ColorTextHint
import com.novamind.app.feature.create.tag.tagmanager.components.ColorTextTitle
import com.novamind.app.feature.create.tag.tagmanager.components.TagColorSwatch
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.util.ColorUtils.toHex

// 配色与颜色圆点组件在 tagmanager/components 包（TagManagerColors / TagColorSwatch）。
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BgPage
private val TextDark: Color
    @Composable @ReadOnlyComposable get() = ColorTextTitle
private val TextHint: Color
    @Composable @ReadOnlyComposable get() = ColorTextHint
private val FieldBg: Color
    @Composable @ReadOnlyComposable get() = BgCard

/**
 * 「创建新标签」底部弹层：名称输入 + 颜色选择 + 创建 / 取消（交互对齐创建文件夹）。
 * [onCreate] 回传标签名与所选颜色 hex。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTagSheet(
    onCreate: (name: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        CreateTagContent(onCreate = onCreate, onCancel = onDismiss)
    }
}

@Composable
private fun CreateTagContent(
    onCreate: (name: String, colorHex: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }
    val trimmed = name.trim()
    val canCreate = trimmed.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Create new tag",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(top = 4.dp),
        )

        Surface(shape = RoundedCornerShape(12.dp), color = FieldBg, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = TextDark),
                    cursorBrush = SolidColor(TextDark),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text("Tag name", fontSize = 16.sp, color = TextHint)
                        inner()
                    },
                )
                if (name.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = TextHint,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { name = "" },
                            ),
                    )
                }
            }
        }

        Text(
            text = "Tag colour",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppConfig.Folder.COLORS.forEachIndexed { index, color ->
                TagColorSwatch(
                    color = color,
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    enabled = canCreate,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onCreate(trimmed, AppConfig.Folder.COLORS[selectedIndex].toHex()) },
                ),
            shape = RoundedCornerShape(50),
            color = if (canCreate) BackgroundColors.Primary.default.current() else BackgroundColors.Interactive.disabled.current(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Create new tag", color = TextColors.Inverse.default.current(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onCancel,
                ),
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.dp, TextDark),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun CreateTagSheetPreview() {
    AppTheme {
        Surface(color = SheetBg) {
            CreateTagContent(onCreate = { _, _ -> }, onCancel = {})
        }
    }
}
