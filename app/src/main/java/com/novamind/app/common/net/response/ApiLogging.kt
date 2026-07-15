package com.novamind.app.common.net.response

import com.novamind.app.common.log.AppLog

/**
 * 网络能力统一收尾：把 [apiCall] 结果的「成功映射 + 三分支日志」样板收敛为一处，供各仓库实现复用，
 * 避免每个方法都重写 `when(ApiResult) { Success/BizError/NetworkError }`。
 *
 * - 成功 → [transform] 把 DTO 映射为领域模型（DTO 不外泄），并记成功日志 [successLog]（可用映射结果）；
 * - 业务 / 网络错误 → 用 [tag]/[op] 记错误日志并**原样透传**（[ApiResult] 协变，错误分支即 `ApiResult<R>`）。
 *
 * [tag] 为各仓库的日志 TAG；[op] 为本次操作名（可带上下文，如 `"createNote id=$id"`）。
 */
fun <T, R> ApiResult<T>.mapLogged(
    tag: String,
    op: String,
    transform: (T?) -> R?,
    successLog: (R?) -> String,
): ApiResult<R> = when (this) {
    is ApiResult.Success -> {
        val mapped = transform(data)
        AppLog.i(tag) { successLog(mapped) }
        ApiResult.Success(mapped)
    }
    is ApiResult.BizError -> {
        AppLog.w(tag) { "$op 业务错误 code=$code traceId=$traceId msg=$message" }
        this
    }
    is ApiResult.NetworkError -> {
        AppLog.w(tag) { "$op 网络错误: $message" }
        this
    }
}
