package com.novamind.app.debug.imageupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.debug.DebugLog
import com.novamind.app.debug.apitest.ApiTls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 图片上传测试页 ViewModel：以 multipart/form-data 方式 POST 选中的图片。
 *
 * 与 ApiTestViewModel 一致，仅依赖 JDK 自带的 [HttpURLConnection]，
 * 网络 IO 在 [Dispatchers.IO] 上执行，复用 [ApiTls] 做主机名钉定。
 */
class ImageUploadViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImageUploadUiState())
    val uiState: StateFlow<ImageUploadUiState> = _uiState.asStateFlow()

    /** 待上传图片的字节内容（不放进可观察状态，避免大对象进 StateFlow）。 */
    private var pendingBytes: ByteArray? = null
    private var pendingMime: String = "application/octet-stream"

    fun onEvent(event: ImageUploadEvent) {
        when (event) {
            is ImageUploadEvent.UpdateUrl -> _uiState.update { it.copy(url = event.value) }
            is ImageUploadEvent.UpdateFieldName ->
                _uiState.update { it.copy(fieldName = event.value) }
            is ImageUploadEvent.ImagePicked -> {
                pendingBytes = event.bytes
                pendingMime = event.mime
                _uiState.update {
                    it.copy(
                        pickedName = event.name,
                        pickedSize = event.size,
                        uploadedUrl = "",
                        errorMessage = null,
                    )
                }
            }
            ImageUploadEvent.ClearImage -> {
                pendingBytes = null
                _uiState.update { it.copy(pickedName = "", pickedSize = 0L, uploadedUrl = "") }
            }
            ImageUploadEvent.Upload -> upload()
        }
    }

    private fun upload() {
        val state = _uiState.value
        if (state.isUploading) return
        val bytes = pendingBytes
        if (bytes == null || state.pickedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请先选择一张图片") }
            return
        }
        val url = state.url.trim()
        if (url.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请填写上传地址") }
            return
        }
        val field = state.fieldName.ifBlank { ImageUploadUiState.DEFAULT_FIELD }
        val fileName = state.pickedName
        val mime = pendingMime

        _uiState.update { it.copy(isUploading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { postMultipart(url, field, fileName, mime, bytes) }
            }
            result.onSuccess { (code, body) ->
                val parsedUrl = if (code in 200..299) extractUrl(body) else ""
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        responseCode = code,
                        responsePreview = body.take(800),
                        uploadedUrl = parsedUrl,
                        errorMessage = if (code in 200..299) null else "HTTP $code",
                        hasUploaded = true,
                    )
                }
                DebugLog.i(TAG, "upload done: HTTP $code, ${bytes.size} bytes, url=$parsedUrl")
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                        hasUploaded = true,
                    )
                }
                DebugLog.e(TAG, "upload failed: ${e.message}")
            }
        }
    }

    /**
     * 手写 multipart/form-data 请求体，返回 (状态码, 响应体)。
     * 单文件字段，无额外文本字段；如需扩展可在边界之间追加 part。
     */
    private fun postMultipart(
        urlStr: String,
        field: String,
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): Pair<Int, String> {
        val boundary = "----NovieBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val lineEnd = "\r\n"
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            ApiTls.apply(this)
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary$lineEnd")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"$field\"; " +
                        "filename=\"$fileName\"$lineEnd"
                )
                out.writeBytes("Content-Type: $mime$lineEnd")
                out.writeBytes(lineEnd)
                out.write(bytes)
                out.writeBytes(lineEnd)
                out.writeBytes("--$boundary--$lineEnd")
                out.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 从响应体中解析图片链接。
     * 优先匹配常见 JSON 字段（url / data.url / path 等），
     * 否则回退到正则提取首个 http(s) 链接；纯文本链接也能命中。
     */
    private fun extractUrl(body: String): String {
        val text = body.trim()
        if (text.isEmpty()) return ""

        // 1) 常见 JSON 字段："url":"..."、"data":"..."、"path":"..."、"link":"..."
        val keyPattern = Regex(
            "\"(?:url|data|path|link|src|location|fileUrl|imageUrl|imgUrl)\"\\s*:\\s*\"([^\"]+)\"",
            RegexOption.IGNORE_CASE,
        )
        keyPattern.find(text)?.groupValues?.getOrNull(1)?.let { v ->
            if (v.isNotBlank()) return v
        }

        // 2) 回退：提取首个 http(s) 链接
        val urlPattern = Regex("https?://[^\\s\"'<>\\\\]+", RegexOption.IGNORE_CASE)
        urlPattern.find(text)?.value?.let { return it }

        // 3) 整个响应就是一个裸链接（无协议头）
        if (!text.contains('{') && !text.contains(' ') && text.length < 500) return text

        return ""
    }

    companion object {
        private const val TAG = "ImageUpload"
    }
}
