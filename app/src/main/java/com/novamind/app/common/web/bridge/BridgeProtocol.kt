package com.novamind.app.common.web.bridge

import org.json.JSONObject

/**
 * JSBridge 协议层：统一 JSON 信封（请求 / 回执 / 事件）与错误码。
 * 详见 Notion「JsBridge」设计文档第 4、9 节。
 */

/** 来源域名等级（决定可调用的 API 权限上限）。 */
enum class SourceLevel { TRUSTED, PARTNER, UNKNOWN }

/** API 权限分级。 */
enum class ApiPermission { PUBLIC, AUTHED, PRIVATE }

/** 统一错误码。 */
object BridgeCode {
    const val OK = 0
    const val INVALID_PARAM = 1001
    const val API_NOT_FOUND = 1002
    const val PERMISSION_DENIED = 1003
    const val AUTH_FAILED = 1004
    const val TIMEOUT = 1005
    const val USER_CANCELED = 1006
    const val INTERNAL_ERROR = 1500
    const val UNAVAILABLE = 1501
}

/** 一次 JS → Native 调用请求。 */
data class BridgeRequest(
    val callId: String,
    val namespace: String,
    val api: String,
    val params: JSONObject,
    val version: String,
) {
    /** 形如 "ui.toast"，用于路由。 */
    val fullName: String get() = "$namespace.$api"

    companion object {
        /** 解析原始 JSON 字符串；非法返回 null。 */
        fun parse(raw: String): BridgeRequest? = runCatching {
            val o = JSONObject(raw)
            val callId = o.optString("callId").takeIf { it.isNotEmpty() } ?: return null
            val namespace = o.optString("namespace").takeIf { it.isNotEmpty() } ?: return null
            val api = o.optString("api").takeIf { it.isNotEmpty() } ?: return null
            BridgeRequest(
                callId = callId,
                namespace = namespace,
                api = api,
                params = o.optJSONObject("params") ?: JSONObject(),
                version = o.optString("version", "1.0"),
            )
        }.getOrNull()
    }
}

/** Handler 执行结果。 */
data class BridgeResult(
    val code: Int,
    val message: String,
    val data: JSONObject? = null,
) {
    companion object {
        fun ok(data: JSONObject? = null) = BridgeResult(BridgeCode.OK, "ok", data)
        fun error(code: Int, message: String?) = BridgeResult(code, message ?: "error", null)
    }
}

/** 拼回执 JSON：{callId, code, message, data}。 */
fun buildResponseJson(callId: String, result: BridgeResult): String =
    JSONObject().apply {
        put("callId", callId)
        put("code", result.code)
        put("message", result.message)
        put("data", result.data ?: JSONObject())
    }.toString()

/** 拼事件 JSON：{event, data}。 */
fun buildEventJson(event: String, data: JSONObject): String =
    JSONObject().apply {
        put("event", event)
        put("data", data)
    }.toString()
