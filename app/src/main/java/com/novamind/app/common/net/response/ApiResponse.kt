package com.novamind.app.common.net.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 后端统一响应信封（对齐 `doc/api-reference.md` 的「统一响应约定」）。
 *
 * 无论成功或失败，除少数二进制/无内容端点外，所有响应都被包成同一结构：
 * ```json
 * { "code": 200, "message": "OK", "data": { } }
 * ```
 *
 * - [code] 为**业务码**，与 HTTP 状态码分离：成功恒为 [BizCode.SUCCESS]（200），
 *   错误码 = `HTTP状态码 × 100 + 两位子码`（见 [BizCode]）。
 * - [data] 成功时是业务数据（对象/数组/字符串）；失败时是 [ApiErrorData]（始终含 traceId）。
 * - 返回 `null` 的端点 → HTTP `204 No Content`，无响应体（不会有本信封）。
 * - 声明返回 `ResponseEntity`/`Resource`/`byte[]` 的端点绕过信封（如二进制下载）。
 *
 * 泛型 [T] 为成功数据类型。错误响应（非 2xx）由 Retrofit 归入 errorBody，
 * 交由 [ApiResponse.decodeError] / 解包器按 [ApiErrorData] 解析。
 */
@Serializable
data class ApiResponse<out T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null,
) {
    /** 业务是否成功：业务码为 [BizCode.SUCCESS]。注意与 HTTP 状态码区分。 */
    val isSuccess: Boolean get() = code == BizCode.SUCCESS
}

/**
 * 错误信封的 `data` 负载。所有异常统一转成信封后，`data` **始终携带** [traceId]，
 * 便于与响应头 `X-Trace-Id` 对应排障。
 *
 * - [fields] 仅字段级校验失败（如 `40001 VALIDATION_FAILED`）时出现，逐字段列出原因。
 * - [current] 仅乐观锁冲突等场景出现，携带服务端当前状态（结构随端点而定，用 [JsonElement] 承载）。
 */
@Serializable
data class ApiErrorData(
    val traceId: String? = null,
    val fields: List<FieldError>? = null,
    val current: JsonElement? = null,
)

/** 字段级校验错误项：哪个字段（[field]）因何原因（[reason]）失败。 */
@Serializable
data class FieldError(
    val field: String,
    val reason: String,
)
