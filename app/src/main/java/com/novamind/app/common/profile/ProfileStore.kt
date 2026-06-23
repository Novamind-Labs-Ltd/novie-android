package com.novamind.app.common.profile

import android.content.Context
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 个人资料存储（头像等）。头像路径持久化到 MMKV，应用重启仍在。
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

    fun setAvatar(@Suppress("UNUSED_PARAMETER") context: Context, path: String) {
        store.putString(KEY_AVATAR, path)
        _avatarPath.value = path
    }
}
