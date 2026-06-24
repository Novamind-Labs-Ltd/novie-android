package com.novamind.app.common.web.bridge

import android.webkit.WebView
import com.novamind.app.common.log.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Bridge 核心：解析 → 鉴权 → 路由 → 线程调度 → 回执；并支持 Native 主动派发事件。
 *
 * - 入口 [onMessage] 由 [NovieBridgeInterface] 在 JS Binder 线程调用，内部切主线程处理。
 * - 回执/事件通过 [evaluateJavascript] 下发，必须在 WebView 创建线程（主线程）执行。
 * - 仅弱引用 WebView，随页面销毁自动失效，避免泄漏。
 */
class BridgeDispatcher(
    webView: WebView,
    private val registry: ApiRegistry,
    private val context: BridgeContext,
    private val scope: CoroutineScope,
) {
    private val webViewRef = WeakReference(webView)

    /** JS → Native：解析、鉴权、路由、回执。 */
    fun onMessage(raw: String) {
        val req = BridgeRequest.parse(raw) ?: run {
            DebugLog.w(TAG, "drop invalid message: ${raw.take(120)}")
            return
        }
        scope.launch {
            val result = dispatch(req)
            respond(req.callId, result)
            DebugLog.d(TAG, "${req.fullName} -> ${result.code} (${context.sourceLevel})")
        }
    }

    private suspend fun dispatch(req: BridgeRequest): BridgeResult {
        val handler = registry.find(req.fullName)
            ?: return BridgeResult.error(BridgeCode.API_NOT_FOUND, "api not found: ${req.fullName}")
        if (!PermissionGate.allow(handler.permission, context.sourceLevel)) {
            return BridgeResult.error(BridgeCode.PERMISSION_DENIED, "permission denied: ${req.fullName}")
        }
        return runCatching { handler.handle(req.params, context) }
            .getOrElse { BridgeResult.error(BridgeCode.INTERNAL_ERROR, it.message) }
    }

    private suspend fun respond(callId: String, result: BridgeResult) {
        val json = buildResponseJson(callId, result)
        eval("window.NovieBridge && window.NovieBridge._onResponse($json)")
    }

    /** Native → JS：主动派发事件（生命周期/网络/返回键等）。 */
    fun emit(event: String, data: JSONObject = JSONObject()) {
        scope.launch {
            val json = buildEventJson(event, data)
            eval("window.NovieBridge && window.NovieBridge._onEvent($json)")
        }
    }

    private suspend fun eval(js: String) = withContext(Dispatchers.Main) {
        webViewRef.get()?.evaluateJavascript(js, null)
    }

    companion object {
        private const val TAG = "Bridge"
    }
}
