package com.novamind.app.common.log

/**
 * PII 脱敏：日志进管道前统一清洗（落盘即脱敏，宁可多脱不可漏脱）。
 * 覆盖：邮箱、手机号、JWT、带敏感 key 的键值对。
 */
internal object PiiMasker {

    private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
    private val CN_MOBILE = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    private val JWT = Regex("""eyJ[\w-]{10,}\.[\w-]+\.[\w-]+""")

    /** token=xxx / password: xxx 等键值形式，保留 key、抹掉 value。 */
    private val SENSITIVE_KV = Regex(
        """(?i)\b(token|secret|password|passwd|authorization|api[_-]?key)\b(["']?\s*[=:]\s*)\S+""",
    )

    fun mask(input: String): String {
        var s = input
        s = SENSITIVE_KV.replace(s) { m -> "${m.groupValues[1]}${m.groupValues[2]}***" }
        s = JWT.replace(s, "***JWT***")
        s = EMAIL.replace(s, "***@***")
        s = CN_MOBILE.replace(s, "***********")
        return s
    }
}
