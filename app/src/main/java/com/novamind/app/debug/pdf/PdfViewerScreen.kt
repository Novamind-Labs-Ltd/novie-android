package com.novamind.app.debug.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.novamind.app.R
import com.novamind.app.common.pdf.PdfPageCache
import com.novamind.app.common.pdf.PdfRenderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val Bg = Color(0xFF2A2A2D)        // 阅读器深色底，突出页面
private val PageBg = Color(0xFFFFFFFF)
private val OnDark = Color(0xFFF2F2F2)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFE07A7A)

private const val MAX_SCALE = 5f
private const val MIN_SCALE = 1f
private const val DOUBLE_TAP_SCALE = 2.5f

// ─── Route（有状态：文件选择 + 渲染）────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerRoute(onBack: () -> Unit, initialPath: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetWidth = context.resources.displayMetrics.widthPixels

    var fileName by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<PdfRenderSession?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 关闭旧 session（切换文件或离开界面时），释放渲染器与文件描述符。
    // 捕获本次的 session 值：key 变化时 onDispose 关闭的应是「上一个」session，而非已更新的新值。
    DisposableEffect(session) {
        val current = session
        onDispose { current?.close() }
    }

    // 以指定文件路径打开（从笔记的 PDF 块点开时走这里），跳过选择器
    LaunchedEffect(initialPath) {
        if (initialPath != null) {
            loading = true; error = null; session = null; pageCount = 0
            val f = File(initialPath)
            fileName = f.name
            val s = withContext(Dispatchers.IO) { PdfRenderSession.open(f, targetWidth) }
            if (s == null) error = "无法渲染该 PDF"
            else { session = s; pageCount = s.pageCount }
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true; error = null; session = null; pageCount = 0
                runCatching {
                    fileName = withContext(Dispatchers.IO) { queryName(context, uri) }
                    withContext(Dispatchers.IO) { openSession(context, uri, targetWidth) }
                }.onSuccess { s ->
                    if (s == null) error = "无法渲染该 PDF（空文档或格式不支持）"
                    else { session = s; pageCount = s.pageCount }
                }.onFailure { error = it.message ?: "打开 PDF 失败" }
                loading = false
            }
        }
    }
    val openPicker = { picker.launch(arrayOf("application/pdf")) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(fileName ?: "PDF 阅读器", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "返回", tint = OnDark)
                    }
                },
                actions = {
                    Text(
                        "选择文件",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Accent)
                            .clickable(onClick = openPicker)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = OnDark),
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                error != null -> Text(error!!, color = Danger, modifier = Modifier.padding(20.dp))
                session == null || pageCount == 0 -> EmptyState(onPick = openPicker)
                else -> PdfReader(
                    session = session!!,
                    pageCount = pageCount,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ─── 分页 + 缩放阅读器 ──────────────────────────────────────────────────────

/**
 * 可复用的 PDF 阅读器核心：横向翻页 + 捏合/双击缩放 + 底部翻页/缩放/跳页控制条。
 * 不含 Scaffold/顶栏/文件选择，故既能全屏(fillMaxSize)，也能内联(限定高度)嵌入。
 */
@Composable
internal fun PdfReader(
    session: PdfRenderSession,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val scope = rememberCoroutineScope()
    var showJump by remember { mutableStateOf(false) }

    // 按需渲染 + LRU 缓存：内存峰值 ≈ 容量 × 单页。容量 5 ≥ Pager 同屏页数(current ± 1)，
    // 保证正在显示的页不会被回收。离开界面时回收全部位图。
    val cache = remember(session) { PdfPageCache(session, capacity = 5) }
    DisposableEffect(cache) {
        onDispose { cache.clear() }
    }

    // 缩放/平移状态（按当前页生效，翻页时重置）
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomAnimJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        zoomAnimJob?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = ((s - 1f) * boxSize.width / 2f).coerceAtLeast(0f)
        val maxY = ((s - 1f) * boxSize.height / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            // 预组合左右各一页，提前渲染相邻页，滑动到位即可显示
            beyondViewportPageCount = 1,
            // 放大时禁用横向翻页，让横向拖动用于平移；回到 1x 才能翻页
            userScrollEnabled = scale <= 1.01f,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it },
                contentAlignment = Alignment.Center,
            ) {
                // 仅当前页应用缩放/平移状态，其余页保持 1x
                val isCurrent = pageIndex == pagerState.currentPage
                val pScale = if (isCurrent) scale else 1f
                val pOffset = if (isCurrent) offset else Offset.Zero

                // 按需渲染该页：进入组合时取（缓存命中或渲染），渲染中显示进度
                var bmp by remember(pageIndex, session) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(pageIndex, session) {
                    bmp = cache.get(pageIndex)
                }

                val pageBmp = bmp
                if (pageBmp == null) {
                    CircularProgressIndicator(color = Accent)
                    return@Box
                }

                Image(
                    bitmap = pageBmp.asImageBitmap(),
                    contentDescription = "第 ${pageIndex + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = pScale,
                            scaleY = pScale,
                            translationX = pOffset.x,
                            translationY = pOffset.y,
                        )
                        // 双击：在 1x 与 2.5x 间补间切换
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (!isCurrent) return@detectTapGestures
                                    val targetScale = if (scale > 1f) MIN_SCALE else DOUBLE_TAP_SCALE
                                    val startScale = scale
                                    val startOffset = offset
                                    val targetOffset =
                                        if (targetScale > 1f) clamp(startOffset, targetScale) else Offset.Zero
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
                        // 双指捏合缩放 + 放大后拖动平移；单指未放大时不消费 → 交给 Pager 翻页
                        .pointerInput(pageIndex) {
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
                                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
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
                                    val target = clamp(start, scale)
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
                )
            }
        }

        // 底部控制条：上一页 / 缩小 / 页码 / 放大 / 下一页（均为屏内按钮，非弹窗）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC1A1A1A))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 上一页：到首页时置灰禁用
            PagerButton("‹", enabled = pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
            // 缩小：到最小倍数时置灰
            ZoomButton("−", enabled = scale > MIN_SCALE + 0.01f) {
                scale = (scale / 1.5f).coerceAtLeast(MIN_SCALE)
                if (scale <= 1f) offset = Offset.Zero else offset = clamp(offset, scale)
            }
            // 点击页码 → 跳转到指定页
            Text(
                "${pagerState.currentPage + 1} / $pageCount",
                color = OnDark,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { showJump = true }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            // 放大：到最大倍数时置灰
            ZoomButton("+", enabled = scale < MAX_SCALE - 0.01f) {
                scale = (scale * 1.5f).coerceAtMost(MAX_SCALE)
            }
            // 下一页：到末页时置灰禁用
            PagerButton("›", enabled = pagerState.currentPage < pageCount - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }

        // 缩放百分比：放大时在顶部居中淡入显示（1x 时隐藏）
        AnimatedVisibility(
            visible = scale > 1.01f,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        ) {
            Text(
                "${(scale * 100).roundToInt()}%",
                color = OnDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC1A1A1A))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        // 跳转到指定页
        if (showJump) {
            var input by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showJump = false },
                title = { Text("跳转到页") },
                text = {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { v -> input = v.filter { it.isDigit() }.take(6) },
                        singleLine = true,
                        label = { Text("页码 (1 - $pageCount)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        input.toIntOrNull()?.let { n ->
                            val target = (n - 1).coerceIn(0, pageCount - 1)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        showJump = false
                    }) { Text("跳转") }
                },
                dismissButton = {
                    TextButton(onClick = { showJump = false }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun ZoomButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(if (enabled) 0x33FFFFFF else 0x14FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0x66FFFFFF),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// 翻页按钮：禁用时置灰且不可点击
@Composable
private fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(if (enabled) 0x33FFFFFF else 0x14FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0x66FFFFFF),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("选择一个 PDF 文件，逐页阅读（支持翻页、捏合/双击缩放）。", color = OnDark, fontSize = 14.sp)
        Text(
            "选择 PDF 文件",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Accent)
                .clickable(onClick = onPick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

// ─── 渲染 / 工具 ────────────────────────────────────────────────────────────

private fun openSession(context: Context, uri: Uri, targetWidth: Int): PdfRenderSession? {
    // 复制到 cache 文件，保证 PdfRenderer 拿到可随机读取(seek)的描述符；session 会一直持有它按需渲染。
    val cacheFile = File(context.cacheDir, "debug_pdf_preview.pdf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    return PdfRenderSession.open(cacheFile, targetWidth)
}

private fun queryName(context: Context, uri: Uri): String {
    var name = "document.pdf"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
    }
    return name
}
