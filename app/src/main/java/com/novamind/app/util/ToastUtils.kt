package com.novamind.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Toast 工具：统一弹提示。
 *
 * - 用 `applicationContext`，避免持有 Activity 造成泄漏；
 * - 自动取消上一条，连续调用不会排队堆叠；
 * - 始终切到主线程显示，可在任意线程调用。
 */
object ToastUtils {

    @SuppressLint("StaticFieldLeak") // 仅持有 applicationContext，无泄漏风险
    private var current: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 短时提示（文本）。 */
    fun short(context: Context, text: CharSequence) = show(context, text, Toast.LENGTH_SHORT)

    /** 长时提示（文本）。 */
    fun long(context: Context, text: CharSequence) = show(context, text, Toast.LENGTH_LONG)

    /** 短时提示（字符串资源）。 */
    fun short(context: Context, @StringRes resId: Int) =
        show(context, context.getString(resId), Toast.LENGTH_SHORT)

    /** 长时提示（字符串资源）。 */
    fun long(context: Context, @StringRes resId: Int) =
        show(context, context.getString(resId), Toast.LENGTH_LONG)

    /** 取消当前正在显示的提示。 */
    fun cancel() {
        mainHandler.post { current?.cancel(); current = null }
    }

    private fun show(context: Context, text: CharSequence, duration: Int) {
        val app = context.applicationContext
        val block = {
            current?.cancel()
            current = Toast.makeText(app, text, duration).also { it.show() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
