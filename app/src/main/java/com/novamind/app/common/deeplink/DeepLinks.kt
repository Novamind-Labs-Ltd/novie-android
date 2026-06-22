package com.novamind.app.common.deeplink

import android.content.Intent
import android.net.Uri
import com.novamind.app.ui.components.BottomNavDestination

/**
 * App Links / Deep Link 解析。
 *
 * 把浏览器进来的 https URI（host = [HOST]）映射成应用内部的底部导航 route。
 * 单 Activity 架构下，MainActivity 拿到 [resolve] 的结果后直接驱动 currentRoute。
 *
 * 目前仅启用首页直达；新增页面只需在 [resolve] 的 when 里加一行，
 * manifest 无需改动（intent-filter 未限定 path，整 host 都命中）。
 *
 *   https://app.novamind-labs.ai            → Home
 *   https://app.novamind-labs.ai/home       → Home
 *   https://app.novamind-labs.ai/<未知路径>  → Home（兜底，避免外链打不开）
 */
object DeepLinks {

    /** App Links 绑定的域名，需与 manifest intent-filter 及 assetlinks.json 一致。 */
    const val HOST = "app.novamind-labs.ai"

    /**
     * 从 VIEW Intent 中解析目标 route；非本应用链接或无 data 时返回 null（不导航）。
     */
    fun resolve(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return resolve(intent.data)
    }

    /** 从 URI 解析目标 route。 */
    fun resolve(uri: Uri?): String? {
        if (uri == null) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null

        return when (uri.pathSegments.firstOrNull()?.lowercase()) {
            null, "", "home" -> BottomNavDestination.Home.route
            // 后续要开放更多入口时在此扩展，例如：
            // "library" -> BottomNavDestination.Library.route
            // "calendar" -> BottomNavDestination.Calendar.route
            else -> BottomNavDestination.Home.route // 未知路径兜底进首页
        }
    }
}
