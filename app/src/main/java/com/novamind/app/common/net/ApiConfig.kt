package com.novamind.app.common.net

import android.content.Context
import com.novamind.app.BuildConfig
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
 * 默认环境与是否允许切换由 product flavor 注入（[BuildConfig.DEFAULT_ENV] /
 * [BuildConfig.ENV_SWITCHABLE]）：dev 默认 test 且可切；prod 默认 prod 且锁死。
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

    /** 每套环境的 ask-novie agent 域名（以 "/" 结尾）。与 api 独立部署，走 agents 子域。 */
    private val AGENT_TABLE: Map<Env, String> = mapOf(
        Env.TEST to "https://agents.mynovie.novamind-labs.co.nz/",
        Env.PROD to "https://agents.novamind-labs.co/",
    )

    /** flavor 注入的默认环境；脏值回退 test。 */
    private val DEFAULT: Env =
        Env.entries.firstOrNull { it.name == BuildConfig.DEFAULT_ENV } ?: Env.TEST

    /** flavor 注入：是否允许运行时切换环境（prod 为 false）。切换入口 UI 应据此隐藏/禁用。 */
    val isSwitchable: Boolean = BuildConfig.ENV_SWITCHABLE

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

    /** 当前环境的 ask-novie agent 域名（SSE 聊天，路径 v1/chat）。走独立 agents 子域。 */
    val agentBaseUrl: String get() = AGENT_TABLE.getValue(_env.value)

    /** 在 Application.onCreate 调用（需在 MMKV.initialize 之后），载入已持久化的环境选择。 */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        store = MmkvStore(PREFS_NAME)
        // prod 锁死：忽略持久化，恒为 flavor 默认；dev 才读取已保存选择。
        _env.value = if (!isSwitchable) {
            DEFAULT
        } else {
            val saved = store.getString(KEY_ENV, null)
            // 仅接受枚举内的值，脏数据回退默认。
            Env.entries.firstOrNull { it.name == saved } ?: DEFAULT
        }
    }

    /** 切换环境并持久化。prod（[isSwitchable] = false）为空操作。 */
    fun select(env: Env) {
        if (!isSwitchable) return
        _env.value = env
        store.putString(KEY_ENV, env.name)
    }
}
