# 用户登录信息全局管理设计（UserSession）

- **日期**：2026-07-04
- **目标**：把「当前登录用户信息」做成**全局单一数据源**，任意模块可读、可观察；冷启动即有缓存、登录后由 `/api/v1.0/me` 校正。
- **数据来源**：后端 `GET /api/v1.0/me`（见 `api-reference.md` §2、`my-novie-backend` MeController）：

```json
"data": { "registered": true, "userId": "3f...uuid", "displayName": "Jane", "email": "jane@example.com", "avatarUrl": null }
```

- **相关**：`data-layering-design.md`（DTO/Domain/UiState 分层）、`user-center-design.md`、现有 `AuthViewModel` / `AppUserProvider` / `ProfileStore` / `TokenProvider`。

---

## 一、目标与原则

1. **单一数据源（SoT）**：全 App 只有一处持有「当前用户」，其它模块只订阅，不各自缓存。
2. **全局可调用**：UI（Compose 订阅）、ViewModel、后台（Worker/拦截器等非 DI 场景）都能取到。
3. **离线优先展示**：用户信息持久化到 MMKV，冷启动先显示缓存，再异步用 `/me` 刷新。
4. **分层纯净**：网络 `*Dto`、领域无后缀、UI `*UiState`；领域模型不带序列化/框架类型（遵循 data-layering）。
5. **收敛现状**：统一/吸收现有零散来源（`AppUserProvider`、`AuthUiState` 里的 userName/email/picture），避免多份「当前用户」。

---

## 二、分层模型

**网络 DTO**（`common/net`，新增）——对齐 `/api/v1.0/me` 响应（信封由 `apiCall` 处理）：

```kotlin
@Serializable
data class MeProfileDto(
    val registered: Boolean = false,
    val userId: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
)

interface IdentityApi {
    @GET("api/v1.0/me")
    suspend fun me(): Response<ApiResponse<MeProfileDto>>
}
```
> 注意与既有 `AuthApi.me`(=`/api/auth/me`, Auth0 claims) 区分，命名上用 `IdentityApi` / `MeProfileDto`。

**领域模型**（`common/session`，纯 Kotlin）：

```kotlin
/** 全局登录用户档案（来自 /api/v1.0/me）。 */
data class UserProfile(
    val userId: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
)

/** 全局用户会话：认证态 + 档案的合并视图，单一数据源。 */
data class UserSession(
    val status: AuthStatus,          // 认证态
    val userKey: String? = null,     // 登录账户 key（邮箱），登录即刻可得、不依赖 /me；收敛自旧 AppUserProvider
    val registered: Boolean = false, // 后端是否已建档
    val profile: UserProfile? = null,// 已注册用户档案（含头像 avatarUrl，以后端为准）
) {
    val isLoggedIn get() = status == AuthStatus.AUTHENTICATED
    val isGuest get() = status == AuthStatus.GUEST
}

enum class AuthStatus { UNKNOWN, CHECKING, GUEST, UNAUTHENTICATED, AUTHENTICATED }
```

**UI 状态**：各页自己的 `XxxUiState` 从 `UserSession` 映射所需字段（不直接把 DTO/Session 传进深层 Composable）。

---

## 三、全局持有者：UserSessionManager（方案2 · 单一 object）

**采用「全局一份」**：`UserSessionManager` 是一个进程级 `object` 单例（与现有 `AppUserProvider`/`TokenProvider`/`ProfileStore`/`AppLog`/`ApiConfig` 同一套约定），作为**唯一真源**，以 `StateFlow<UserSession>` 暴露。不做「主源 + 镜像」两份，`AppUserProvider` 直接**并入/废弃**。

```kotlin
/** 全局唯一的用户会话真源。薄壳：只持状态 + 迁移 + 读写 MMKV；重逻辑在 IdentityRepository。 */
object UserSessionManager {
    private val _session = MutableStateFlow(UserSession(AuthStatus.UNKNOWN))
    val session: StateFlow<UserSession> = _session.asStateFlow()

    /** 同步快照（非 DI/同步场景直接读，如拦截器）。 */
    val current: UserSession get() = _session.value

    fun loadCached(store: UserSessionStore)                 // 冷启动：读 MMKV → 缓存态
    fun applyProfile(profile: UserProfile?, registered: Boolean) // /me 结果写入 + 落 MMKV
    fun onLoggedIn(email: String?) / onGuest() / onLoggedOut()   // 认证层驱动状态迁移
    fun reset()                                             // 清空（登出 + 单测隔离）
}
```

- **全局访问，一份到底**：
  - **DI / Compose 场景**：直接 `UserSessionManager.session.collectAsStateWithLifecycle()` 观察（object 无需注入）。
  - **非 DI / 同步场景**（`AuthInterceptor`、Worker 等）：`UserSessionManager.current` 同步读，无需 EntryPoint 绕路。
- **薄壳 + 可注入 Repository**：`UserSessionManager` 只做「持 `StateFlow` + 状态迁移 + 读写 MMKV」这层薄壳；**拉 `/me`、DTO→领域映射等业务逻辑放可注入的 `IdentityRepository`**（Hilt，可测/可替身）。这样既全局一份、拦截器可同步取，又把值得测的逻辑留在可注入处。
- **测试隔离**：object 是进程级可变单例，测试间会串状态——用 `reset()` 在 `@Before`/`@After` 清理；因逻辑已下沉到 `IdentityRepository`，`UserSessionManager` 本身几乎无需替身。

---

## 四、持久化（冷启动即有）

`UserSessionStore`（MMKV，仿 `ProfileStore`）：缓存 `userId/displayName/email/avatarUrl/registered`，**不存 token**（token 仍走 `TokenProvider`/安全存储）。

冷启动顺序（`Application.onCreate` 后）：
1. `loadCached()` → 立即得到上次的 `UserSession`（`CHECKING` 或缓存的 `AUTHENTICATED`），UI 先渲染。
2. 认证层判定会话（已有 `AuthViewModel.checkSession`）→ `onLoggedIn/onGuest/onLoggedOut`。
3. 登录后 `refreshFromServer()` 拉 `/me` 校正 `registered/profile` 并写回 MMKV。

---

## 五、状态机（AuthStatus）

```
UNKNOWN ──启动检查──▶ CHECKING ──┬─ 有会话 ─▶ AUTHENTICATED ──/me──▶ (registered=true/false)
                                 ├─ 游客   ─▶ GUEST
                                 └─ 无会话 ─▶ UNAUTHENTICATED
AUTHENTICATED ──登出──▶ UNAUTHENTICATED（清缓存）
```

- `registered=false`（`/me` 上游 204）：已登录但 Novie 侧未建档 → 引导完善资料（见 `user-center-design`）。
- token 失效（`BizError.isAuthExpired`）：`refreshFromServer` 得 401 → 迁 `UNAUTHENTICATED` 并触发重登。

---

## 六、读写路径

**读**（展示）：
```
UI/ViewModel ── collect ──▶ UserSessionManager.session : StateFlow<UserSession>
              头像/昵称/邮箱一律取 profile（**以后端为准**）：头像用 profile.avatarUrl，为空则占位。
              后端用户信息缓存在本地(MMKV)仅用于冷启动/离线兜底，不做「本地优先覆盖」。
```
**刷新**：
```
IdentityApi.me() : Response<ApiResponse<MeProfileDto>>
  → apiCall → ApiResult<MeProfileDto>
  → IdentityRepository 映射 → UserSession(registered, profile)
  → UserSessionManager 更新 _session + 写 MMKV
```
**认证事件驱动**：`AuthViewModel` 登录/续期/登出/游客时调用 `UserSessionManager.onLoggedIn/onGuest/onLoggedOut`，不再把用户信息塞进 `AuthUiState`（改为从 session 派生）。

---

## 七、与现有代码的整合

1. **`AppUserProvider`**：**废弃并入** `UserSessionManager`（不留镜像，全局一份）。其 `currentUserKey/isGuest` 语义由 `UserSession` 派生：日历等消费方改为订阅 `UserSessionManager.session`（响应式）或读 `UserSessionManager.current`（同步）。迁移时逐个替换调用点后删除 `AppUserProvider`。
2. **`AuthViewModel`/`AuthUiState`**：`userName/userEmail/userPicture` 不再各自持有，改从 `UserSessionManager.session` 读；认证动作回调 manager。
3. **头像**：**以后端 `avatarUrl` 为准**，缓存在本地(MMKV)供离线显示，不做本地优先覆盖。`ProfileStore.avatarPath`（本地选图）仅是「编辑/上传中转」——换头像=上传后端→刷新 `/me`→用服务端 URL 显示（依赖后端头像写接口，暂缺）；在写接口就绪前，头像只读后端返回值。
4. **`/api/auth/me`(AuthApi) vs `/api/v1.0/me`(IdentityApi)**：前者验 Auth0 claims、后者取后端档案；本设计以后者为档案源，前者可保留做 token 校验。

---

## 八、边界情况

游客（隐藏账号项）、`registered=false`（引导建档）、离线（用 MMKV 缓存兜底、标记 stale、可重试）、token 失效（转未登录）、多账号切换（登出必须 `reset()` 清缓存 + MMKV，避免串号）。头像统一以后端 `avatarUrl` 为准（缓存仅供离线显示）。

---

## 九、落地清单

- [x] `common/net`：`IdentityApi` + `MeProfileDto`；`NetworkModule.identityApi`。
- [x] `common/session`：`UserProfile`/`UserSession`（含 `userKey`）/`AuthStatus` 领域模型；`IdentityRepository`（`apiCall` + 映射，可注入）；`UserSessionStore`（MMKV）；`UserSessionManager`（**`object` 薄壳 + `reset()` + 回前台节流刷新**）。
- [x] `NovieApplication`/认证层：启动 `UserSessionManager.loadCached()`；`AuthViewModel` 接线 `onLoggedIn(email)/onGuest/onLoggedOut` + 登录后 `refreshFromServer(force=true)` 拉 `/me`；`onAppForegrounded` 节流刷新。
- [x] 收敛：**废弃 `AppUserProvider`**（`CalendarViewModel`/`FileRecordingUploader` 改订阅/读 `UserSessionManager`，已删除 `AppUserProvider.kt`）；`AuthUiState` 移除 `userName/userEmail/userPicture`，`MainActivity` 改从 `UserSessionManager.session` 派生；`ProfileStore.setAvatar` → `setLocalAvatar`。
- [ ] 守卫：领域模型无框架依赖；DTO 仅在网络层；UI 只见 `UserSession`/`XxxUiState`。（待编译/评审确认）

> 落地状态（2026-07-07）：核心基建 + 收敛改造均已落地。`ProfileRepository`/`AuthUser`（Auth0 `/api/auth/me`）暂保留但不再被 `AuthViewModel` 调用——token 校验现由 `/api/v1.0/me`（401→`onLoggedOut`）承担；如确认不再需要可后续删除。未跑构建（沙箱无 Gradle 环境），需本地 `./gradlew compileDebugKotlin` 验证。

