package com.novamind.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.novamind.app.ui.colors.Palette

/**
 * Toast 工具：统一显示无图标的纯文本提示。
 *
 * - 用 `applicationContext`，避免持有 Activity 造成泄漏；
 * - 自动取消上一条，连续调用不会排队堆叠；
 * - 始终切到主线程显示，可在任意线程调用。
 */
object ToastUtils {

    /** Toast 距屏幕底部的高度，避开系统手势区和应用底部导航栏。 */
    private const val BOTTOM_OFFSET_DP = 200

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
            val density = app.resources.displayMetrics.density
            val horizontalPadding = (16 * density).toInt()
            val verticalPadding = (10 * density).toInt()
            val messageView = TextView(app).apply {
                this.text = text
                setTextColor(Palette.sand300.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * density
                    setColor(Palette.gray800.toArgb())
                }
            }
            @Suppress("DEPRECATION")
            current = Toast(app).apply {
                this.duration = duration
                view = messageView
                setGravity(
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    0,
                    (BOTTOM_OFFSET_DP * density).toInt(),
                )
            }.also { it.show() }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
