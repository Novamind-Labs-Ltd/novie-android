package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.novamind.app.feature.create.editor.MarkdownBlock
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.ui.theme.novieMarkdownTypography

@Composable
internal fun MarkdownBlockView(
    block: MarkdownBlock,
    readOnly: Boolean,
    onContentChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    // 编辑/预览切换；编辑时直接修改原始 markdown 文本，退出编辑回到渲染视图
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.id) { mutableStateOf(block.content) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 头部：标识 + 编辑/完成 + 删除
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Markdown", fontSize = 12.sp, color = TextColors.Primary.secondary.current())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 录音等只读场景下隐藏「编辑」入口
                        if (!readOnly) {
                            Text(
                                text = if (editing) "Done" else "Edit",
                                fontSize = 13.sp,
                                color = IconColors.Brand.default.current(),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            if (editing) onContentChange(draft)
                                            editing = !editing
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false),
                                    onClick = onDelete,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = TextColors.Primary.secondary.current(), fontSize = 18.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (editing) {
                    // 编辑原始 markdown 文本（等宽字体），实时写回块内容
                    BasicTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            onContentChange(it)
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextColors.Primary.default.current(),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(IconColors.Brand.default.current()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F3F0))
                            .padding(12.dp),
                    )
                } else {
                    // 渲染视图（m3 变体，跟随 Material3 主题）
                    Markdown(
                        content = block.content,
                        typography = novieMarkdownTypography(),
                    )
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Create · MarkdownBlockView")
@Composable
private fun MarkdownBlockViewPreview() {
    AppTheme {
        MarkdownBlockView(
            block = MarkdownBlock(content = "# Title\n\nBody text **bold**"),
            readOnly = false,
            onContentChange = {},
            onDelete = {},
        )
    }
}
