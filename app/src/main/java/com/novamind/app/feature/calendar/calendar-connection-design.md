# 日历连接状态与产品需求设计

> 模块：`feature/calendar`、`data/calendar`、`common/google`
> 方案：设备端直连 Google Calendar + Google Tasks（**账号跟随登录账户**：用 App 登录邮箱 + `GoogleAuthUtil.getToken`，scope = `calendar.readonly` + `tasks`（读写，右滑完成任务需要），不弹账号选择器）
> 内容：**活动**(Calendar events) 与 **任务**(Google Tasks) 是两套 API，合并成一条按时间排序的议程 `agenda`；任务的 due 仅到日期。
> 缓存：**持久化、按 Google 账号隔离**
> 关键约束：取到的是 **~1h 过期的 access token，没有 refresh token**；"保持登录"靠为**已绑定账号**重新 `GoogleAuthUtil.getToken`（已授权静默返回，未授权/被撤销抛 `UserRecoverableAuthException` → 需用户同意）。
>
> 为什么不用 Identity `AuthorizationClient`：它按 scope 申请、静默复用系统已记住的账号，导致换账号拿到的还是旧账号。改用「为指定账号（当前登录账户）取 token」后是确定性的。**不弹账号选择器**：日历账户始终 = App 登录账户（`AppUserProvider.currentUserKey`），与"登录账户↔日历账户一致性"一致。

---

## 1. 核心概念与边界

四个相互独立、**不可混为一谈**的东西，是整套设计的地基：

| 概念 | 归属 | 生命周期 | 谁能销毁 |
|---|---|---|---|
| **App 登录态**（Auth0） | Novie 账号 | App 会话 | 用户在 App 内登出 |
| **Google 授权（grant）** | Google 账号 ↔ 本 App 的 OS 级授权 | 跨重装存在，直到被撤销 | 用户在 Google 账号设置 / 调 revoke 端点 |
| **access token** | 由 grant 派生 | ~1 小时 | 过期自动失效 / 本地清除 |
| **日历绑定 + 缓存** | 本地，绑定到某个 Google 账号 id | 直到断开 / 换账号 | App 内"断开"或"换账号" |

由此推出用户给的四条规则：

- **退出登录 ≠ 取消授权**：App 账号登出**不** revoke Google grant；只清本地 token+缓存（防止下一个登录用户看到上个账号的日历）。Google grant 保留，重新登录可静默恢复。
- **断开 Calendar = 删除 token + 删除绑定 + 清缓存**：本地三件套全清，状态回 `NOT_CONNECTED`。**不** revoke（用户想再连还能静默连上，无需重新同意）。
- **切换账号 ≠ 复用旧日历数据**：换账号前必须清掉旧账号缓存，绝不允许 B 账号界面里出现 A 账号的事件。
- **换 Google 账号 = 重新授权 + 重新同步**：revoke/解绑旧账号 → 拉起同意流程（选新账号）→ 清缓存 → 全量重新拉取。

---

## 2. CalendarConnectionStatus 状态语义

状态枚举是**页面唯一的渲染依据**（single source of truth），UI 只看 `connectionStatus` 决定主视图。

| 状态 | 含义 | 主视图 | 用户可做 |
|---|---|---|---|
| `NOT_CONNECTED` | 从未连接，或已断开 | 连接卡片 | 点击「连接 Google 日历」 |
| `SYNCING` | 已授权，正在拉取事件 | 有缓存→列表+刷新条；无缓存→骨架屏 | 等待（可取消） |
| `CONNECTED` | 已授权且 token 有效，事件已就绪 | 事件列表 | 切日期 / 刷新 / 断开 / 换账号 |
| `TOKEN_EXPIRED` | token 过期，**可静默续期** | 通常瞬态，沿用旧列表+轻提示 | 无（自动续期） |
| `PERMISSION_REVOKED` | grant 被撤销（Google 设置里取消 / revoke 后） | 重新授权卡片 | 重新连接（需重走同意） |
| `SYNC_FAILED` | 非授权类失败（网络/服务端） | 错误态 + 重试 | 重试 |

设计要点：

- `SYNCING` 与"已连接"语义有重叠——刷新时其实仍是已授权。处理办法是 **事件列表 `events` 独立存放在 UiState 里**，`SYNCING` 时若 `events` 非空就继续展示旧列表 + 顶部刷新指示，避免白屏。
- `TOKEN_EXPIRED` 多为**瞬态过渡**：检测到 401 → 进此态 → 立即静默 `authorize()`。成功就回 `SYNCING`；若静默返回 `NeedsConsent`（说明 grant 已没了）→ 转 `PERMISSION_REVOKED`。
- `PERMISSION_REVOKED` 与 `NOT_CONNECTED` 的区别：前者**曾经绑定过**（绑定记录还在，只是授权失效），文案应是"授权已失效，请重新连接"，而非首次引导。

---

## 3. 状态机转移表

```
                ┌───────────────┐
   首次/已断开  │ NOT_CONNECTED │
                └───────┬───────┘
            点 Connect → 同意 → 拿到 token
                        ▼
                   ┌─────────┐  拉取成功   ┌───────────┐
                   │ SYNCING │ ──────────▶ │ CONNECTED │
                   └────┬────┘             └─────┬─────┘
            网络/服务端失败│                切日期/刷新│
                        ▼                         ▼
                ┌─────────────┐              （回到 SYNCING）
                │ SYNC_FAILED │ ── 重试 ──▶ SYNCING
                └─────────────┘
                                    401 过期 │
                          CONNECTED ─────────▼
                                   ┌───────────────┐
                                   │ TOKEN_EXPIRED │
                                   └───────┬───────┘
                       静默 authorize 成功 │ │ 静默返回 NeedsConsent
                                  SYNCING ◀┘ ▼
                                   ┌────────────────────┐
              403/撤销检测 ───────▶│ PERMISSION_REVOKED │
                                   └─────────┬──────────┘
                              用户重新连接(走同意)│
                                            ▼ SYNCING
```

完整转移表：

| 当前状态 | 触发 | 动作 | 目标状态 |
|---|---|---|---|
| `NOT_CONNECTED` | 点 Connect → 同意成功 | 写 token、写绑定、写 connected 标记 | `SYNCING` |
| `SYNCING` | 拉取成功 | 写入账号缓存、更新 events | `CONNECTED` |
| `SYNCING` | 网络/5xx 失败 | 保留旧 events | `SYNC_FAILED` |
| `SYNCING` | 401 | — | `TOKEN_EXPIRED` |
| `CONNECTED` | 切日期 / 刷新 | — | `SYNCING` |
| `CONNECTED` | 请求 401 | — | `TOKEN_EXPIRED` |
| `CONNECTED` | 请求 403 / 检测撤销 | 清 token | `PERMISSION_REVOKED` |
| `TOKEN_EXPIRED` | 静默 authorize → Authorized | 写新 token | `SYNCING` |
| `TOKEN_EXPIRED` | 静默 authorize → NeedsConsent | 清 token | `PERMISSION_REVOKED` |
| `SYNC_FAILED` | 重试 | — | `SYNCING` |
| `PERMISSION_REVOKED` | 用户重新连接 → 同意成功 | 写新 token | `SYNCING` |
| 任意 | **断开 Calendar** | 删 token + 删绑定 + 清缓存 | `NOT_CONNECTED` |
| 任意 | **重新授权当前账户** | revoke 旧 + 清旧缓存 → 用登录账户重取 token | `SYNCING` |

### App 冷启动 / 进入页面的进入逻辑

```
读取本地绑定:
  有绑定记录(含账号邮箱):
    一致性校验后静默 GoogleAuthUtil.getToken(boundAccount):
      成功    → 写 token → 拉取 → CONNECTED / SYNC_FAILED
      需要同意 → PERMISSION_REVOKED        （之前连过但授权没了）
  无绑定记录:
    未登录(登录态未就绪)         → NOT_CONNECTED   （显示首次引导卡片）
    已登录 → 静默探测 getToken(登录账户):
      成功    → 建立绑定 → 拉取 → CONNECTED   （已授权日历读取 ⇒ 自动连接）
      需要同意/失败 → NOT_CONNECTED          （未授权，等用户主动连接触发同意）
```

要点：静默 `getToken(account)` 在已授权时**不弹 UI**，进入页时调用是安全的；用户主动连接时直接用登录账户取 token（**不弹账号选择器**），仅在 `getToken` 抛 `UserRecoverableAuthException`（首次授权 calendar 范围）时 `launch` 其恢复意图（OAuth 同意页）。这就实现了"已登录则不需重连、自动刷新"。

---

## 4. 状态、事件与数据模型

### 4.1 UiState 重构

`isConnected` / `isLoading` 两个布尔被 `connectionStatus` 取代，所有派生量从枚举推导：

```kotlin
data class CalendarUiState(
    val connectionStatus: CalendarConnectionStatus = CalendarConnectionStatus.NOT_CONNECTED,
    val account: GoogleAccount? = null,        // 当前绑定账号(邮箱/头像)，用于"换账号"展示
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList(),
    val morningExpanded: Boolean = false,
    val afternoonExpanded: Boolean = false,
    val errorMessage: String? = null,          // 一次性提示，消费后置空
) {
    val showConnectCard  get() = connectionStatus == NOT_CONNECTED
    val needsReconnect   get() = connectionStatus == PERMISSION_REVOKED
    val isSyncing        get() = connectionStatus == SYNCING
    val showEvents       get() = connectionStatus == CONNECTED ||
                                 (connectionStatus == SYNCING && events.isNotEmpty())
    // 现有派生量保持不变
    val meetingCount get() = events.count { it.isMeeting }
    val todoCount    get() = events.count { !it.isMeeting }
    val morningEvents   get() = events.filter { it.isMorning }
    val afternoonEvents get() = events.filter { !it.isMorning }
}
```

### 4.2 事件入口

在现有 `CalendarUiEvent` 上新增三个：

```kotlin
data object Disconnect      : CalendarUiEvent   // 断开 Calendar
data object SwitchAccount   : CalendarUiEvent   // 换 Google 账号
data object Retry           : CalendarUiEvent   // SYNC_FAILED 重试(也可复用 Refresh)
// 既有: Connect / GoogleTokenObtained / AuthFailed / DateSelected / PrevDay / NextDay
//       ToggleMorning / ToggleAfternoon / Refresh / ErrorShown
```

`GoogleTokenObtained` 建议带上账号信息（`token` + `accountEmail/id`），以便写绑定和给缓存分区。

### 4.3 三层数据模型

| 层 | 存储 | 内容 | 谁写 | 谁清 |
|---|---|---|---|---|
| **token** | 内存 `GoogleTokenProvider` | access token（敏感，不落盘） | 授权/静默续期成功 | 过期/登出/断开 |
| **绑定 binding** | `KeyValueStore`(MMKV) | `connected=true`、`accountId`、`accountEmail` | 首次连接成功 | 断开 / 换账号 |
| **事件缓存 cache** | 持久化，**key 带 accountId** | 按 `accountId + 日期` 缓存事件 | 每次拉取成功 | 断开 / 换账号 / 登出 |

缓存按账号 id 分区是"切换账号不复用旧数据"的硬保证：读缓存永远只读当前绑定 `accountId` 下的分区，换账号写入新分区前先删旧分区。

> 架构边界：`GoogleAuthUtil` 依赖 `Context`。**不要把 Context 泄进 ViewModel**——用轻接口 `GoogleCalendarAuthSource { fetchToken(account); clearToken(token); revoke(token) }` 封装，ViewModel 依赖接口（实现由 Application 注入），同意恢复意图的 ActivityResult 在 Route 启动。账号名取自 `AppUserProvider.currentUserKey`，无需选择器。

---

## 5. 四类销毁操作对照（产品需求核心）

| 操作 | 入口 | 清 access token | 删本地绑定 | 清账号缓存 | revoke Google grant | 结束状态 | 重新进入时 |
|---|---|:--:|:--:|:--:|:--:|---|---|
| **App 退出登录**（Auth0） | 设置/账号 | ✅ | ✅(当前用户) | ✅ | ❌ | App 登录页 | 重新登录后**静默恢复**日历(grant 还在) |
| **断开 Calendar** | 日历页/设置 | ✅ | ✅ | ✅ | ❌ | `NOT_CONNECTED` | 点连接可**免同意静默连上** |
| **换 Google 账号** | 日历页 | ✅ | ✅(旧) | ✅(旧) | ✅(旧号) | `SYNCING`(新号) | 新账号数据 |
| **取消授权** | Google 账号设置(App 外) | (失效) | — | — | ✅(用户侧) | App 内下次请求→`PERMISSION_REVOKED` | 需重新同意 |

关键差异一句话版：

- **登出**只动"本设备本地状态"，不碰 Google 那侧的授权——所以登出再登录能无感恢复。
- **断开**比登出多删绑定、回到首次态，但仍**不** revoke，方便用户反悔。
- **重新授权当前账户**：清旧 token 缓存 + revoke 旧 grant + 清旧缓存，再用**登录账户**重新 `getToken` 后全量重同步。日历账户始终跟随 App 登录账户，无账号选择器；要换日历账户需切换 App 登录账户。
- **取消授权**发生在 App 之外，App 只能被动检测（403 / 静默 `getToken` 抛 `UserRecoverableAuthException`）并转 `PERMISSION_REVOKED`。

---

## 6. 错误 → 状态映射

| 来源 | 现象 | 映射 |
|---|---|---|
| Calendar API 401 | token 过期 | `TOKEN_EXPIRED` → 静默续期 |
| Calendar API 403 / 静默 authorize 返回 NeedsConsent | grant 被撤销 | `PERMISSION_REVOKED` |
| 网络超时 / 5xx / 解析错误 | 拉取失败 | `SYNC_FAILED` |
| 同意流程被用户取消 | `AuthFailed` | 维持原态（`NOT_CONNECTED` / `PERMISSION_REVOKED`），仅弹一次性 `errorMessage` |

`data/calendar` 里已有的 `GoogleAuthExpiredException` 用来承载 401；建议再加一个 `GoogleAuthRevokedException` 区分 403，让 ViewModel 能精确落到不同状态。

---

## 7. 验收标准（可直接转测试用例）

1. 已连接用户冷启动 App：进入日历页**不出现**连接卡片，直接看到（缓存）事件并在后台刷新 → 最终 `CONNECTED`。
2. token 过期（模拟 401）：界面不退回未连接，旧列表保留，静默续期后无感刷新。
3. 在 Google 账号设置里取消授权后回到 App：下次请求落到 `PERMISSION_REVOKED`，显示"重新连接"，点击需重新同意。
4. App 退出登录再用**另一个** App 账号登录：看不到上一个用户的任何日历事件（缓存已清）。
5. App 退出登录再用**同一** App 账号登录：日历自动恢复，无需重新点连接（grant 未 revoke）。
6. 断开 Calendar：回到首次连接态；token/绑定/缓存均被清；再次连接走静默路径、无需重新同意。
7. 重新授权当前账户：先 revoke 旧 token、清旧缓存，用登录账户重新取 token 并重同步；切换 App 登录账户后日历账户随之变更。
8. 弱网首次连接：进入 `SYNC_FAILED` 且提供重试；重试成功转 `CONNECTED`。

---

## 7.1 登录账户 ↔ 日历账户一致性（软）

日历会话绑定到 **App 登录用户**（按邮箱隔离），保证三件事：

1. **退出登录 → 清日历**：所有登出 / 退出路径（`logoutLocal` / `logoutFederated` / `exitGuest`）都调用 `NovieApplication.clearCalendarLocalSession()`——删 token + 删绑定 + 清缓存。不 revoke Google grant（退出登录 ≠ 取消授权），重新登录同账号可静默恢复。
2. **切换账号 → 更新日历**：`login` 成功时若新用户 ≠ 上次登录用户，先清掉旧用户的日历绑定/缓存；进入日历页（`refreshAuthAndLoad`）再校验「绑定时的 App 用户 == 当前登录用户」，不一致则清日历回未连接，由新用户重新连接自己的日历。
3. **一致性载体**：当前登录用户由 `AppUserProvider.currentUserKey`（内存，认证层维护）提供；绑定记录额外存 `appUserKey`（连接时的登录邮箱）。两处比对即软一致性，不强制 Google 邮箱等于 App 邮箱（允许用公司号登录、连个人 Google 日历）。
4. **游客（免登录）不可用日历**：游客没有 App 账户，`AppUserProvider.isGuest = true`。日历页进入时直接置 `LOGIN_REQUIRED`，不触发任何授权/拉取，UI 显示「登录后使用」拦截态（隐藏统计卡片与时段列表），不展示 Google 连接卡片。退出登录后再以游客进入也走此分支。

> 触发点：认证层在登录 / 续期 / 服务端刷新时 `AppUserProvider.setUser()`，游客模式 `setGuest()`，登出 / 退游客 `clear()` 并清日历会话。`AppUserProvider.session` 是 `StateFlow`，`CalendarViewModel` 订阅它——会话一变（如游客→登录）即重新评估，**不会因 VM 被保留而停留在旧态**（修复"登录后日历仍显示游客态"）。日历层据此做游客拦截 + 不一致检测，在连接成功时写入 `appUserKey`。

---

## 8. 改动清单（实现时对照）

- `CalendarConnectionStatus.kt`（新增枚举）
- `CalendarUiState.kt`：用 `connectionStatus` + `account` 取代 `isConnected`/`isLoading`，加派生量
- `CalendarUiEvent`：加 `Disconnect` / `SwitchAccount` / `Retry`；`GoogleTokenObtained` 带账号
- `CalendarViewModel`：`init` 改静默 `refreshAuthAndLoad()`；实现登出/断开/换账号的清理编排；错误分流到对应状态
- `GoogleCalendarAuthManager`：账号跟随登录账户——`fetchToken(account)`（`GoogleAuthUtil.getToken`，`UserRecoverableAuthException`→`NeedsConsent`）、`clearToken`、`revoke`（`POST oauth2.googleapis.com/revoke`）；抽 `GoogleCalendarAuthSource` 接口。无账号选择器，账号名取 `AppUserProvider.currentUserKey`
- `data/calendar`：加按账号分区的持久化事件缓存；加 `GoogleAuthRevokedException`
- 绑定存储：基于现有 `KeyValueStore`，新建一个 `CalendarBindingStore`（独立 mmapID）
