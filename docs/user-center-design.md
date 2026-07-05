# 用户中心设计

- **日期**：2026-07-04（基于现有代码设计）
- **落位**：新增 `feature/profile/`（用户中心页），复用 `feature/auth/`、`common/profile/`、`common/session/`、`common/net/`
- **相关**：`http-networking-design.md`、后端 `../../my-novie-backend/doc/api-reference.md`

---

## 一、背景与目标

目前「用户」相关的界面只有一个侧滑抽屉 `ProfileDrawerContent`（头像、昵称、邮箱、权限管理、指纹开关、退出登录/登录注册）。本设计把它**扩展为一个完整的「用户中心」页面**，用于集中查看与维护用户资料、账户与安全、偏好设置及各类入口，并把资料打通到后端（读 `/api/v1.0/me`，写 `members/me`）。

设计原则：**最大化复用现有逻辑**，不重造会话/认证/头像/网络那套已经跑通的机制。

---

## 二、现有可复用基础

| 能力 | 现有实现 | 用户中心如何用 |
|------|----------|----------------|
| 会话/认证状态 | `AuthViewModel` + `AuthUiState`（userName/userEmail/userPicture、isGuest、isAuthenticated、biometricAvailable/Enabled…） | 直接作为用户中心的基础数据源与动作入口 |
| 全局会话身份 | `AppUserProvider`（userKey、isGuest） | 判断游客/登录，联动其它模块 |
| 本地头像 | `ProfileStore`（MMKV 存 avatar 路径，Flow 暴露） | 头像本地缓存与即时回显 |
| 头像查看/裁剪 | `common/profile/AvatarViewerScreen`、`AvatarCropScreen` | 复用为头像修改流程 |
| 抽屉 UI 组件 | `ProfileDrawerContent`、`ProfileMenuItem` | 抽出菜单项样式，供用户中心复用 |
| 轻量身份 | `ProfileRepository.fetchAuthMe()` → `AuthUser`（OIDC claims） | 基础资料兜底 |
| 退出二次确认 | `ui/components/DeleteConfirmSheet` | 退出登录/注销确认 |
| 权限管理 | `common/permission/PermissionManagerScreen` | 作为一个入口 |
| 统一网络 | `apiCall`/`ApiResult` + `FilesApi`（头像上传） | 拉取/更新资料、上传头像 |
| 转场/隐藏底栏 | `MainActivity` overlay + `onFullscreenChange` | 用户中心作为全屏子页接入 |

---

## 三、信息架构（功能模块）

用户中心按四段组织，标注「现成 / 需新增 / 需后端」：

1. **资料区（顶部）**：头像、昵称、邮箱、登录态徽标（已登录/游客/邮箱未验证）。
   - 头像修改：现成流程（选图→`AvatarCropScreen`→`ProfileStore` 本地保存）＋新增「上传到后端」。
   - 昵称修改：**需新增**（内联编辑或弹窗）＋**需后端**写接口。
   - 游客态：显示「登录 / 注册」引导，隐藏需要账号的项。

2. **账户与安全**：
   - 指纹登录开关：现成（`AuthViewModel.setBiometricEnabled`，仅设备支持且非游客显示）。
   - 退出登录 / 彻底登出：现成（`logout` / `logoutFederated`，配 `DeleteConfirmSheet` 二次确认）。
   - 修改密码 / 注销账号：**需后端**（Auth0 侧或后端接口），先占位。

3. **偏好设置**：主题、语言、通知开关等。多为**需新增**的本地偏好（可存 MMKV），通知权限跳系统设置。先放占位骨架。

4. **其他入口**：权限管理（现成 `PermissionManagerScreen`）、关于/版本、意见反馈、清理缓存等。按现有能力逐个接。

---

## 四、数据来源与模型

分两层，**会话态**与**完整档案**：

- **会话态（即时可得）**：来自 `AuthViewModel`/`AppUserProvider`——是否登录、是否游客、token 内的 name/email/picture。用户中心打开即有基础显示，无需等网络。
- **完整档案（服务端）**：`GET /api/v1.0/me` 返回 `{ registered, userId, displayName, email, avatarUrl }`（见 api-reference §2）。用来校正/补全昵称与头像 URL。`registered=false` 时引导完善资料。
  - 更完整的档案（如可写字段）后端文档提到 `/api/v1.0/members/me`，**当前 api-reference 未列出其字段与是否支持写**——见 §八 待确认。

建议领域模型（`feature/profile/`）：

```kotlin
data class UserProfile(
    val userId: String?,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,       // 服务端头像
    val localAvatarPath: String?, // 本地缓存头像（ProfileStore）
    val registered: Boolean,
    val isGuest: Boolean,
    val emailVerified: Boolean?,
)
```

读取统一走 `apiCall` → `ApiResult` 三态；显示优先级：本地头像 > 服务端 avatarUrl > 默认占位。

---

## 五、架构与文件落位（对齐 android-project 规范）

新增 `feature/profile/`，遵循 Route/Screen/ViewModel/UiState 拆分：

```
feature/profile/
├── UserCenterUiState.kt   // 不可变状态 + 事件
├── UserCenterViewModel.kt // 组合 AuthViewModel 会话 + ProfileRepository 档案
├── UserCenterScreen.kt    // 无状态 UI，可 @Preview（游客/登录/加载/错误）
└── UserCenterRoute.kt     // 有状态：连 ViewModel、接头像裁剪子页
```

- `UserCenterViewModel` 用 `@HiltViewModel`，注入 `ProfileRepository`（扩展）与读 `AppUserProvider`；会话动作（登出/指纹/登录）可复用/委派现有 `AuthViewModel`，或把这些动作下沉到共享层，避免两个 VM 各持一份逻辑（见 §八 决策）。
- `ProfileRepository` 扩展：新增 `fetchMe(): ApiResult<UserProfile>`（`GET /api/v1.0/me`）、`updateProfile(...)`/`updateAvatar(fileId)`（**待后端**）。
- UI 复用 `ProfileMenuItem` 等抽屉里的样式组件（可从 `ProfileDrawer.kt` 抽公共件到 `common/profile/`）。
- 状态收集用 `collectAsStateWithLifecycle()`；单一 `UserCenterUiState`。

---

## 六、导航与交互

沿用现有 overlay 模式：入口仍是抽屉/首页头像，点击进入用户中心全屏页。有两种接法：

- **A（推荐，改动小）**：把用户中心作为 `MainActivity` 的一个 overlay（类似 `Permissions`/`Avatar`），或作为 `HomeRoute` 内的子页；打开时 `onFullscreenChange(true)` 隐藏底栏，返回键回上层。
- **B**：迁到类型安全 Navigation Compose（与 MainActivity 评审建议一致），把用户中心、权限管理、头像等做成目的地。成本更高，作为后续统一导航时一起做。

头像修改流程复用现有：进入 `AvatarCropScreen` → `ProfileStore.setAvatar` 本地即时回显 → 后台走 `FilesApi` 上传得 `fileId` → 调 `members/me` 更新 `avatarUrl`（待后端）。失败保留本地头像并可重试。

---

## 七、关键流程

- **打开用户中心**：立即用会话态渲染（名字/邮箱/头像本地），并发起 `fetchMe()` 校正；`ApiResult.NetworkError` 时保留本地显示并提示可下拉重试。
- **改昵称**：内联编辑 → 乐观更新 UI → 调后端写接口；失败回滚并提示。
- **改头像**：选图→裁剪→本地保存→上传→更新服务端；各步失败互不影响本地可用性。
- **退出登录**：`DeleteConfirmSheet` 二次确认 → `AuthViewModel.logout`（或 `logoutFederated` 彻底登出）→ 回登录页/游客态。
- **游客**：隐藏账号相关项，突出「登录 / 注册」；`AppUserProvider.isGuest` 为准。

---

## 八、后端依赖与待确认

1. **完整档案接口**：`GET /api/v1.0/me` 只读且字段有限。昵称/头像**写入**需要 `members/me`（PATCH）或等价接口——api-reference 未列出，需后端确认路径、可写字段、头像是传 `fileId` 还是 `avatarUrl`。**这是资料「维护（编辑）」能力的前置阻塞项。**
2. **头像上传**：走现有 files 三段式拿 `fileId`，再由 `members/me` 关联；确认后端接受 `fileId` 或需要先换取 URL。
3. **修改密码 / 注销账号**：走 Auth0 还是后端接口？先占位。
4. **VM 归属**：会话动作（登出/指纹）现在在 `AuthViewModel`。用户中心是复用 `AuthViewModel` 实例、还是新建 `UserCenterViewModel` 只做档案、会话动作回调上抛？建议**档案归 `UserCenterViewModel`、会话动作复用 `AuthViewModel`**，避免逻辑重复。

---

## 九、边界情况

游客/未登录（隐藏账号项、引导登录）、`registered=false`（引导完善资料）、离线（本地态兜底 + 重试）、邮箱未验证（徽标提示）、头像上传失败（本地可用、可重试）、token 失效（`BizError.isAuthExpired` → 触发重新登录）。

---

## 十、落地清单（分阶段）

1. **骨架（不依赖后端）**：`feature/profile/` 四件套；复用 `AuthUiState` 渲染资料区与账户安全区；接 overlay 导航；抽出 `ProfileMenuItem` 公共件。
2. **档案读取**：`ProfileRepository.fetchMe()`（`GET /api/v1.0/me`）+ `UserProfile` 模型 + 三态渲染。
3. **头像维护**：裁剪→本地→`FilesApi` 上传→（待后端）关联 `avatarUrl`。
4. **资料编辑**：昵称等，**依赖后端写接口**。
5. **偏好/其他入口**：主题/语言/通知/权限管理/关于/反馈，按现有能力逐个接。

> 说明：第 1 阶段可完全基于现有代码落地跑通 UI 与会话动作；第 2–4 阶段的「维护（编辑）」能力取决于 §八 的后端接口确认。
