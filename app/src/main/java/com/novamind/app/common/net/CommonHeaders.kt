package com.novamind.app.common.net

import android.content.Context
import android.os.Build
import com.novamind.app.BuildConfig
import com.novamind.app.common.device.DeviceIdentity
import com.novamind.app.debug.DebugLog
import java.net.HttpURLConnection
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * HTTPS 公用请求头（公用头部）。
 *
 * 所有走 [HttpURLConnection] 的接口请求都应套用本对象，保证平台/版本/设备标识/语言/
 * 时区/链路追踪等口径一致，避免各处各自 setRequestProperty 导致字段缺失或不统一。
 *
 * 设计要点（详见 Notion《Https公用头部》）：
 * - 自定义头统一 `X-` 前缀，避免与标准头冲突；
 * - 静态字段（平台/版本/设备/屏幕/UA）首次构建后缓存，零成本复用，与 [DeviceIdentity] 一致；
 * - 动态字段每次请求重算：`X-Request-Id`（每请求唯一）、`Authorization`（按当前登录态）；
 * - **不设置 `Content-Type`**：请求体类型由各调用方决定（JSON / multipart），公用头不越权。
 *
 * 用法：
 * ```
 * CommonHeaders.init(applicationContext)            // 在 Application.onCreate 调用一次
 * CommonHeaders.apply(conn)                         // 匿名请求
 * CommonHeaders.apply(conn, token = accessToken)    // 带鉴权请求
 * ```
 */
object CommonHeaders {

    /** 自定义头前缀，集中维护便于联调对齐。 */
    private const val H_PLATFORM = "X-App-Platform"
    private const val H_APP_VERSION = "X-App-Version"
    private const val H_APP_BUILD = "X-App-Build"
    private const val H_DEVICE_ID = "X-Device-Id"
    private const val H_DEVICE_MODEL = "X-Device-Model"
    private const val H_DEVICE_SCREEN = "X-Device-Screen"
    private const val H_OS_VERSION = "X-OS-Version"
    private const val H_OS_API = "X-OS-Api"
    private const val H_TIMEZONE = "X-Timezone"
    private const val H_REQUEST_ID = "X-Request-Id"

    /** 首次构建后缓存的静态头快照（不含每请求变化的 Request-Id / Authorization / Accept-Language）。 */
    @Volatile
    private var staticHeaders: Map<String, String>? = null

    /** 自定义 User-Agent，形如 `Novie/1.0.0 (Android 14; Google Pixel 7; build/12)`。 */
    @Volatile
    private var userAgent: String = "Novie/${BuildConfig.VERSION_NAME}"

    /**
     * 在 [android.app.Application.onCreate] 调用一次，构建并缓存设备/应用静态头。
     * 幂等：重复调用只构建一次。
     */
    fun init(context: Context) {
        if (staticHeaders != null) return
        synchronized(this) {
            if (staticHeaders != null) return
            buildStatic(context.applicationContext)
        }
    }

    private fun buildStatic(appContext: Context) {
        val fp = DeviceIdentity.fingerprint(appContext)
        val deviceModel = "${Build.BRAND} ${Build.MODEL}".trim()
        userAgent = "Novie/${BuildConfig.VERSION_NAME} " +
            "(Android ${Build.VERSION.RELEASE}; $deviceModel; build/${BuildConfig.VERSION_CODE})"
        staticHeaders = linkedMapOf(
            H_PLATFORM to "android",
            H_APP_VERSION to BuildConfig.VERSION_NAME,
            H_APP_BUILD to BuildConfig.VERSION_CODE.toString(),
            H_DEVICE_ID to DeviceIdentity.localDeviceId(appContext),
            H_DEVICE_MODEL to deviceModel,
            H_DEVICE_SCREEN to fp.screen,
            H_OS_VERSION to Build.VERSION.RELEASE,
            H_OS_API to Build.VERSION.SDK_INT.toString(),
            H_TIMEZONE to TimeZone.getDefault().id,
        )
    }

    /**
     * 返回本次请求应携带的完整公用头快照。未 [init] 时仅返回内容协商类头并打日志告警。
     *
     * @param token 访问令牌；非空时附带 `Authorization: Bearer <token>`。
     */
    fun snapshot(token: String? = null): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        // 内容协商（始终携带）
        headers["Accept"] = "application/json"
        headers["Accept-Language"] = Locale.getDefault().toLanguageTag()
        headers["User-Agent"] = userAgent
        // 应用 / 设备静态头
        staticHeaders?.let { headers.putAll(it) }
            ?: DebugLog.w(TAG, "CommonHeaders 未 init()，仅发送内容协商头")
        // 链路追踪（每请求唯一）
        headers[H_REQUEST_ID] = UUID.randomUUID().toString()
        // 鉴权（按需）
        token?.takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Bearer $it" }
        return headers
    }

    /** 把公用头逐个写入连接。应在调用方设置 `Content-Type` 之前调用。 */
    fun apply(conn: HttpURLConnection, token: String? = null) {
        snapshot(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
    }

    private const val TAG = "CommonHeaders"
}
