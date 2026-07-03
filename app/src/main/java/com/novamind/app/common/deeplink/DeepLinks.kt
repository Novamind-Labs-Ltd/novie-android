package com.novamind.app.common.deeplink

import android.content.Intent
import android.net.Uri
import com.novamind.app.ui.components.BottomNavDestination

/** Deep Link 解析结果：tab 级跳转或带参数的页面。 */
sealed interface DeepLinkTarget {
    /** 底部导航 tab。 */
    data class Tab(val route: String) : DeepLinkTarget

    /** 笔记详情（编辑页）。 */
    data class Note(val noteId: String) : DeepLinkTarget
}

/**
 * App Links / Deep Link 解析。
 *
 * 把浏览器进来的 https URI（host = [HOST]）映射成 [DeepLinkTarget]。
 * 单 Activity 架构下，MainActivity 拿到 [resolve] 的结果后直接驱动导航状态。
 *
 * 新增入口只需在 [resolve] 的 when 里加分支，manifest 无需改动
 * （intent-filter 未限定 path，整 host 都命中）。
 *
 *   https://app.novamind-labs.ai            → Home
 *   https://app.novamind-labs.ai/home       → Home
 *   https://app.novamind-labs.ai/note/<id>  → 笔记详情
 *   https://app.novamind-labs.ai/<未知路径>  → Home（兜底，避免外链打不开）
 */
object DeepLinks {

    /** App Links 绑定的域名，需与 manifest intent-filter 及 assetlinks.json 一致。 */
    const val HOST = "app.novamind-labs.ai"

    /** 从 VIEW Intent 中解析目标；非本应用链接或无 data 时返回 null（不导航）。 */
    fun resolve(intent: Intent?): DeepLinkTarget? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return resolve(intent.data)
    }

    /** 从 URI 解析目标。 */
    fun resolve(uri: Uri?): DeepLinkTarget? {
        if (uri == null) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null

        return when (uri.pathSegments.firstOrNull()?.lowercase()) {
            null, "", "home" -> DeepLinkTarget.Tab(BottomNavDestination.Home.route)
            // /note/<id>：缺 id 时兜底首页
            "note" -> uri.pathSegments.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLinkTarget.Note(it) }
                ?: DeepLinkTarget.Tab(BottomNavDestination.Home.route)
            else -> DeepLinkTarget.Tab(BottomNavDestination.Home.route) // 未知路径兜底进首页
        }
    }
}
