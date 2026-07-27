package com.novamind.app.common.web.bridge.handlers

import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeCode
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import com.novamind.app.util.ToastUtils
import org.json.JSONObject

/** ui.toast：显示无图标文本 Toast。params: { text: String, duration?: "short"|"long" } */
class ToastHandler : BridgeHandler {
    override val name = "ui.toast"
    override val permission = ApiPermission.PUBLIC

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        val text = params.optString("text")
        if (text.isEmpty()) return BridgeResult.error(BridgeCode.INVALID_PARAM, "text required")
        if (params.optString("duration") == "long") {
            ToastUtils.long(ctx.appContext, text)
        } else {
            ToastUtils.short(ctx.appContext, text)
        }
        return BridgeResult.ok()
    }
}
