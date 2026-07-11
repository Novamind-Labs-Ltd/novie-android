package com.novamind.app.common.update

import android.content.Context
import com.novamind.app.common.storage.MmkvStore
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

    private const val PREF = "update"
    private const val KEY_SIM = "simulate_type"   // Debug 预置：下次冷启动按此弹

    // MMKV.mmkvWithID 无需 Context；保留方法上的 context 参数仅为兼容既有调用方签名。
    private val store by lazy { MmkvStore(PREF) }

    /**
     * 冷启动检查。优先读取 Debug 预置的模拟类型（下次启动生效）；否则走真实策略
     * （占位 Mock：无更新）。真实实现：请求服务端 latest / minSupported 判定。
     */
    fun checkOnStartup(@Suppress("UNUSED_PARAMETER") context: Context, currentVersionCode: Int) {
        val sim = store.getString(KEY_SIM, null)
            ?.let { runCatching { UpdateType.valueOf(it) }.getOrNull() }
        if (sim != null && sim != UpdateType.None) {
            _state.value = mock(sim)
            return
        }
        val latest = currentVersionCode      // mock：最新即当前 → 无更新
        val minSupported = 0
        val type = when {
            currentVersionCode >= latest -> UpdateType.None
            currentVersionCode < minSupported -> UpdateType.Force
            else -> UpdateType.Optional
        }
        _state.value = if (type == UpdateType.None) null else mock(type)
    }

    /** Debug 预置：不立即弹，下次冷启动再按 [type] 弹（None = 清除预置并关闭当前弹窗） */
    fun setSimulateForNextLaunch(@Suppress("UNUSED_PARAMETER") context: Context, type: UpdateType) {
        if (type == UpdateType.None) {
            store.remove(KEY_SIM)
            _state.value = null
        } else {
            store.putString(KEY_SIM, type.name)
        }
    }

    /** 关闭升级弹窗；强制升级不可关闭 */
    fun dismiss() {
        if (_state.value?.type != UpdateType.Force) _state.value = null
    }

    private fun mock(type: UpdateType) = UpdateInfo(
        type = type,
        latestVersionName = "9.9.9",
        latestVersionCode = 999,
        releaseNotes = "• Brand-new profile and drawer\n• Improved image preview experience\n• Fixed several known issues",
        url = STORE_URL,
    )
}
