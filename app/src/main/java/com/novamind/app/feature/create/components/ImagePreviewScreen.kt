package com.novamind.app.feature.create.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.novamind.app.R
import kotlinx.coroutines.launch
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

    // 进入时预取初始页及左右各一张（共 3 张）到 Coil 内存缓存，避免首次滑动白屏
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val center = initialIndex.coerceIn(0, paths.lastIndex)
        listOf(center - 1, center, center + 1)
            .filter { it in paths.indices }
            .forEach { i ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context).data(File(paths[i])).build()
                )
            }
    }

    // 删除确认弹窗
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 缩放 / 平移状态（当前页）：用 Animatable 以便双击带补间动画；捏合用 snapTo 即时跟手
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = scaleAnim.value
    val scope = rememberCoroutineScope()
    // 翻页时平滑复位
    LaunchedEffect(pagerState.currentPage) {
        scaleAnim.animateTo(1f, tween(200))
        offsetAnim.snapTo(Offset.Zero)
    }

    // 沉浸模式：单击切换。开启时背景变黑、隐藏顶栏（顶部/底部留黑边）
    var immersive by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (immersive) Color.Black else BgPage,
        animationSpec = tween(220),
        label = "previewBg",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        // 图片数 ≥ 2 且未放大时才允许左右翻页；放大后水平拖动用于平移
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = paths.size >= 2 && scale <= 1f,
            beyondViewportPageCount = 1,   // 预组合左右各一页，提前加载，避免滑动白屏
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            val pageScale = if (isCurrent) scaleAnim.value else 1f
            val pageOffset = if (isCurrent) offsetAnim.value else Offset.Zero

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = {
                                if (isCurrent) immersive = !immersive
                            },
                            onDoubleTap = {
                                if (!isCurrent) return@detectTapGestures
                                // 双击：在 1× 与 2.5× 间补间切换，不突变
                                val target = if (scaleAnim.value > 1f) 1f else 2.5f
                                scope.launch {
                                    if (target == 1f) {
                                        // 缩小：缩放与平移同步动画回位
                                        launch { offsetAnim.animateTo(Offset.Zero, tween(250)) }
                                    }
                                    scaleAnim.animateTo(target, tween(250))
                                }
                            },
                        )
                    }
                    .pointerInput(page) {
                        // 自定义手势：仅在「双指捏合」或「已放大」时才消费事件做缩放/平移；
                        // 单指且未放大时不消费 → 交给 HorizontalPager 做左右翻页。
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (!isCurrent) continue
                                val pointers = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (pointers >= 2 || scaleAnim.value > 1f) {
                                    val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                                    val newOffset = if (newScale > 1f) offsetAnim.value + pan else Offset.Zero
                                    // 捏合即时跟手：snapTo（无补间）
                                    scope.launch { scaleAnim.snapTo(newScale) }
                                    scope.launch { offsetAnim.snapTo(newOffset) }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
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

        // 顶栏：返回 | N of M | 删除（沉浸模式下淡出隐藏）
        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
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
