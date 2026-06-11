package com.novamind.app.feature.create.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import java.io.File

/**
 * 图片预览全屏页：左右滑动翻页（[HorizontalPager]）、双指缩放 / 双击放大、删除当前图片。
 *
 * @param paths 笔记内全部图片的有序路径
 * @param initialIndex 进入时显示的图片下标
 * @param onDelete 删除第 index 张图片（由上层从编辑器移除对应块）
 * @param onBack 关闭预览
 */
@Composable
fun ImagePreviewScreen(
    paths: List<String>,
    initialIndex: Int,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit,
) {
    if (paths.isEmpty()) {
        // 没有可显示的图片（例如删到空）：直接关闭
        LaunchedEffect(Unit) { onBack() }
        return
    }

    BackHandler(onBack = onBack)

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, paths.lastIndex),
        pageCount = { paths.size },
    )

    // 删除确认弹窗
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 缩放 / 平移状态（当前页），翻页时复位
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage),
    ) {
        // 图片数 ≥ 2 且未放大时才允许左右翻页；放大后水平拖动用于平移
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = paths.size >= 2 && scale <= 1f,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            val pageScale = if (isCurrent) scale else 1f
            val pageOffset = if (isCurrent) offset else Offset.Zero

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isCurrent) return@detectTapGestures
                                if (scale > 1f) {
                                    scale = 1f; offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (!isCurrent) return@detectTransformGestures
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(paths[page]),
                    contentDescription = "Image ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pageScale
                            scaleY = pageScale
                            translationX = pageOffset.x
                            translationY = pageOffset.y
                        },
                )
            }
        }

        // 顶栏：返回 | N of M | 删除
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(R.drawable.ic_arrow_back, "Back", onClick = onBack)
            Text(
                text = "${pagerState.currentPage + 1} of ${paths.size}",
                fontSize = 16.sp,
                color = ColorTextTitle,
            )
            CircleIconButton(R.drawable.ic_delete, "Delete", onClick = {
                showDeleteConfirm = true
            })
        }

        // 删除二次确认
        if (showDeleteConfirm) {
            DeleteConfirmSheet(
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete(pagerState.currentPage)
                },
                onDismiss = { showDeleteConfirm = false },
                title = "Delete image?",
                message = "This will remove the image from the note.",
                confirmLabel = "Delete",
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(shape = CircleShape, color = ColorChipBg, shadowElevation = 2.dp) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick,
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
}
