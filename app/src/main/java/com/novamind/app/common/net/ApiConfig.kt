package com.novamind.app.common.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 后端 API 域名（环境）配置。支持在 Debug 工具箱里切换,选择会持久化。
 *
 * 注意：[NetworkModule] 的 Retrofit 在首次使用时读取 [baseUrl] 并缓存,因此切换域名
 * **下次冷启动生效**;若代码在请求时直接读取 [baseUrl]/[ENVIRONMENTS] 则即时生效。
 *
 * 用前需在 Application.onCreate 调用 [init]。
 */
object ApiConfig {

    /** 可选环境：展示名 → base URL（均以 "/" 结尾，便于与相对路径拼接）。 */
    val ENVIRONMENTS: List<Environment> = listOf(
        Environment(label = "co.nz", baseUrl = "https://app.novamind-labs.co.nz/"),
        Environment(label = "co", baseUrl = "https://app.novamind-labs.co/"),
    )

    /** 默认环境：co.nz。 */
    private val DEFAULT = ENVIRONMENTS.first()

    private const val PREFS_NAME = "api_config"
    private const val KEY_BASE_URL = "base_url"

    private lateinit var prefs: android.content.SharedPreferences

    private val _baseUrl = MutableStateFlow(DEFAULT.baseUrl)
    /** 当前选中的 base URL（响应式）。 */
    val baseUrlFlow: StateFlow<String> = _baseUrl.asStateFlow()

    /** 当前选中的 base URL（同步取值，供 [NetworkModule] 等读取）。 */
    val baseUrl: String get() = _baseUrl.value

    /** 在 Application.onCreate 调用，载入已持久化的选择。 */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BASE_URL, null)
        // 仅接受白名单内的值，避免脏数据导致指向未知域名。
        _baseUrl.value = ENVIRONMENTS.firstOrNull { it.baseUrl == saved }?.baseUrl ?: DEFAULT.baseUrl
    }

    /** 切换环境并持久化。传入的 [baseUrl] 必须是 [ENVIRONMENTS] 中之一。 */
    fun select(baseUrl: String) {
        if (ENVIRONMENTS.none { it.baseUrl == baseUrl }) return
        _baseUrl.value = baseUrl
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
    }

    data class Environment(val label: String, val baseUrl: String)
}
