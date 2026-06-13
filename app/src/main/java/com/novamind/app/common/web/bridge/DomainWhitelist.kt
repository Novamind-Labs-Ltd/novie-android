package com.novamind.app.common.web.bridge

import android.net.Uri

/**
 * 域名白名单：根据 URL 判定来源等级（TRUSTED / PARTNER / UNKNOWN）。
 *
 * 安全要点（见 Notion「JsBridge」第 7 节）：
 * - **精确后缀匹配**：host == domain 或 host.endsWith(".$domain")，杜绝 `contains` 被
 *   `evil-novamind-labs.ai` 之类绕过。
 * - **仅 https 参与可信判定**：明文 http 一律降为 UNKNOWN。
 * - 本地 `file:///android_asset/` 页视为一方可信（App 内置页）。
 * - 等级应**按当前 URL 动态评估**：可信页若跳转到外链，应即时降权。
 */
object DomainWhitelist {

    // 一方域名（可调全部 API，含 PRIVATE）
    private val trusted = mutableListOf(
        "novamind-labs.ai",
    )

    // 合作方域名（可调 PUBLIC + AUTHED）
    private val partner = mutableListOf<String>(
        // 例：在此登记合作方域名
    )

    /** 由远程配置/启动配置覆盖白名单（预留）。 */
    fun configure(trustedDomains: List<String>, partnerDomains: List<String>) {
        trusted.clear(); trusted.addAll(trustedDomains.map { it.lowercase() })
        partner.clear(); partner.addAll(partnerDomains.map { it.lowercase() })
    }

    fun levelOf(url: String): SourceLevel {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return SourceLevel.UNKNOWN
        val scheme = uri.scheme?.lowercase()

        // 本地内置资源页：一方可信
        if (scheme == "file" && uri.path?.startsWith("/android_asset/") == true) {
            return SourceLevel.TRUSTED
        }
        // 仅 https 来源参与可信判定
        if (scheme != "https") return SourceLevel.UNKNOWN
        val host = uri.host?.lowercase() ?: return SourceLevel.UNKNOWN

        return when {
            trusted.any { matches(host, it) } -> SourceLevel.TRUSTED
            partner.any { matches(host, it) } -> SourceLevel.PARTNER
            else -> SourceLevel.UNKNOWN
        }
    }

    /** 精确后缀匹配：host 等于 domain，或为其子域。 */
    private fun matches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")
}
