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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Bg = Color(0xFFF4F4F5)
private val PageBg = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

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
                loading = true
                error = null
                pages = emptyList()
                runCatching {
                    fileName = withContext(Dispatchers.IO) { queryName(context, uri) }
                    withContext(Dispatchers.IO) { renderPdf(context, uri, targetWidth) }
                }.onSuccess {
                    pages = it
                    if (it.isEmpty()) error = "无法渲染该 PDF（空文档或格式不支持）"
                }.onFailure {
                    error = it.message ?: "打开 PDF 失败"
                }
                loading = false
            }
        }
    }

    val openPicker = { picker.launch(arrayOf("application/pdf")) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        fileName ?: "PDF 预览",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "返回", tint = TextMain)
                    }
                },
                actions = {
                    Text(
                        "选择文件",
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Accent.copy(alpha = 0.12f))
                            .clickable(onClick = openPicker)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain),
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

                error != null -> Text(
                    error!!,
                    color = Danger,
                    modifier = Modifier.padding(20.dp),
                )

                pages.isEmpty() -> EmptyState(onPick = openPicker)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(pages) { index, bmp ->
                        Column {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "第 ${index + 1} 页",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PageBg),
                            )
                            Text(
                                "第 ${index + 1} / ${pages.size} 页",
                                fontSize = 11.sp,
                                color = TextSub,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
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
        Text("选择一个 PDF 文件，用系统 PdfRenderer 逐页渲染预览。", color = TextSub, fontSize = 14.sp)
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

// ─── 渲染逻辑 ────────────────────────────────────────────────────────────────

/**
 * 把所选 PDF 逐页渲染为 [Bitmap]。
 *
 * 实现要点：
 * - 先把内容 Uri 复制到 cache 文件，保证 [PdfRenderer] 拿到可随机读取(seek)的描述符
 *   （部分 content provider 的 fd 不可 seek，会导致 PdfRenderer 抛异常）。
 * - PdfRenderer 同一时刻只能打开一页，逐页渲染并关闭。
 * - 渲染前用白色填充位图，避免透明 PDF 渲染出黑底。
 */
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
