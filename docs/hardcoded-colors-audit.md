# 硬编码自定义颜色审计

> 扫描范围：`app/src/main/java/com/novamind/app`，匹配 `Color(0x...)` 字面量
> 已排除 `ui/colors/`（设计系统色板与语义令牌的来源，本就应保留）
> 生成时间：2026-06-26 · 共 **34 个文件** 仍含硬编码颜色

---

## 一、结论速览

迁移工作只完成了**编辑器 / 首页卡片**一小块。绝大多数业务页面仍直接写死 `Color(0xFF...)`，颜色与主题无法联动深浅色。

| 状态 | 文件数 | 说明 |
|---|---|---|
| ✅ 已迁移（无字面量） | — | `NoteCard`、`CreateTopBar`、`FormattingToolbar` 等仅 `@Preview` 背景保留，不计入 |
| 🟡 已迁移但有残留 | 3 | 仅剩个别装饰色 / 占位色 |
| 🔴 完全未迁移 | 29 | 整屏使用私有 `Color(0x...)` 常量 |
| ⚪️ 模板保留 | 2 | Material 模板色，无需改动 |

---

## 二、反复出现的颜色 → 应替换为的语义令牌

这些值在多个文件重复出现，是最该统一的。建议批量替换：

| 硬编码值 | 含义 | 应替换为 |
|---|---|---|
| `0xFF1A1A1A` | 主文字（近黑） | `TextColors.Primary.default.current()` |
| `0xFF6B6B6B` | 次级文字 | `TextColors.Primary.secondary.current()` |
| `0xFF8A8A8A` / `0xFFAAAAAA` / `0xFFBDBDBD` | 提示 / 占位文字 | `TextColors.Primary.tertiary.current()` |
| `0xFF3D7A5A` / `0xFF2E9E5B` | 品牌绿 | `IconColors.Brand.default.current()` |
| `0xFFF0EFEA` / `0xFFF4F2EC` / `0xFFF1F0EC` | 页面底色 | `BackgroundColors.Page.default.current()` |
| `0xFFFFFFFF` / `0xFFFBFAF7` / `0xFFFDFCF8` / `0xFFF5F5F0` | 卡片 / 浮层底色 | `BackgroundColors.Surface.default.current()` |
| `0xFFD13C3C` / `0xFFB00020` / `0xFFB3261E` | 错误 / 删除红 | `IconColors.Error.default.current()` |
| `0xFFE0E0E0` / `0xFFE3E0D8` / `0xFFCDC8BC` / `0xFFD8D5CC` | 边框 / 分隔线 | `BorderColors.Default.default.current()` |
| `0x12000000` / `0x66000000` / `0x99000000` | 阴影 / 遮罩 | `ShadowColors.*` / 半透明黑遮罩 |

---

## 三、🟡 已迁移但有残留（优先清理，量小）

这几个文件已用语义令牌，只剩零星字面量：

- **`feature/create/components/NoteEditorSkeleton.kt`** — `CardSoft = Color(0xFFF7F6F2)`（即你举例的那个色）。其余已用 `BackgroundColors.Page` / `TextColors.Primary`。
- **`feature/create/components/FastScrollbar.kt`** — `BarIdle = Color(0x66000000)`、`BubbleBg = Color(0xE61A1A1A)`（拖杆 / 气泡装饰色，可保留或归入 `ShadowColors`）。
- **`feature/create/components/NoteContentEditor.kt`** — `0xFFE8E7E2`（图片占位）、`0x99000000`（删除角标遮罩）、`0xFFF3F3F0` / `0xFFFDFCF8` / `0xFFFFFFFF`。

---

## 四、🔴 完全未迁移（整屏私有颜色常量）

按字面量数量从多到少排列：

### feature 业务页

| 文件 | 数量 | 主要色值 |
|---|---|---|
| `feature/calendar/CalendarScreen.kt` | 15 | `0xFFF4F2EC` `0xFF1A1A1A` `0xFF3D7A5A` `0xFF2E7D6B` `0xFF5B89A6` `0xFFDCEAF1` … |
| `feature/asknovie/AskNovieScreen.kt` | 12 | `0xFFF4F2EA` `0xFF1A1A1A` `0xFF2E9E5B` `0xFFE9E7DF` … |
| `feature/library/LibraryScreen.kt` | 11 | `0xFFF4F2EC` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFCDC8BC` … |
| `feature/home/HomeScreen.kt` | 9 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFF6B6B6B` `0xFFD13C3C` `0xFF9E8E78` … |
| `feature/asknovie/ChatHistorySheet.kt` | 6 | `0xFFFBFAF7` `0xFF1A1A1A` `0xFF2A2A2A` `0xFF8A8A8A` |
| `feature/asknovie/RenameSheet.kt` | 6 | `0xFFFBFAF7` `0xFFF2F0E9` `0xFF1A1A1A` `0xFFE0DDD3` |
| `feature/home/UpcomingListScreen.kt` | 5 | `0xFFF0EFEA` `0xFFEDEAE2` `0xFF1A1A1A` `0xFF6B6B6B` |
| `feature/auth/LoginScreen.kt` | 5 | `0xFFFBFAF7` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFB3261E` |
| `feature/auth/BiometricLockScreen.kt` | 5 | `0xFFFBFAF7` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFB00020` |
| `feature/create/folder/FolderPickerSheet.kt` | 5 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFAAAAAA` |
| `feature/create/tag/TagPickerSheet.kt` | 3 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFFAAAAAA` |
| `feature/create/tag/TagChip.kt` | 3 | `0xFF3D7A5A` `0xFF6B6B6B` `0xFFE0E0E0` |

### common 公共页

| 文件 | 数量 | 主要色值 |
|---|---|---|
| `common/permission/PermissionManagerScreen.kt` | 9 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFE8F0EB` … |
| `common/pdf/PdfViewerScreen.kt` | 9 | `0xFF2A2A2D` `0xFFF2F2F2` `0xFFE07A7A` `0xCC1A1A1A` `0x66FFFFFF` … |
| `common/profile/ProfileDrawer.kt` | 6 | `0xFFFBFAF7` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFD0C8B8` |
| `common/notifications/NotificationListScreen.kt` | 6 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFD13C3C` |
| `common/profile/AvatarViewerScreen.kt` | 5 | `0xFF101010` `0xFF1A1A1A` `0xFF2A2A2A` `0xFF3D7A5A` |
| `common/onboarding/OnboardingScreen.kt` | 5 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFF3D7A5A` `0xFFCFCBC0` |
| `common/update/UpdateDialog.kt` | 4 | `0xFFFFFFFF` `0xFF1A1A1A` `0xFF6B6B6B` `0xFF3D7A5A` |
| `common/web/WebViewScreen.kt` | 4 | `0xFFF6F6F4` `0xFF1A1A1A` `0xFF6B6B6B` `0xFF3D7A5A` |
| `common/profile/AvatarCropScreen.kt` | 4 | `0xFF101010` `0xFF3D7A5A` `0x22FFFFFF` |
| `MainActivity.kt` | 2 | `0xFF3D7A5A` `0xFFFBFAF7` |

### ui/components 通用组件

| 文件 | 数量 | 主要色值 |
|---|---|---|
| `ui/components/VoiceRecordingBar.kt` | 10 | `0xFFFBFAF7` `0xFF1A1A1A` `0xFF2E9E5B` `0xFFE9E9E9` … |
| `ui/components/DeleteConfirmSheet.kt` | 4 | `0xFFFFFFFF` `0xFF1A1A1A` `0xFF6B6B6B` `0xFFD13C3C` |
| `ui/components/ImagePreviewScreen.kt` | 3 | `0xFFF0EFEA` `0xFF1A1A1A` `0xFFFFFFFF` |
| `ui/components/BottomNavBar.kt` | 3 | `0xFFF5F5F0` `0xFF1A1A1A` `0xFF3D7A5A` |
| `ui/components/Shimmer.kt` | 2 | `0xFFEDEDED` `0xFFFFFFFF` |
| `ui/components/AttachmentSheet.kt` | 2 | `0xFFFFFFFF` `0xFF1A1A1A` |
| `ui/components/BackButton.kt` | 1 | `0xFFF0EFEA` |

---

## 五、⚪️ 模板保留（无需改动）

- `ui/theme/Color.kt` — Material 模板色 `Purple80` 等（`0xFFD0BCFF` …）
- `ui/theme/Theme.kt` — `0xFFFFFBFE` / `0xFF1C1B1F`（Material 默认 surface/onSurface 模板）

> 注：`NoteCard.kt`、`CreateTopBar.kt`、`FormattingToolbar.kt` 中残留的 `0xFFF0EFEA` / `0xFFFDFCF8` 仅出现在 `@Preview(backgroundColor=…)`，是预览画布底色（要求 Long 字面量），**无需迁移**。

---

## 六、建议

1. 先清理第三节 3 个「残留」文件（量小、收益直接）。
2. 业务页按模块批量迁移：`feature/home` → `feature/calendar` → `feature/library` → `feature/asknovie` → `common/*`。多数文件只是把顶部 `private val BgPage = Color(0xFF…)` 这类常量换成第二节的语义令牌 getter。
3. 迁移时把 `private val Xxx = Color(0x…)` 改成 `@Composable @ReadOnlyComposable get() = XxxColors.….current()`（与 `NoteCard.kt` 一致）。
