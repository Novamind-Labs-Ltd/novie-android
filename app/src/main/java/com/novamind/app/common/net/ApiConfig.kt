package com.novamind.app.common.net

import android.content.Context
import com.novamind.app.common.storage.KeyValueStore
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 后端域名（环境）配置。
 *
 * 两个正交维度：
 * - **环境（[Env]）**：唯一的切换开关，一次只选一套（co.nz / co）。
 * - **用途（[Endpoints]）**：每套环境内并存的三类域名——auth（认证）、chat（聊天）、api（通用接口）。
 *
 * 切环境时三类域名整体一起变，杜绝「生产 auth + 测试 api」之类的跨环境错配。
 * 选择会持久化；[NetworkModule] 的 Retrofit 懒加载，切换**下次冷启动生效**。
 *
 * 用前需在 Application.onCreate 调用 [init]。
 */
object ApiConfig {

    enum class Env(val label: String) { CO_NZ("co.nz"), CO("co") }

    /** 一套环境下的三类域名（均以 "/" 结尾，便于与相对路径拼接）。 */
    data class Endpoints(
        val auth: String,   // 认证：如 {auth}/api/auth/me
        val chat: String,   // 聊天
        val api: String,    // 通用接口
    )

    private val TABLE: Map<Env, Endpoints> = mapOf(
        Env.CO_NZ to Endpoints(
            auth = "https://auth.novamind-labs.co.nz/",
            chat = "https://chat.novamind-labs.co.nz/",
            api = "https://api.novamind-labs.co.nz/",
        ),
        // 注意：.co 的三个地址按 co.nz 的子域规律推断，如与后端实际不符请更正。
        Env.CO to Endpoints(
            auth = "https://auth.novamind-labs.co/",
            chat = "https://chat.novamind-labs.co/",
            api = "https://api.novamind-labs.co/",
        ),
    )

    private val DEFAULT = Env.CO_NZ

    private const val PREFS_NAME = "api_config"
    private const val KEY_ENV = "env"

    private lateinit var store: KeyValueStore

    private val _env = MutableStateFlow(DEFAULT)
    /** 当前环境（响应式）。 */
    val envFlow: StateFlow<Env> = _env.asStateFlow()

    /** 当前环境（同步取值）。 */
    val env: Env get() = _env.value

    /** 当前环境下的三类域名。 */
    val endpoints: Endpoints get() = TABLE.getValue(_env.value)

    /** 便捷取值。 */
    val authBaseUrl: String get() = endpoints.auth
    val chatBaseUrl: String get() = endpoints.chat
    val apiBaseUrl: String get() = endpoints.api

    /** 在 Application.onCreate 调用（需在 MMKV.initialize 之后），载入已持久化的环境选择。 */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        store = MmkvStore(PREFS_NAME)
        val saved = store.getString(KEY_ENV, null)
        // 仅接受枚举内的值，脏数据回退默认。
        _env.value = Env.entries.firstOrNull { it.name == saved } ?: DEFAULT
    }

    /** 切换环境并持久化。 */
    fun select(env: Env) {
        _env.value = env
        store.putString(KEY_ENV, env.name)
    }
}
