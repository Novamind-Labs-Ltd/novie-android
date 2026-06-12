package com.novamind.app.common.onboarding

import android.content.Context

/** 引导页是否已完成（首启展示一次，用 SharedPreferences 持久化） */
object OnboardingStore {

    private const val PREF = "onboarding"
    private const val KEY_DONE = "completed"

    fun isCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DONE, false)

    fun setCompleted(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_DONE, done).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
