package com.novamind.app.common.web.bridge.handlers

import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import org.json.JSONObject

/**
 * app.getToken（AUTHED）：返回访问令牌，供合作方/一方页面带鉴权请求。
 * UNKNOWN 来源调用会被 PermissionGate 拦截（错误码 1003）。
 *
 * 注：当前为 Mock。真实实现需从主进程登录态获取（WebView 在 :web 进程，
 * 应经 ContentProvider/Messenger 跨进程读取，详见 Notion 第 10 节）。
 */
class TokenHandler : BridgeHandler {
    override val name = "app.getToken"
    override val permission = ApiPermission.AUTHED

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        val data = JSONObject().apply {
            put("token", "mock_token_${System.currentTimeMillis()}")
            put("expiresIn", 3600)
        }
        return BridgeResult.ok(data)
    }
}
