package com.novamind.app.debug.apitest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

/**
 * 接口测试页 ViewModel：发起网络请求并解析为列表。
 *
 * 仅依赖 JDK 自带的 [HttpURLConnection] 与 org.json，无需引入额外网络库。
 * 网络 IO 在 [Dispatchers.IO] 上执行，UI 状态通过 [uiState] 单一不可变对象暴露。
 */
class ApiTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ApiTestUiState())
    val uiState: StateFlow<ApiTestUiState> = _uiState.asStateFlow()

    fun onEvent(event: ApiTestEvent) {
        when (event) {
            ApiTestEvent.Fetch -> fetch()
        }
    }

    private fun fetch() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { request(ENDPOINT) } }
            result.onSuccess { body ->
                val items = parseItems(body)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                        rawPreview = body.take(800),
                        errorMessage = if (items.isEmpty()) "解析到 0 条数据（可查看原始响应）" else null,
                        hasLoaded = true,
                    )
                }
                DebugLog.i(TAG, "fetched ${items.size} items")
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                        hasLoaded = true,
                    )
                }
                DebugLog.e(TAG, "fetch failed: ${e.message}")
            }
        }
    }

    /** 同步 GET 请求，返回响应体字符串；非 2xx 抛异常。 */
    private fun request(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            // 接口服务器证书无 SAN，仅 CN 含 IP，默认主机名校验会失败。
            // 这里只对该主机放宽主机名绑定：仍要求服务器证书指纹与钉定值一致，
            // 证书链校验依旧由 network_security_config 完整执行（非 trust-all）。
            if (this is HttpsURLConnection) hostnameVerifier = PINNED_HOSTNAME_VERIFIER
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 容错解析：接口返回结构不确定，依次尝试
     * 顶层数组 → data.items / data.list / data(数组) → items / list / records / rows。
     */
    private fun parseItems(body: String): List<ApiItem> {
        val trimmed = body.trim()
        val array: JSONArray? = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> findArray(JSONObject(trimmed))
            else -> null
        }
        array ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { toItem(it) }
        }
    }

    private fun findArray(root: JSONObject): JSONArray? {
        val keys = listOf("items", "list", "records", "rows", "results", "data")
        // 先看 data 内层
        root.optJSONObject("data")?.let { d ->
            for (k in keys) d.optJSONArray(k)?.let { return it }
        }
        root.optJSONArray("data")?.let { return it }
        for (k in keys) root.optJSONArray(k)?.let { return it }
        return null
    }

    private fun toItem(obj: JSONObject): ApiItem {
        val id = firstOf(obj, "id", "uuid", "key", "_id")
        val title = firstOf(obj, "title", "name", "label", "headline").ifEmpty { "Item $id".trim() }
        val subtitle = firstOf(obj, "subtitle", "desc", "description", "summary", "content")
        return ApiItem(
            id = id,
            title = title,
            subtitle = subtitle,
            extra = obj.toString(),
        )
    }

    private fun firstOf(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) return obj.optString(k)
        }
        return ""
    }

    companion object {
        private const val TAG = "ApiTest"
        const val ENDPOINT = "https://121.41.207.114/items?page=1&size=10"

        /** 需要放宽主机名校验的目标主机。 */
        private const val PINNED_HOST = "121.41.207.114"

        /** 钉定的服务器证书 SHA-256 指纹（大写十六进制，无分隔符）。 */
        private const val PINNED_CERT_SHA256 =
            "23C623C3BC4A8C7B364AA539115BC063A4C2ECA90D86768631DF6C084C6A7B35"

        /**
         * 仅对 [PINNED_HOST] 放宽主机名绑定：校验对端证书指纹与 [PINNED_CERT_SHA256] 一致即放行。
         * 其它主机一律走系统默认校验。指纹不匹配则拒绝。
         */
        private val PINNED_HOSTNAME_VERIFIER = HostnameVerifier { hostname, session ->
            if (hostname == PINNED_HOST) {
                runCatching {
                    val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                    cert != null && sha256Hex(cert.encoded) == PINNED_CERT_SHA256
                }.getOrDefault(false)
            } else {
                HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
        }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it) }
    }
}

/** UI → ViewModel 事件。 */
sealed interface ApiTestEvent {
    data object Fetch : ApiTestEvent
}
