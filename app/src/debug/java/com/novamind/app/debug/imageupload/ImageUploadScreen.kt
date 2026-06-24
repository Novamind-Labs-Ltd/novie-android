package com.novamind.app.debug.imageupload

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFFF4F4F5)
private val Card = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

/** 有状态路由：连接 ViewModel、图片选择器与无状态 UI。 */
@Composable
fun ImageUploadRoute(
    onBack: () -> Unit,
    viewModel: ImageUploadViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 选中的图片 Uri 仅供预览，临时持有在 Compose 侧。
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickedUri = uri }

    // 选好图片后，在 IO 线程读取元信息与字节，交给 ViewModel。
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        val resolver = context.contentResolver
        runCatching {
            withContext(Dispatchers.IO) {
                val mime = resolver.getType(uri) ?: "image/*"
                var name = "image"
                resolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
                }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取图片内容")
                Triple(name, mime, bytes)
            }
        }.onSuccess { (name, mime, bytes) ->
            viewModel.onEvent(ImageUploadEvent.ImagePicked(name, bytes.size.toLong(), mime, bytes))
        }
    }

    ImageUploadScreen(
        uiState = uiState,
        previewUri = pickedUri,
        onPick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onClear = {
            pickedUri = null
            viewModel.onEvent(ImageUploadEvent.ClearImage)
        },
        onEvent = viewModel::onEvent,
        onBack = onBack,
    )
}

/** 无状态 UI：可预览、可测试。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUploadScreen(
    uiState: ImageUploadUiState,
    previewUri: Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onEvent: (ImageUploadEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("图片上传测试", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 22.sp, color = TextMain)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = { onEvent(ImageUploadEvent.UpdateUrl(it)) },
                label = { Text("上传地址") },
                singleLine = true,
                enabled = !uiState.isUploading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.fieldName,
                onValueChange = { onEvent(ImageUploadEvent.UpdateFieldName(it)) },
                label = { Text("表单字段名") },
                singleLine = true,
                enabled = !uiState.isUploading,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 图片选择 / 预览 ──
            if (previewUri != null && uiState.hasImage) {
                AsyncImage(
                    model = previewUri,
                    contentDescription = "已选图片预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card),
                )
                Text(
                    "${uiState.pickedName} · ${formatSize(uiState.pickedSize)}",
                    fontSize = 12.sp,
                    color = TextSub,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("重新选择", enabled = !uiState.isUploading, onClick = onPick)
                    Chip("移除", danger = true, enabled = !uiState.isUploading, onClick = onClear)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card)
                        .clickable(enabled = !uiState.isUploading, onClick = onPick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋ 点击选择图片", color = TextSub, fontSize = 14.sp)
                }
            }

            PrimaryButton(
                text = if (uiState.isUploading) "上传中…" else "上传图片",
                enabled = !uiState.isUploading && uiState.hasImage,
                onClick = { onEvent(ImageUploadEvent.Upload) },
            )

            uiState.errorMessage?.let { msg ->
                Text(
                    "⚠ $msg",
                    color = Danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Danger.copy(alpha = 0.08f))
                        .padding(12.dp),
                )
            }

            if (uiState.isUploading) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            if (uiState.hasUploadedUrl) {
                UploadedUrlCard(url = uiState.uploadedUrl)
            }

            if (uiState.hasUploaded && uiState.responseCode != 0) {
                Text(
                    "响应状态：HTTP ${uiState.responseCode}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.responseCode in 200..299) Accent else Danger,
                )
                if (uiState.responsePreview.isNotEmpty()) {
                    Text("响应体（前 800 字）", fontSize = 12.sp, color = TextSub)
                    Text(
                        uiState.responsePreview,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Card)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

/** 上传成功后展示返回的图片链接，并提供一键复制。 */
@Composable
private fun UploadedUrlCard(url: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // 复制提示在 2 秒后自动消失。
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("图片链接", fontSize = 12.sp, color = TextSub, fontWeight = FontWeight.SemiBold)
        Text(
            url,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMain,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Card)
                .padding(10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Chip(
                label = if (copied) "✓ 已复制" else "复制链接",
                onClick = {
                    clipboard.setText(AnnotatedString(url))
                    copied = true
                },
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.5f
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Chip(label: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (danger) Danger else Accent
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// ── Previews ───────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewEmpty() {
    AppTheme {
        ImageUploadScreen(ImageUploadUiState(), null, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewResult() {
    AppTheme {
        ImageUploadScreen(
            ImageUploadUiState(
                pickedName = "photo.jpg",
                pickedSize = 248_300,
                hasUploaded = true,
                responseCode = 200,
                responsePreview = "{\"url\":\"https://.../photo.jpg\"}",
                uploadedUrl = "https://cdn.example.com/uploads/photo.jpg",
            ),
            null, {}, {}, {}, {},
        )
    }
}
