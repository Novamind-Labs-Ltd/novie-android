package com.novamind.app.common.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 升级状态控制器。当前为 Mock 策略（无后端时默认「无更新」），后续把 [checkOnStartup]
 * 换成请求服务端策略接口即可。UI 观察 [state] 决定是否弹升级框。
 */
object UpdateController {

    const val STORE_URL = "https://play.google.com/store/apps/details?id=com.novamind.app"

    private val _state = MutableStateFlow<UpdateInfo?>(null)
    /** 当前需要展示的升级信息；null = 不展示 */
    val state: StateFlow<UpdateInfo?> = _state.asStateFlow()

    /**
     * 冷启动检查（占位 Mock）。真实实现：请求服务端拿 latest / minSupported，按规则判定：
     * - current >= latest → None
     * - current < minSupported → Force
     * - 否则 → Optional
     */
    fun checkOnStartup(currentVersionCode: Int) {
        val latest = currentVersionCode      // mock：最新即当前 → 无更新
        val minSupported = 0
        val type = when {
            currentVersionCode >= latest -> UpdateType.None
            currentVersionCode < minSupported -> UpdateType.Force
            else -> UpdateType.Optional
        }
        _state.value = if (type == UpdateType.None) null else mock(type)
    }

    /** Debug 模拟：直接以指定类型弹出（None = 关闭） */
    fun simulate(type: UpdateType) {
        _state.value = if (type == UpdateType.None) null else mock(type)
    }

    /** 关闭升级弹窗；强制升级不可关闭 */
    fun dismiss() {
        if (_state.value?.type != UpdateType.Force) _state.value = null
    }

    private fun mock(type: UpdateType) = UpdateInfo(
        type = type,
        latestVersionName = "9.9.9",
        latestVersionCode = 999,
        releaseNotes = "• 全新个人中心与抽屉\n• 图片预览体验优化\n• 修复若干已知问题",
        url = STORE_URL,
    )
}
