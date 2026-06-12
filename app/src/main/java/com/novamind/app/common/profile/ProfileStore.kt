package com.novamind.app.common.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 个人资料存储（头像等）。头像路径用 SharedPreferences 持久化，应用重启仍在。
 */
object ProfileStore {

    private const val PREF = "profile"
    private const val KEY_AVATAR = "avatar_path"

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    fun load(context: Context) {
        _avatarPath.value = prefs(context).getString(KEY_AVATAR, null)
    }

    fun setAvatar(context: Context, path: String) {
        prefs(context).edit().putString(KEY_AVATAR, path).apply()
        _avatarPath.value = path
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
