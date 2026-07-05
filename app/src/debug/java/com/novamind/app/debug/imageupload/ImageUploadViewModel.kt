package com.novamind.app.debug.imageupload

import com.novamind.app.common.log.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.net.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 图片上传测试页 ViewModel：以 multipart/form-data 方式 POST 选中的图片。
 *
 * 与 ApiTestViewModel 一致，通过 Retrofit [NetworkModule] 发起请求，
 * 公用头部、TLS 钉定与（Debug）日志均由共享 OkHttpClient 统一处理。
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
                AppLog.i(TAG) { "upload done: HTTP $code, ${bytes.size} bytes, url=$parsedUrl" }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                        hasUploaded = true,
                    )
                }
                AppLog.e(TAG) { "upload failed: ${e.message}" }
            }
        }
    }

    /**
     * 以 multipart/form-data 上传单个文件，返回 (状态码, 响应体)。
     *
     * 走 Retrofit [NetworkModule.apiService]：边界与 Content-Type 由 OkHttp 自动生成，
     * 公用头部、TLS 钉定、（Debug）日志由 OkHttpClient 统一处理。
     */
    private suspend fun postMultipart(
        urlStr: String,
        field: String,
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): Pair<Int, String> {
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData(field, fileName, body)
        val resp = NetworkModule.apiService.upload(urlStr, part)
        val text = (if (resp.isSuccessful) resp.body() else resp.errorBody())?.string().orEmpty()
        return resp.code() to text
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
