package com.novamind.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 运行时权限工具：集中「是否已授权 / 是否需要申请」的判断。
 *
 * 只做纯查询（无 Compose、无 UI）；真正的申请动作由调用方持有 launcher 触发。
 */
object PermissionUtils {

    /** 录音（麦克风）权限是否已授权。 */
    fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 通知权限是否已授权。Android 13（[Build.VERSION_CODES.TIRAMISU]）以下无需该权限，
     * 视为已授权。
     */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    /** 是否需要向用户申请通知权限（仅 Android 13+ 且尚未授权时为 true）。 */
    fun needsNotificationPermission(context: Context): Boolean =
        !hasNotificationPermission(context)
}
