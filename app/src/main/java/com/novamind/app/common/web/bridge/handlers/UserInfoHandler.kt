package com.novamind.app.common.web.bridge.handlers

import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import org.json.JSONObject

/**
 * user.getUserInfo（PRIVATE）：返回用户信息，仅一方可信页可调。
 * PARTNER / UNKNOWN 来源调用会被 PermissionGate 拦截（错误码 1003）。
 *
 * 注：当前为 Mock。真实实现需跨进程从主进程读取登录态用户信息。
 */
class UserInfoHandler : BridgeHandler {
    override val name = "user.getUserInfo"
    override val permission = ApiPermission.PRIVATE

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        val data = JSONObject().apply {
            put("userId", "u_10086")
            put("nickname", "Novie 用户")
            put("avatar", "")
        }
        return BridgeResult.ok(data)
    }
}
