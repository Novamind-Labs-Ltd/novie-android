package com.novamind.app.debug.markdown

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFFFBFAF7)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

// ─── Route（有状态：文件选择 + 读取；渲染交给 mikepenz Markdown）───────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownPreviewRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = withContext(Dispatchers.IO) { queryName(context, uri) }
                    val text = withContext(Dispatchers.IO) { readText(context, uri) }
                    fileName = name
                    content = text
                    error = null
                }.onFailure {
                    error = it.message ?: "读取文件失败"
                    content = null
                }
            }
        }
    }

    // md 的 MIME 在各文件提供方很不统一（常被报成 text/plain 或 application/octet-stream），
    // 用 */* 保证 .md 一定可选；选中后按 UTF-8 文本读取。
    val openPicker = { picker.launch(arrayOf("*/*")) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(fileName ?: "Markdown 阅读器", fontWeight = FontWeight.SemiBold, maxLines = 1) },
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
                error != null -> Text(
                    "读取失败：$error",
                    color = Danger,
                    modifier = Modifier.padding(20.dp),
                )

                content == null -> EmptyState(onPick = openPicker)

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    // mikepenz multiplatform-markdown-renderer（m3 变体，套用 Material3 主题）
                    Markdown(content = content!!)
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
        Text("选择一个 .md 文件，使用 Markdown 渲染器预览。", color = TextSub, fontSize = 14.sp)
        Text(
            "选择 Markdown 文件",
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

// ─── 文件读取 ────────────────────────────────────────────────────────────────

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().toString(Charsets.UTF_8)
    } ?: ""

private fun queryName(context: Context, uri: Uri): String {
    var name = "untitled.md"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
    }
    return name
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7)
@Composable
private fun MarkdownPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp)) {
            Markdown(
                content = """
                # 标题一
                ## 标题二
                带 **加粗**、*斜体* 和 `行内代码` 的文字，还有 [链接](https://example.com)。

                > 引用块

                - 无序项 A
                - 无序项 B

                1. 有序项一
                2. 有序项二

                ```kotlin
                fun hello() = println("hi")
                ```

                | 列 A | 列 B |
                | --- | --- |
                | 1 | 2 |
                """.trimIndent()
            )
        }
    }
}
