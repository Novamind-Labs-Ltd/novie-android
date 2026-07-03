package com.novamind.app.common.net.response

/**
 * 后端业务码表（对齐 `doc/api-reference.md` 的「业务码表」）。
 *
 * 规则：
 * - 成功 = [SUCCESS]（`200`）。
 * - 错误码 = `HTTP状态码 × 100 + 两位子码` —— 即前三位就是 HTTP 状态码。
 * - 未显式映射的 MVC 异常按 `HTTP状态码 × 100 + 99`（通用族码，如 `40099`）返回。
 *
 * 这里只把文档中**已命名**的码列为常量，供上层做精确分支；未列出的码仍可用
 * [httpStatusOf] / [subCodeOf] 归类处理，无需穷举。
 */
object BizCode {

    /** 业务成功码（与 HTTP 200 分离，但取值一致）。 */
    const val SUCCESS = 200

    // ── 400 校验 / 请求 ────────────────────────────────────────────────
    const val VALIDATION_FAILED = 40001
    const val MALFORMED_BODY = 40002
    const val BAD_PARAMETER = 40003
    const val FILE_TYPE_NOT_ALLOWED = 40004
    const val FILE_TOO_LARGE = 40005
    const val FILE_UPLOAD_INVALID = 40006
    const val TOO_MANY_RECIPIENTS = 40007

    // ── 401 / 403 鉴权 ────────────────────────────────────────────────
    const val INVALID_TOKEN = 40101
    const val NOT_AUTHORIZED = 40301

    // ── 404 不存在 ────────────────────────────────────────────────────
    const val NOTE_NOT_FOUND = 40401
    const val ATTACHMENT_NOT_FOUND = 40402
    const val SHARE_LINK_INVALID = 40403
    const val UNKNOWN_ROUTE = 40404
    const val FILE_NOT_FOUND = 40405
    const val MAIL_BATCH_NOT_FOUND = 40406

    // ── 405 / 406 / 409 ───────────────────────────────────────────────
    const val METHOD_NOT_ALLOWED = 40501
    const val NOT_ACCEPTABLE = 40601
    const val CONFLICT = 40901
    const val FILE_NOT_READY = 40902
    const val ATTACHMENT_LIMIT_EXCEEDED = 40903

    // ── 413 / 415 / 429 ───────────────────────────────────────────────
    const val PAYLOAD_TOO_LARGE = 41301
    const val UNSUPPORTED_MEDIA_TYPE = 41501
    const val QUOTA_EXCEEDED = 42901

    // ── 5xx 服务端 ────────────────────────────────────────────────────
    const val INTERNAL_ERROR = 50001
    const val PERSISTENCE_ERROR = 50002
    const val DEPENDENCY_UNAVAILABLE = 50201

    /** 本地兜底码：无法解析响应体 / 未知客户端错误（非后端下发）。 */
    const val CLIENT_UNKNOWN = -1

    /** 从业务码还原 HTTP 状态码（前三位）。成功码返回 200。 */
    fun httpStatusOf(code: Int): Int = if (code == SUCCESS) 200 else code / 100

    /** 取业务子码（后两位）。成功码返回 0。 */
    fun subCodeOf(code: Int): Int = if (code == SUCCESS) 0 else code % 100

    /** 是否为「通用族码」（子码为 99，即未显式映射的兜底族）。 */
    fun isGeneric(code: Int): Boolean = subCodeOf(code) == 99

    /** 是否鉴权失效（token 缺失/无效/过期）——上层可据此触发重新登录。 */
    fun isAuthExpired(code: Int): Boolean = code == INVALID_TOKEN

    /** 是否 4xx 客户端族错误。 */
    fun isClientError(code: Int): Boolean = code != SUCCESS && httpStatusOf(code) in 400..499

    /** 是否 5xx 服务端族错误。 */
    fun isServerError(code: Int): Boolean = httpStatusOf(code) in 500..599
}
