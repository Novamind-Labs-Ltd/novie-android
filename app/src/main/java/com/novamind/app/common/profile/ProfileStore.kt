package com.novamind.app.common.profile

import android.content.Context
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地头像暂存（编辑 / 上传中转）。头像路径持久化到 MMKV，应用重启仍在。
 *
 * 注：全局展示的头像**以后端 `avatarUrl` 为准**（见 [com.novamind.app.common.session.UserSession]）；
 * 本地路径仅用于换头像时的选图/裁剪中转，待后端头像写接口就绪后由服务端 URL 覆盖显示。
 */
object ProfileStore {

    private const val PREF = "profile"
    private const val KEY_AVATAR = "avatar_path"

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // MMKV.mmkvWithID 无需 Context；保留方法上的 context 参数仅为兼容既有调用方签名。
    private val store by lazy { MmkvStore(PREF) }

    fun load(@Suppress("UNUSED_PARAMETER") context: Context) {
        _avatarPath.value = store.getString(KEY_AVATAR, null)
    }

    /** 设置本地暂存头像（选图/裁剪后的中转路径），非最终展示源。 */
    fun setLocalAvatar(@Suppress("UNUSED_PARAMETER") context: Context, path: String) {
        store.putString(KEY_AVATAR, path)
        _avatarPath.value = path
    }
}
