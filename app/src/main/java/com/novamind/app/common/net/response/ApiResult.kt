package com.novamind.app.common.net.response

/**
 * 领域层统一结果：把「HTTP 传输 + 业务信封」两层收敛为供 UI/仓库消费的三态。
 *
 * 相比直接把 [ApiResponse] 往上抛，[ApiResult] 让调用方只需 `when` 三个分支即可覆盖
 * 「成功 / 后端返回了业务错误 / 压根没连上后端」，无需再各自判 HTTP 码、解错误体、catch 异常。
 *
 * 用解包器 [apiCall] / [Response.toApiResult]（见 ApiCall.kt）从 Retrofit 响应构造本类型。
 */
sealed interface ApiResult<out T> {

    /**
     * 业务成功（信封 `code == 200`）。
     * [data] 为业务数据；`204 No Content` 等无数据成功场景为 `null`。
     */
    data class Success<out T>(val data: T?) : ApiResult<T>

    /**
     * 后端**明确**返回了业务错误信封（无论 HTTP 2xx 携带非 200 业务码，还是非 2xx 错误体）。
     *
     * @property code    业务码（见 [BizCode]）。
     * @property message 后端 message，缺省时上层可回退到本地文案。
     * @property httpStatus 对应 HTTP 状态码（由 [BizCode.httpStatusOf] 还原或取自响应）。
     * @property traceId 链路追踪 id，与响应头 `X-Trace-Id` 对应，便于排障/上报。
     * @property fields  字段级校验错误（仅 [BizCode.VALIDATION_FAILED] 等场景非空）。
     * @property errorData 完整错误负载（含 `current` 等场景化字段），需要时可深入取用。
     */
    data class BizError(
        val code: Int,
        val message: String?,
        val httpStatus: Int,
        val traceId: String? = null,
        val fields: List<FieldError> = emptyList(),
        val errorData: ApiErrorData? = null,
    ) : ApiResult<Nothing> {
        /** 是否鉴权失效——上层可据此触发重新登录。 */
        val isAuthExpired: Boolean get() = BizCode.isAuthExpired(code)
    }

    /**
     * 未能取得业务信封：网络异常、超时、TLS/连接失败，或响应体无法解析为信封。
     * 这类失败**没有**业务码，通常提示「网络异常，请重试」。
     *
     * @property cause 原始异常（若由异常触发）。
     * @property httpStatus 若已拿到 HTTP 响应但响应体不可解析，则带上状态码，否则为 null。
     */
    data class NetworkError(
        val cause: Throwable? = null,
        val httpStatus: Int? = null,
        val message: String? = cause?.message,
    ) : ApiResult<Nothing>
}

/** 成功时取数据，否则返回 [fallback]（含 204 的 null 也走 fallback）。 */
fun <T> ApiResult<T>.dataOr(fallback: T): T =
    (this as? ApiResult.Success)?.data ?: fallback

/** 成功且有数据时返回，否则 null。 */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/** 链式变换成功数据，错误分支原样透传。 */
inline fun <T, R> ApiResult<T>.map(transform: (T?) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.BizError -> this
    is ApiResult.NetworkError -> this
}

/** 成功回调（仅在业务成功时触发），返回自身以便链式。 */
inline fun <T> ApiResult<T>.onSuccess(action: (T?) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

/** 任意错误回调（业务错误 + 网络错误都会触发），返回自身以便链式。 */
inline fun <T> ApiResult<T>.onError(action: (ApiResult<T>) -> Unit): ApiResult<T> {
    if (this !is ApiResult.Success) action(this)
    return this
}
