# MainActivity 代码评审

- **文件**：`app/src/main/java/com/novamind/app/MainActivity.kt`（402 行）
- **评审日期**：2026-07-03
- **范围**：Activity 入口 + 全局导航编排 + 认证门控 + 深链/权限/FCM

## 评审结论：需修改（无阻断项，建议项较多）

整体能跑、`rememberSaveable` 用得规范、深链消费与前后台监听处理得当。主要问题集中在**架构**：导航状态全部散落在 `setContent` 里，形成近 290 行的「上帝 Composable」，可测性与可维护性差。以下按优先级列出。

---

## 🔴 阻断（必须改）

未发现会导致崩溃或功能错误的阻断项。

---

## 🟡 建议

### 1. 导航状态散落，建议收敛到单一状态源 + 类型安全导航
`setContent` 中并列了 9 个可变状态：`currentRoute`、`editingNoteId`、`createReturnRoute`、`libraryAsSubpage`、`hideBottomNav`、`showAskNovie`、`showRecycleBin`、`showTagManager`、`showOnboarding`（第 100–130 行）。这违背「单一不可变 `UiState`」约定，且用字符串 route + 布尔位手工管理页面栈，等价于自造了一套脆弱的导航。

改法：抽出 `AppNavState`（不可变 data class）由一个 `AppViewModel`/状态持有者管理，或迁移到**类型安全 Navigation Compose**（`@Serializable` route）。覆盖层（AskNovie/回收站/标签管理）作为独立目的地，返回栈交给 NavController，能消掉大部分手工布尔位与多个 `BackHandler`。

### 2. 拆分「上帝 Composable」
`onCreate` 内联了约 290 行 UI（第 98–377 行）。建议抽出无状态的 `@Composable AppRoot(state, onEvent)`，`MainActivity` 只负责 `enableEdgeToEdge()`、软键盘、深链、权限等 Activity 级职责。拆分后 `AppRoot` 可加 `@Preview`，也便于测试。

### 3. `collectAsState()` 不一致（生命周期）
第 327 行 `val update by UpdateController.state.collectAsState()` 用了 `collectAsState()`，而 `authState`（第 134 行）用的是 `collectAsStateWithLifecycle()`。应统一为后者，使收集在后台暂停。同时移除第 27 行 `collectAsState` 的 import。

### 4. 硬编码颜色（对照 hardcoded-colors-audit）
认证门控里 `Color(0xFFFBFAF7)`（第 356 行）、`Color(0xFF3D7A5A)`（第 359 行）为硬编码。应走 `MaterialTheme.colorScheme` / `AppTheme` 令牌，与工程既有的硬编码颜色治理一致。

### 5. 认证门控在主内容之上叠加，主内容仍会先组合
门控（loading / 指纹 / 登录，第 352–374 行）画在整个 `Box` 最顶层，但底层的 `HomeRoute` 等已经组合并可能触发依赖 token 的数据加载，未登录/校验会话期间可能产生无谓请求或 401。建议：`isCheckingSession` / 未认证时**短路**不组合主内容（用 `when` 提前 return 或包一层门控），仅在通过后再渲染导航树。

### 6. 传入 Activity 引用需确认未被持有
`authViewModel.logout(this@MainActivity)`、`unlockWithBiometric(this@MainActivity)`（第 207、365 行）把 Activity 传给 ViewModel。若仅作瞬时参数（Auth0 web flow / BiometricPrompt）可接受；请确认 ViewModel **不缓存**该引用，否则泄漏 Activity。

## 🟢 提示

- **FCM token 日志**（第 396–400 行）：每次 `onCreate` 都拉取并 `DebugLog.i` 打印完整 token。token 属设备标识，建议仅 Debug 变体打印或截断；确认 `AppLog` 的 PiiMasker 覆盖此类 secret。方法注释称「用于上报服务端」但当前只打日志，属未完成项，建议补 TODO 或去掉夸大注释。
- **多个 `BackHandler`**（第 114–125 行）：目前各覆盖层互斥，工作正常；若未来可能同时开启，需注意 LIFO 优先级冲突。收敛到 NavController 后此问题自然消除。
- **`hideBottomNav` 复位**：由各子页 `onFullscreenChange` 回调驱动，逻辑正确但依赖每个子页都正确回传 `false`；集中管理后更稳。
- **`Uri.parse` + `startActivity`**（第 332–334 行）已用 `runCatching` 兜底，良好。

---

## 小结

| 维度 | 评价 |
|------|------|
| 架构 | ⚠️ 导航状态散落、上帝 Composable，是主要债务 |
| Compose 性能 | 基本可，`collectAsState` 需统一 |
| 并发/生命周期 | 良好（前后台监听、深链消费规范） |
| 安全/资源 | ⚠️ 硬编码颜色、FCM token 日志需收敛 |
| 可维护性 | ⚠️ 需拆分与状态收敛 |

**优先处理**：#1 + #2（导航收敛与拆分）收益最大；#3、#4 是低成本快速修复。
