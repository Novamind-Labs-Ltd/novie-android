package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.TextBlock
import java.io.File

/**
 * 图文正文编辑器：按顺序渲染文本块（可编辑、支持加粗/斜体）与图片块（可删除）。
 * 任何文本/结构变化都通过 [onContentChanged] 通知上层去同步与保存。
 */
@Composable
fun NoteContentEditor(
    state: NoteEditorState,
    onContentChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .fillMaxSize()                 // 填满可用高度，使下方空白区也能接收点击
            .verticalScroll(rememberScrollState())
            // 点击正文空白处（非文本/图片块本身）时，聚焦最后一个文本块并调起键盘
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    state.focusLastTextBlock()
                    keyboard?.show()   // 焦点未变化（键盘曾被收起）时也能重新弹出
                },
            ),
    ) {
        val singleEmpty = state.blocks.size == 1 &&
            (state.blocks.first() as? TextBlock)?.rich?.plainText?.isEmpty() == true

        state.blocks.forEach { block ->
            key(block.id) {
                when (block) {
                    is TextBlock -> TextBlockField(
                        block = block,
                        showPlaceholder = singleEmpty,
                        onFocused = { state.onTextFocused(block.id) },
                        onChanged = onContentChanged,
                    )

                    is ImageBlock -> ImageBlockView(
                        block = block,
                        onDelete = {
                            state.removeImage(block.id)
                            onContentChanged()
                        },
                    )
                }
            }
        }

        // 底部留白 + 导航栏高度，避免最后一块内容被导航栏遮挡
        Spacer(modifier = Modifier.height(70.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun TextBlockField(
    block: TextBlock,
    showPlaceholder: Boolean,
    onFocused: () -> Unit,
    onChanged: () -> Unit,
) {
    BasicTextField(
        value = block.rich.value,
        onValueChange = {
            block.rich.onValueChange(it)
            onChanged()
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(block.focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = ColorTextTitle,
            lineHeight = 26.sp,
        ),
        cursorBrush = SolidColor(ColorTextTitle),
        decorationBox = { inner ->
            if (showPlaceholder && block.rich.value.text.isEmpty()) {
                Text("Type here...", fontSize = 16.sp, color = ColorTextHint)
            }
            inner()
        },
    )
}

@Composable
private fun ImageBlockView(
    block: ImageBlock,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = File(block.path),
            contentDescription = "Note image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8E7E2)),
        )
        // 删除按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 18.sp)
        }
    }
}
