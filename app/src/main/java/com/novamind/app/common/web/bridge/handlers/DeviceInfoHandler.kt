package com.novamind.app.common.web.bridge.handlers

import android.os.Build
import com.novamind.app.BuildConfig
import com.novamind.app.common.web.bridge.ApiPermission
import com.novamind.app.common.web.bridge.BridgeContext
import com.novamind.app.common.web.bridge.BridgeHandler
import com.novamind.app.common.web.bridge.BridgeResult
import org.json.JSONObject

/**
 * device.getInfo：返回设备/环境信息与 Bridge/App 版本（供 H5 做版本协商）。
 */
class DeviceInfoHandler : BridgeHandler {
    override val name = "device.getInfo"
    override val permission = ApiPermission.PUBLIC

    override suspend fun handle(params: JSONObject, ctx: BridgeContext): BridgeResult {
        val data = JSONObject().apply {
            put("platform", "android")
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("appVersionCode", BuildConfig.VERSION_CODE)
            put("bridgeVersion", BRIDGE_VERSION)
            put("sourceLevel", ctx.sourceLevel.name)
        }
        return BridgeResult.ok(data)
    }

    companion object {
        const val BRIDGE_VERSION = "1.0"
    }
}
