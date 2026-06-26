package com.novamind.app.util

/**
 * 邮箱格式校验工具。
 *
 * 用纯正则实现（不依赖 android.util.Patterns），便于单元测试且与 UI 解耦。
 * 规则为常见可用子集：`local@domain.tld`
 * - local：字母数字与 `._%+-`
 * - domain：字母数字与 `.-`，至少一个点，顶级域 ≥2 个字母
 * 校验前会 trim 首尾空白。
 */
object EmailUtils {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    /** 邮箱格式是否合法（自动 trim；空白返回 false）。 */
    fun isValid(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_REGEX.matches(email.trim())
    }
}

/** 便捷扩展：`"a@b.com".isValidEmail()`。 */
fun String?.isValidEmail(): Boolean = EmailUtils.isValid(this)
