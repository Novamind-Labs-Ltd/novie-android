package com.novamind.app.debug.files

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFFF4F4F5)
private val Card = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)

/**
 * 调试用文件目录浏览器：列出指定目录下的文件/子目录，显示绝对路径与大小，
 * 点子目录进入、点文件分享导出。仅供 Debug 工具箱使用。
 */
class FileBrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rootPath = intent.getStringExtra(EXTRA_PATH) ?: filesDir.absolutePath
        setContent {
            AppTheme {
                FileBrowserRoute(rootPath = rootPath, onFinish = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"

        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, FileBrowserActivity::class.java).putExtra(EXTRA_PATH, path),
            )
        }
    }
}

private data class Entry(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserRoute(rootPath: String, onFinish: () -> Unit) {
    val context = LocalContext.current
    val root = remember(rootPath) { File(rootPath) }
    var currentDir by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var totalSize by remember { mutableStateOf(0L) }

    LaunchedEffect(currentDir) {
        val (list, total) = withContext(Dispatchers.IO) {
            if (!currentDir.exists()) emptyList<Entry>() to 0L
            else {
                val files = currentDir.listFiles()?.toList().orEmpty()
                val mapped = files.map {
                    Entry(
                        file = it,
                        isDir = it.isDirectory,
                        size = if (it.isDirectory) 0L else it.length(),
                        lastModified = it.lastModified(),
                        childCount = if (it.isDirectory) it.listFiles()?.size ?: 0 else 0,
                    )
                }.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.file.name.lowercase() })
                val sum = files.filter { it.isFile }.sumOf { it.length() }
                mapped to sum
            }
        }
        entries = list
        totalSize = total
    }

    // 不在根目录时返回上一级；在根目录时关闭页面
    fun goBackOrUp() {
        val parent = currentDir.parentFile
        if (currentDir.absolutePath != root.absolutePath && parent != null) {
            currentDir = parent
        } else {
            onFinish()
        }
    }
    BackHandler { goBackOrUp() }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentDir.name.ifEmpty { "/" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBackOrUp() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Card),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // 当前绝对路径 + 汇总
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Card)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    currentDir.absolutePath,
                    fontSize = 12.sp,
                    color = TextSub,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "${entries.count { !it.isDir }} files · ${entries.count { it.isDir }} folders · Total ${formatSize(totalSize)}",
                    fontSize = 12.sp,
                    color = Accent,
                )
            }
            HorizontalDivider(color = Bg)

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("(Empty folder)", fontSize = 14.sp, color = TextSub)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.file.absolutePath }) { e ->
                        EntryRow(
                            entry = e,
                            onClick = {
                                if (e.isDir) currentDir = e.file
                                else shareFile(context, e.file)
                            },
                        )
                        HorizontalDivider(color = Bg)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(if (entry.isDir) R.drawable.ic_folder else R.drawable.ic_document),
            contentDescription = null,
            tint = if (entry.isDir) Accent else TextSub,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.file.name,
                fontSize = 14.sp,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = if (entry.isDir) {
                "${entry.childCount} items"
            } else {
                "${formatSize(entry.size)} · ${formatTime(entry.lastModified)}"
            }
            Text(sub, fontSize = 11.sp, color = TextSub)
        }
        if (!entry.isDir) {
            Text("Share", fontSize = 12.sp, color = Accent)
        }
    }
}

private fun shareFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "txt", "md" -> "text/plain"
            else -> "*/*"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun formatTime(ms: Long): String =
    if (ms <= 0) "-" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
