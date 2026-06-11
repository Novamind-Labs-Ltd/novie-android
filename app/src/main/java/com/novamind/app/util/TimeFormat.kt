package com.novamind.app.util

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat

/**
 * 时间格式化工具。集中处理应用内的时长、时钟、相对日期与自定义格式，
 * 避免各处零散写 SimpleDateFormat / 手算分秒。所有方法线程安全（每次新建 formatter）。
 */
object TimeFormat {

    /**
     * 时长格式化（用于录音、音频进度等）。
     * < 1 小时 → mm:ss（如 03:07）；≥ 1 小时 → H:mm:ss（如 1:02:09）。
     */
    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 时钟（24 小时制）：HH:mm，如 09:05。 */
    fun clock(ms: Long = System.currentTimeMillis()): String =
        format(ms, "HH:mm")

    /**
     * 笔记/列表用的相对时间：
     * 今天 → Today HH:mm；昨天 → Yesterday HH:mm；今年内 → MMM d（如 Jun 12）；
     * 跨年 → MMM d, yyyy。
     */
    fun relative(ms: Long): String = when {
        isSameDay(ms, System.currentTimeMillis()) -> "Today " + format(ms, "HH:mm")
        isYesterday(ms) -> "Yesterday " + format(ms, "HH:mm")
        isSameYear(ms, System.currentTimeMillis()) -> format(ms, "MMM d")
        else -> format(ms, "MMM d, yyyy")
    }

    /** 紧凑日期时间：MM-dd HH:mm（如 06-12 14:26）。 */
    fun shortDateTime(ms: Long): String = format(ms, "MM-dd HH:mm")

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
            min < 1 -> "刚刚"
            min < 60 -> "${min}分钟前"
            hour < 24 -> "${hour}小时前"
            day < 30 -> "${day}天前"
            else -> format(ms, "yyyy-MM-dd")
        }
    }

    // ── 内部判定 ──────────────────────────────────────────────────────────────

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(ms: Long): Boolean {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(ms, yesterday.timeInMillis)
    }

    private fun isSameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}
