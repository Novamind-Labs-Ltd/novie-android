package com.novamind.app.common.web.bridge

import android.content.Context
import org.json.JSONObject

/**
 * 单个 JSAPI 的实现。每个 Handler 声明自己的 [name]（"命名空间.方法"）与 [permission]。
 * [handle] 运行在主线程；需要 IO 的实现自行 withContext(Dispatchers.IO)。
 */
interface BridgeHandler {
    /** 形如 "ui.toast"。 */
    val name: String

    /** 权限等级，由 PermissionGate 结合来源域名等级判定。 */
    val permission: ApiPermission

    suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult
}

/**
 * Handler 执行上下文：提供 App 级依赖与容器回调（不持有 WebView，避免泄漏）。
 *
 * [sourceLevelProvider] 按「当前 URL」动态评估来源等级——可信页跳到外链会即时降权。
 */
class BridgeContext(
    val appContext: Context,
    private val sourceLevelProvider: () -> SourceLevel,
    /** 关闭当前 WebView 页（nav.close 使用）。 */
    val onClose: () -> Unit,
) {
    /** 当前来源等级（每次调用实时计算）。 */
    val sourceLevel: SourceLevel get() = sourceLevelProvider()
}
