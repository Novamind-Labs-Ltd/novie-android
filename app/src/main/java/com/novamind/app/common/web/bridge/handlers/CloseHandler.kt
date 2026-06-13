package com.novamind.app.common.web.bridge.handlers

import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** nav.close：关闭当前 WebView 页（等价于点返回退出容器）。 */
class CloseHandler : BridgeHandler {
    override val name = "nav.close"
    override val permission = ApiPermission.PUBLIC

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        withContext(Dispatchers.Main) { ctx.onClose() }
        return BridgeResult.ok()
    }
}
