package com.novamind.app.util

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat

/**
 * 时间格式化工具。集中处理应用内的笔记时间、相对时间与自定义格式，
 * 避免各处零散写 SimpleDateFormat / 手算分秒。所有方法线程安全（每次新建 formatter）。
 */
object TimeUtils {

    /**
     * 笔记时间戳：
     * 同一年 → 日 月  时间（24 小时制，日期与时间间隔两个空格，如 1 AUG  22:18）；
     * 不同年份 → 日 月 年（如 1 AUG 2025）。
     */
    fun smart(ms: Long, now: Long = System.currentTimeMillis()): String {
        val pattern = if (isSameYear(ms, now)) "d MMM  HH:mm" else "d MMM yyyy"
        return format(ms, pattern, Locale.ENGLISH).uppercase(Locale.ENGLISH)
    }

    /** 任意 pattern 自定义格式（locale 默认）。 */
    fun format(ms: Long, pattern: String, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat(pattern, locale).format(Date(ms))

    /** “x 分钟前 / x 小时前 / x 天前 / 刚刚”，用于轻量相对描述。 */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - ms).coerceAtLeast(0)
        val min = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hour = TimeUnit.MILLISECONDS.toHours(diff)
        val day = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            min < 1 -> "Just now"
            min < 60 -> "${min} min ago"
            hour < 24 -> "${hour} h ago"
            day < 30 -> "${day} d ago"
            else -> format(ms, "yyyy-MM-dd")
        }
    }

    // ── 内部判定 ──────────────────────────────────────────────────────────────

    private fun isSameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}
