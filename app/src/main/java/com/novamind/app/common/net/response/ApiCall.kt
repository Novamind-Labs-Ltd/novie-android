package com.novamind.app.common.net.response

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import retrofit2.Response

private const val TAG = "ApiCall"

/**
 * 统一响应解包器：把一次 Retrofit 调用折叠为领域三态 [ApiResult]。
 *
 * 覆盖 `doc/api-reference.md` 约定的全部形态：
 * 1. **业务成功** —— HTTP 2xx 且信封 `code == 200` → [ApiResult.Success]（`data` 可能为 null）。
 * 2. **无内容成功** —— HTTP 204 / 空响应体 → [ApiResult.Success]`(null)`。
 * 3. **业务错误** —— HTTP 非 2xx（错误信封）或 2xx 携带非 200 业务码 → [ApiResult.BizError]。
 * 4. **网络/解析失败** —— 抛异常、连接失败，或响应体无法解析为信封 → [ApiResult.NetworkError]。
 *
 * 用法：
 * ```kotlin
 * val result = apiCall { NetworkModule.someApi.getProfile() }
 * when (result) {
 *     is ApiResult.Success     -> render(result.data)
 *     is ApiResult.BizError    -> if (result.isAuthExpired) relogin() else toast(result.message)
 *     is ApiResult.NetworkError -> toast("网络异常，请重试")
 * }
 * ```
 *
 * @param block 发起请求的挂起函数，返回 `Response<ApiResponse<T>>`（Retrofit 已用统一 Json 解析信封）。
 */
suspend fun <T> apiCall(
    block: suspend () -> Response<ApiResponse<T>>,
): ApiResult<T> =
    try {
        block().toApiResult()
    } catch (c: CancellationException) {
        // Retrofit 会随调用协程取消底层 OkHttp Call；取消属于正常控制流，必须继续向上传播。
        throw c
    } catch (t: Throwable) {
        AppLog.w(TAG) { "请求异常: ${t.message}" }
        ApiResult.NetworkError(cause = t)
    }

/**
 * 把已拿到的 [Response] 折叠为 [ApiResult]。异常需由调用方（如 [apiCall]）捕获。
 * 单独暴露以便已持有 Response 的场景直接复用同一套解包规则。
 */
fun <T> Response<ApiResponse<T>>.toApiResult(): ApiResult<T> {
    // 非 2xx：错误信封在 errorBody，按 ApiErrorData 解析
    if (!isSuccessful) {
        return parseErrorEnvelope(code(), errorBody()?.string())
    }
    // 2xx：204 / 空体视为无数据成功
    val envelope = body()
        ?: return ApiResult.Success(null)

    return if (envelope.isSuccess) {
        ApiResult.Success(envelope.data)
    } else {
        // 罕见：HTTP 2xx 却携带非 200 业务码；data 结构不确定，仅取码与文案
        ApiResult.BizError(
            code = envelope.code,
            message = envelope.message,
            httpStatus = BizCode.httpStatusOf(envelope.code),
        )
    }
}

/**
 * 解析非 2xx 的错误信封字符串。解析失败（空体/非信封结构）时退化为 [ApiResult.NetworkError]，
 * 并带上 HTTP 状态码，避免把无法归因的传输问题误报成业务错误。
 */
fun parseErrorEnvelope(httpStatus: Int, rawBody: String?): ApiResult<Nothing> {
    if (rawBody.isNullOrBlank()) {
        AppLog.w(TAG) { "非2xx 且错误体为空 http=$httpStatus" }
        return ApiResult.NetworkError(httpStatus = httpStatus, message = "HTTP $httpStatus")
    }
    return try {
        val env = NetworkModule.json.decodeFromString<ApiResponse<ApiErrorData>>(rawBody)
        val err = env.data
        ApiResult.BizError(
            code = env.code,
            message = env.message,
            httpStatus = httpStatus,
            traceId = err?.traceId,
            fields = err?.fields.orEmpty(),
            errorData = err,
        )
    } catch (t: Throwable) {
        AppLog.w(TAG) { "错误信封解析失败 http=$httpStatus: ${t.message}" }
        ApiResult.NetworkError(httpStatus = httpStatus, cause = t)
    }
}
