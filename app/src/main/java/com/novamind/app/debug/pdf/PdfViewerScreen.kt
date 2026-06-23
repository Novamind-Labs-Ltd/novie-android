package com.novamind.app.debug.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
fun PdfViewerRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetWidth = context.resources.displayMetrics.widthPixels

    var fileName by remember { mutableStateOf<String?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true; error = null; pages = emptyList()
                runCatching {
                    fileName = withContext(Dispatchers.IO) { queryName(context, uri) }
                    withContext(Dispatchers.IO) { renderPdf(context, uri, targetWidth) }
                }.onSuccess {
                    pages = it
                    if (it.isEmpty()) error = "无法渲染该 PDF（空文档或格式不支持）"
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
                pages.isEmpty() -> EmptyState(onPick = openPicker)
                else -> PdfPager(pages = pages)
            }
        }
    }
}

// ─── 分页 + 缩放阅读器 ──────────────────────────────────────────────────────

@Composable
private fun PdfPager(pages: List<Bitmap>) {
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 缩放/平移状态（按当前页生效，翻页时重置）
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = ((s - 1f) * boxSize.width / 2f).coerceAtLeast(0f)
        val maxY = ((s - 1f) * boxSize.height / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
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

                Image(
                    bitmap = pages[pageIndex].asImageBitmap(),
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
                        // 双击：放大到 2.5x / 还原
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                    else scale = DOUBLE_TAP_SCALE
                                },
                            )
                        }
                        // 放大后单指拖动平移；1x 时不拦截，交给 Pager 翻页
                        .then(
                            if (isCurrent && scale > 1f) Modifier.pointerInput(pageIndex) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offset = clamp(offset + dragAmount, scale)
                                }
                            } else Modifier,
                        ),
                )
            }
        }

        // 底部控制条：缩小 / 页码 / 放大
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
            ZoomButton("−") {
                scale = (scale / 1.5f).coerceAtLeast(MIN_SCALE)
                if (scale <= 1f) offset = Offset.Zero else offset = clamp(offset, scale)
            }
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                color = OnDark,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            ZoomButton("+") {
                scale = (scale * 1.5f).coerceAtMost(MAX_SCALE)
            }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

private fun renderPdf(context: Context, uri: Uri, targetWidth: Int): List<Bitmap> {
    // 复制到 cache 文件，保证 PdfRenderer 拿到可随机读取(seek)的描述符，再交给共享渲染器。
    val cacheFile = File(context.cacheDir, "debug_pdf_preview.pdf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
    } ?: return emptyList()
    return PdfPageRenderer.render(cacheFile, targetWidth)
}

private fun queryName(context: Context, uri: Uri): String {
    var name = "document.pdf"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
    }
    return name
}
