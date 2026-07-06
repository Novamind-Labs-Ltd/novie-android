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
 * 单一维度：**环境（[Env]）**——一次只选一套（test / prod），每套仅一个 api 域名。
 * 选择会持久化；[NetworkModule] 的 Retrofit 懒加载，切换**下次冷启动生效**。
 *
 * 用前需在 Application.onCreate 调用 [init]。
 */
object ApiConfig {

    enum class Env(val label: String) { TEST("test"), PROD("prod") }

    /** 每套环境的 api 域名（以 "/" 结尾，便于与相对路径拼接）。 */
    private val TABLE: Map<Env, String> = mapOf(
        Env.TEST to "https://api.mynovie.novamind-labs.co.nz/",
        Env.PROD to "https://api.novamind-labs.co/",
    )

    private val DEFAULT = Env.TEST

    private const val PREFS_NAME = "api_config"
    private const val KEY_ENV = "env"

    private lateinit var store: KeyValueStore

    private val _env = MutableStateFlow(DEFAULT)
    /** 当前环境（响应式）。 */
    val envFlow: StateFlow<Env> = _env.asStateFlow()

    /** 当前环境（同步取值）。 */
    val env: Env get() = _env.value

    /** 当前环境的 api 域名。 */
    val apiBaseUrl: String get() = TABLE.getValue(_env.value)

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
