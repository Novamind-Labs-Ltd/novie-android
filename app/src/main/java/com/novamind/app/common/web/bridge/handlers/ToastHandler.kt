package com.novamind.app.common.web.bridge.handlers

import android.widget.Toast
import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeCode
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** ui.toast：弹系统 Toast。params: { text: String, duration?: "short"|"long" } */
class ToastHandler : BridgeHandler {
    override val name = "ui.toast"
    override val permission = ApiPermission.PUBLIC

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        val text = params.optString("text")
        if (text.isEmpty()) return BridgeResult.error(BridgeCode.INVALID_PARAM, "text required")
        val length = if (params.optString("duration") == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx.appContext, text, length).show()
        }
        return BridgeResult.ok()
    }
}
