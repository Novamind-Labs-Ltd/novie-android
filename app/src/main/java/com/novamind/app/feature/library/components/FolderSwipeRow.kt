package com.novamind.app.feature.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val ButtonSize = 44.dp
private val ButtonGap = 12.dp
private val EdgePad = 8.dp
// 完全展开时内容左移距离：两枚按钮 + 间距 + 右侧留白 + 与内容的间隙
private val RevealWidth = ButtonSize * 2 + ButtonGap + EdgePad + ButtonGap

/**
 * 文件夹行左滑交互（Figma 879-26226）：整行向左拖拽，露出右侧「编辑(绿)/删除(红)」两枚圆形按钮。
 * 与外层长按拖拽排序共存（长按=排序、横向拖=露出）。[content] 收到 (isOpen, close)：
 * 展开时点击整行应先收起而非进入文件夹。
 */
@Composable
internal fun FolderSwipeRow(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { RevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { offsetX.animateTo(0f) }
        onOpenChange(false)
    }
    val isOpen = offsetX.value < -revealPx / 2f

    // 外部要求关闭（打开了其它行）时自动收起本行——保证同时最多一行展开
    LaunchedEffect(open) {
        if (!open && offsetX.value != 0f) offsetX.animateTo(0f)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 背景操作层：右对齐、垂直居中，随内容左移逐渐露出
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = EdgePad),
            horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionCircle(
                bg = ButtonColors.Success.background.current(),
                tint = ButtonColors.Success.text.current(),
                iconRes = R.drawable.ic_pencil_line,
                desc = "Rename",
                onClick = { close(); onEdit() },
            )
            ActionCircle(
                // Figma：删除按钮为深色(炭黑)圆钮 + 白色垃圾桶（非红色）
                bg = IconColors.Default.default.current(),
                tint = IconColors.Default.onDark.current(),
                iconRes = R.drawable.ic_trash_line,
                desc = "Delete",
                onClick = { close(); onDelete() },
            )
        }

        // 前景内容层：可横向拖拽，覆盖背景按钮（收起态完全遮住）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            val target = (offsetX.value + drag).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(target) }
                        },
                        onDragEnd = {
                            val opened = offsetX.value < -revealPx / 2f
                            scope.launch { offsetX.animateTo(if (opened) -revealPx else 0f) }
                            onOpenChange(opened)   // 通知宿主：本行成为唯一展开行 / 收起
                        },
                    )
                },
        ) {
            content(isOpen, close)
        }
    }
}

@Composable
private fun ActionCircle(bg: Color, tint: Color, iconRes: Int, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
