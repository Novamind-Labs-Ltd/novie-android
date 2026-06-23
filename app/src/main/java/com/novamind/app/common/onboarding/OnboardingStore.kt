package com.novamind.app.common.onboarding

import android.content.Context
import com.novamind.app.common.storage.MmkvStore

/** 引导页是否已完成（首启展示一次，持久化到 MMKV）。 */
object OnboardingStore {

    private const val PREF = "onboarding"
    private const val KEY_DONE = "completed"

    // MMKV.mmkvWithID 无需 Context；保留方法上的 context 参数仅为兼容既有调用方签名。
    private val store by lazy { MmkvStore(PREF) }

    fun isCompleted(@Suppress("UNUSED_PARAMETER") context: Context): Boolean =
        store.getBoolean(KEY_DONE, false)

    fun setCompleted(@Suppress("UNUSED_PARAMETER") context: Context, done: Boolean) {
        store.putBoolean(KEY_DONE, done)
    }
}
