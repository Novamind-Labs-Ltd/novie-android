package com.novamind.app.common.web.bridge

/**
 * 权限闸门：用「来源域名等级 × API 权限等级」矩阵判定本次调用是否放行。
 * - TRUSTED：可调全部（含 PRIVATE）
 * - PARTNER：可调 PUBLIC + AUTHED
 * - UNKNOWN：仅可调 PUBLIC
 */
object PermissionGate {
    fun allow(api: ApiPermission, source: SourceLevel): Boolean = when (source) {
        SourceLevel.TRUSTED -> true
        SourceLevel.PARTNER -> api != ApiPermission.PRIVATE
        SourceLevel.UNKNOWN -> api == ApiPermission.PUBLIC
    }
}
