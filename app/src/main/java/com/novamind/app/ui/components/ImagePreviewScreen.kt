package com.novamind.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.novamind.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

// 公共组件自带配色，避免依赖各 feature 内部颜色常量
private val PreviewBg = Color(0xFFF0EFEA)
private val PreviewText = Color(0xFF1A1A1A)
private val PreviewBtnBg = Color(0xFFFFFFFF)

/**
 * 图片预览全屏页：左右滑动翻页（[HorizontalPager]）、双指缩放 / 双击放大、下拉关闭，
 * 可选删除当前图片。通用组件，笔记编辑、Ask Novie 等场景均可复用。
 *
 * @param paths 全部图片的有序路径
 * @param initialIndex 进入时显示的图片下标
 * @param onBack 关闭预览
 * @param onDelete 删除第 index 张图片；为 null 时隐藏删除按钮
 * @param deleteTitle / deleteMessage / deleteConfirmLabel 删除二次确认文案（按场景定制）
 */
@Composable
fun ImagePreviewScreen(
    paths: List<String>,
    initialIndex: Int,
    onBack: () -> Unit,
    onDelete: ((Int) -> Unit)? = null,
    deleteTitle: String = "Delete image?",
    deleteMessage: String = "This will remove the image.",
    deleteConfirmLabel: String = "Delete",
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

    // 缩放 / 平移状态（当前页）。手势期间直接同步更新 state（最跟手、无协程开销），
    // 双击用动画过渡。offset 始终夹紧在边界内，避免拖出空白。
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var zoomAnimJob by remember { mutableStateOf<Job?>(null) }

    // 把平移夹紧在「放大后图片仍覆盖视口」的范围内：|t| ≤ 容器尺寸 × (scale−1) / 2
    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (containerSize.width * (s - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    // 翻页时复位
    LaunchedEffect(pagerState.currentPage) {
        zoomAnimJob?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    // 左右翻页的边界橡皮筋：首/末页继续外滑时，pager 整体随手平移，松手弹簧回弹
    val overscrollX = remember { mutableFloatStateOf(0f) }
    val scaleState = rememberUpdatedState(scale)
    val containerWidthState = rememberUpdatedState(containerSize.width)
    val edgeOverscroll = remember {
        object : NestedScrollConnection {
            private val resist = 0.4f   // 阻尼：越界位移按比例缩小，越拖越沉
            // 先消费用于「回拉」抵消已有 overscroll 的拖动量
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val cur = overscrollX.floatValue
                if (cur == 0f) return Offset.Zero
                val dx = available.x
                // 仅当拖动方向与 overscroll 相反（回拉）时抵消
                if ((cur > 0f && dx < 0f) || (cur < 0f && dx > 0f)) {
                    val inputToZero = -cur / resist
                    return if ((cur > 0f && dx <= inputToZero) || (cur < 0f && dx >= inputToZero)) {
                        overscrollX.floatValue = 0f
                        Offset(inputToZero, 0f)   // 抵消到 0，剩余交还 pager
                    } else {
                        overscrollX.floatValue = cur + dx * resist
                        Offset(dx, 0f)            // 全部用于回拉
                    }
                }
                return Offset.Zero
            }
            // pager 在边界消费不掉的滑动量 → 转成 overscroll 平移。
            // 仅在「真正到首/末页」时捕获，避免快速滑动时 pager 瞬时吃不下的残余被误当作越界（导致回弹到上一页）。
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
                if (scaleState.value > 1f) return Offset.Zero   // 放大时由图片平移逻辑处理
                val atStart = !pagerState.canScrollBackward
                val atEnd = !pagerState.canScrollForward
                val overscrolling = (available.x > 0f && atStart) || (available.x < 0f && atEnd)
                if (!overscrolling) return Offset.Zero          // 非边界的瞬时残余：不消费，交还 pager 正常翻页
                val maxOver = containerWidthState.value * 0.35f
                overscrollX.floatValue =
                    (overscrollX.floatValue + available.x * resist).coerceIn(-maxOver, maxOver)
                return Offset(available.x, 0f)
            }
            // 松手：异步弹簧回弹归零，立即返回（不阻塞 pager 自身的 fling/翻页）
            override suspend fun onPreFling(available: Velocity): Velocity {
                val cur = overscrollX.floatValue
                if (cur != 0f) {
                    scope.launch {
                        animate(
                            cur, 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) { v, _ -> overscrollX.floatValue = v }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    // 沉浸模式：单击切换。开启时背景变黑、隐藏顶栏（顶部/底部留黑边）
    var immersive by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (immersive) Color.Black else PreviewBg,
        animationSpec = tween(220),
        label = "previewBg",
    )

    // 下拉关闭：竖直拖拽距离（仅向下）。图片随之缩小、背景渐隐，露出下层页面（近共享元素）
    var dragDownY by remember { mutableFloatStateOf(0f) }
    val dismissDistance = (containerSize.height.takeIf { it > 0 } ?: 1).toFloat()
    val dismissProgress = (dragDownY / dismissDistance).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor.copy(alpha = 1f - dismissProgress * 0.85f))
            // 下拉关闭手势：未放大时生效；向下拖动 → 关闭，向上忽略
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (scale <= 1f) {
                            dragDownY = (dragDownY + dragAmount).coerceAtLeast(0f)
                            if (dragDownY > 0f) change.consume()
                        }
                    },
                    onDragEnd = {
                        if (dragDownY > dismissDistance * 0.18f) {
                            // 超过阈值：继续缩小并关闭
                            scope.launch {
                                animate(dragDownY, dismissDistance, animationSpec = tween(200)) { v, _ -> dragDownY = v }
                                onBack()
                            }
                        } else {
                            // 未达阈值：弹回
                            scope.launch {
                                animate(dragDownY, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragDownY = v }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { animate(dragDownY, 0f, animationSpec = tween(150)) { v, _ -> dragDownY = v } }
                    },
                )
            },
    ) {
        // 图片数 ≥ 2 且未放大时才允许左右翻页；放大后水平拖动用于平移
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = paths.size >= 2 && scale <= 1f,
            beyondViewportPageCount = 1,   // 预组合左右各一页，提前加载，避免滑动白屏
            pageSpacing = 6.dp,           // 图片之间的间隙
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(edgeOverscroll)
                .graphicsLayer {
                    translationX = overscrollX.floatValue
                    // 下拉关闭：整体下移 + 缩小（最多缩到 0.6×）
                    translationY = dragDownY
                    val s = 1f - dismissProgress * 0.4f
                    scaleX = s
                    scaleY = s
                },
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            val pageScale = if (isCurrent) scale else 1f
            val pageOffset = if (isCurrent) offset else Offset.Zero

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { if (isCurrent) containerSize = it }
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = {
                                if (isCurrent) immersive = !immersive
                            },
                            onDoubleTap = {
                                if (!isCurrent) return@detectTapGestures
                                // 双击：在 1× 与 2.5× 间补间切换，不突变
                                val targetScale = if (scale > 1f) 1f else 2.5f
                                val startScale = scale
                                val startOffset = offset
                                val targetOffset =
                                    if (targetScale > 1f) clampOffset(startOffset, targetScale) else Offset.Zero
                                zoomAnimJob?.cancel()
                                zoomAnimJob = scope.launch {
                                    animate(0f, 1f, animationSpec = tween(250)) { t, _ ->
                                        scale = lerp(startScale, targetScale, t)
                                        offset = lerp(startOffset, targetOffset, t)
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(page) {
                        // 自定义手势：仅在「双指捏合」或「已放大」时才消费事件做缩放/平移；
                        // 单指且未放大时不消费 → 交给 HorizontalPager 做左右翻页。
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            zoomAnimJob?.cancel()   // 触摸开始即打断进行中的动画
                            var moved = false
                            do {
                                val event = awaitPointerEvent()
                                if (!isCurrent) continue
                                val pointers = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (pointers >= 2 || scale > 1f) {
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    // 拖拽期间直接同步更新、允许越界（跟手、不卡顿）
                                    scale = newScale
                                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                                    moved = true
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            // 松手回弹：若越界，用弹簧动画把 offset 滚回合法边界
                            if (moved && scale > 1f) {
                                val start = offset
                                val target = clampOffset(start, scale)
                                if (start != target) {
                                    zoomAnimJob = scope.launch {
                                        animate(
                                            0f, 1f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        ) { t, _ -> offset = lerp(start, target, t) }
                                    }
                                }
                            }
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
            visible = !immersive && dragDownY == 0f,
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
                    color = PreviewText,
                )
                if (onDelete != null) {
                    CircleIconButton(R.drawable.ic_delete, "Delete", onClick = {
                        showDeleteConfirm = true
                    })
                } else {
                    // 占位，保持标题居中
                    Box(modifier = Modifier.size(44.dp))
                }
            }
        }

        // 删除二次确认
        if (showDeleteConfirm && onDelete != null) {
            DeleteConfirmSheet(
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete(pagerState.currentPage)
                },
                onDismiss = { showDeleteConfirm = false },
                title = deleteTitle,
                message = deleteMessage,
                confirmLabel = deleteConfirmLabel,
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
    Surface(shape = CircleShape, color = PreviewBtnBg, shadowElevation = 2.dp) {
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
                tint = PreviewText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────
// 注：预览中图片路径为占位，AsyncImage 不会真正加载；主要展示顶栏与背景等外框。

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, showSystemUi = true)
@Composable
private fun ImagePreviewScreenPreview() {
    com.novamind.app.ui.theme.AppTheme {
        ImagePreviewScreen(
            paths = listOf("/sample/a.jpg", "/sample/b.jpg"),
            initialIndex = 0,
            onBack = {},
            onDelete = {},
        )
    }
}
