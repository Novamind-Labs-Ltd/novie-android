package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出删除按钮（点击触发 [onDelete]）；长按可拖拽排序（[onReorderStart]/[onReorderDrag]/[onReorderEnd]）。
 * 两个手势同处前景元素：水平滑动→删除，长按后纵向拖动→排序，互不冲突。
 */
@Composable
internal fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onReorderStart: () -> Unit = {},
    onReorderDrag: (Float) -> Unit = {},
    onReorderEnd: () -> Unit = {},
    onReorderCancel: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { 60.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        // 背后：右侧深色圆形删除按钮
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BackgroundColors.Primary.default.current())
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete",
                    tint = Palette.white,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 前景：可左滑的内容 + 长按拖拽排序（两个 pointerInput 并存于同一元素）
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(newX) }
                        },
                        onDragEnd = {
                            // 过半则吸附到展开，否则收回
                            val target = if (offsetX.value < -revealPx / 2) -revealPx else 0f
                            scope.launch { offsetX.animateTo(target) }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onReorderStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onReorderDrag(dragAmount.y)
                        },
                        onDragEnd = { onReorderEnd() },
                        onDragCancel = { onReorderCancel() },
                    )
                },
        ) { content() }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Tag · 左滑删除容器")
@Composable
private fun SwipeToDeleteRowPreview() {
    AppTheme {
        SwipeToDeleteRow(onDelete = {}) {
            Text(
                "Swipe me left",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(16.dp),
            )
        }
    }
}
