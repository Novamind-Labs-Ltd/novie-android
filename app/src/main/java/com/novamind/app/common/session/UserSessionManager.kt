package com.novamind.app.common.session

import android.os.SystemClock
import com.novamind.app.common.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 全局唯一的用户会话真源（方案2 · 单一 object，见 user-session-design.md §三）。
 *
 * **薄壳**：只负责「持 [session] 状态 + 认证态迁移 + 读写 MMKV + 刷新节流」；
 * 拉 `/me`、DTO→领域映射等重逻辑在可注入的 [IdentityRepository]。
 *
 * 全局访问：
 * - DI / Compose 场景：`UserSessionManager.session.collectAsStateWithLifecycle()`（object 无需注入）。
 * - 非 DI / 同步场景（拦截器、Worker 等）：[current] 同步读。
 *
 * 测试隔离：进程级可变单例，测试间用 [reset] 在 `@Before`/`@After` 清理。
 */
object UserSessionManager {

    private val _session = MutableStateFlow(UserSession(AuthStatus.UNKNOWN))
    val session: StateFlow<UserSession> = _session.asStateFlow()

    /** 同步快照（非 DI / 同步场景直接读，如拦截器）。 */
    val current: UserSession get() = _session.value

    // 持久化与刷新逻辑的协作者。object 无法构造注入，暴露为可替换字段供单测替身。
    @Volatile
    var store: UserSessionStore = UserSessionStore()

    @Volatile
    var repository: IdentityRepository = IdentityRepository()

    // 上次成功刷新时刻（单调时钟，不受系统时间调整影响）。0 表示尚未刷新过。
    @Volatile
    private var lastRefreshAt: Long = 0L

    // 串行化刷新，避免登录后与回前台并发重复拉 /me。
    private val refreshMutex = Mutex()

    /** 冷启动：读 MMKV 缓存 → 置 [AuthStatus.CHECKING] 并带上缓存档案，UI 可先渲染。 */
    fun loadCached() {
        val cached = store.load()
        _session.value = UserSession(
            status = AuthStatus.CHECKING,
            registered = cached?.registered ?: false,
            profile = cached?.toProfile(),
        )
    }

    /** `/me` 结果写入：更新 profile + registered 并落 MMKV，刷新节流时间戳。 */
    fun applyProfile(profile: UserProfile?, registered: Boolean) {
        _session.update {
            it.copy(
                registered = registered,
                profile = profile ?: it.profile,
                // 后端 email 可校正 userKey（登录时可能仅有 Auth0 email）；无 profile 时保留原值。
                userKey = profile?.email ?: it.userKey,
            )
        }
        store.save(profile, registered)
        lastRefreshAt = SystemClock.elapsedRealtime()
    }

    /**
     * 登录 / 续期成功：迁 [AuthStatus.AUTHENTICATED] 并记录登录账户 [email]（登录即刻可得，
     * 供日历等做账户一致性校验，不依赖 /me）。保留缓存档案，待 /me 校正。
     */
    fun onLoggedIn(email: String?) {
        _session.update { it.copy(status = AuthStatus.AUTHENTICATED, userKey = email) }
    }

    /** 进入游客态：清档案，不落缓存。 */
    fun onGuest() {
        _session.value = UserSession(AuthStatus.GUEST)
    }

    /** 判定无会话：迁未登录，清缓存。 */
    fun onLoggedOut() {
        reset()
        _session.value = UserSession(AuthStatus.UNAUTHENTICATED)
    }

    /**
     * 从后端刷新档案。刷新时机策略集中在此（见设计文档 §十）：
     * - [force] = true（手动刷新，如下拉）：绕过节流强制拉取。
     * - [force] = false（回前台等）：仅当已登录且距上次成功刷新 ≥ [MIN_REFRESH_INTERVAL_MS] 才拉。
     *
     * 失败（网络/业务）静默保留 MMKV 缓存，不阻断 UI；鉴权失效则迁未登录（由调用方据返回触发重登）。
     *
     * @return true 表示实际发起了刷新（无论成败），false 表示被节流跳过。
     */
    suspend fun refreshFromServer(force: Boolean): Boolean {
        if (!force) {
            if (current.status != AuthStatus.AUTHENTICATED) return false
            val elapsed = SystemClock.elapsedRealtime() - lastRefreshAt
            if (lastRefreshAt != 0L && elapsed < MIN_REFRESH_INTERVAL_MS) return false
        }
        return refreshMutex.withLock {
            when (val outcome = repository.fetchProfile()) {
                is IdentityRepository.Outcome.Profile -> {
                    applyProfile(outcome.profile, registered = true)
                    true
                }
                IdentityRepository.Outcome.NotRegistered -> {
                    applyProfile(profile = null, registered = false)
                    true
                }
                IdentityRepository.Outcome.Unauthorized -> {
                    AppLog.w(TAG) { "刷新得鉴权失效 → 迁未登录" }
                    onLoggedOut()
                    // 上抛给认证层，真正弹回登录页（onLoggedOut 只改全局会话，不触发登录门控）。
                    AuthSessionSignal.notifySessionExpired()
                    true
                }
                IdentityRepository.Outcome.Failed -> {
                    // 保留缓存兜底，不改认证态；不更新 lastRefreshAt 以便尽快重试。
                    true
                }
            }
        }
    }

    /** 清空（登出 / 单测隔离）：清缓存 + 复位状态与节流。 */
    fun reset() {
        store.clear()
        lastRefreshAt = 0L
        _session.value = UserSession(AuthStatus.UNKNOWN)
    }

    private const val TAG = "UserSession"

    /** 回前台刷新的最小间隔（毫秒）。默认 5 分钟，避免频繁切换 App 反复打 /me。 */
    private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
}
