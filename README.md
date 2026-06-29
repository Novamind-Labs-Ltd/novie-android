# Novie App

一款基于 Jetpack Compose 的笔记应用，支持图文混排正文、富文本（加粗 / 斜体）、文件夹与标签管理，以及为逐字输入优化的光标 / 键盘交互。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（Material 3，Compose BOM 2026.02.01） |
| 架构 | MVVM + 单向数据流（StateFlow / UiState / Event） |
| 本地存储 | Room 2.7.1 |
| 图片加载 | Coil 2.7.0 |
| 集合稳定性 | kotlinx.collections.immutable（让 Compose 形参稳定、组件可跳过重组） |
| 构建 | AGP 9.1.1 · minSdk 26 · targetSdk 36 |
| 包名 | `com.novamind.app` |

## 功能概览

- **图文笔记**：正文采用块编辑器（文本 / 图片 / 文件 / PDF / Markdown 块），图片内联插入并持久化；
- **富文本**：选中或续写文字的加粗、斜体；
- **组织能力**：文件夹（单选）、标签（多选），均持久化到数据库，带搜索框与「创建新项」入口；编辑页「+」可直接弹出创建面板（名称 + 颜色）；
- **库（Library）**：Recent / Folders 双标签页；Folders 支持新建（名称 + 颜色）、长按拖拽排序、行内重命名、改色（点图标弹色板）、删除二次确认，文件夹颜色映射到卡片边框；新建 / 重命名做重名校验（Toast 拦截）；
- **标签管理（Tag manager）**：从侧栏进入，列表显示标签与关联笔记数，支持新建、重命名、改色、左滑删除、长按拖拽排序；
- **回收站（Recycle Bin）**：删除笔记软删进回收站（tombstone），展示剩余天数，可只读查看、恢复或彻底删除；
- **光标 / 键盘一致性**：采用 `adjustNothing` 窗口策略，键盘弹出不重排，仅在光标被遮时按需滚动（详见 `docs/编辑页光标键盘方案.md`）；
- **撤销 / 重做**、自动保存。

## 模块结构

```
com.novamind.app
├─ MainActivity.kt              应用入口，承载页面切换（AnimatedContent）+ 全屏覆盖层（回收站 / 标签管理）
├─ common/config/AppConfig.kt   集中配置（编辑器/媒体/文件夹颜色等）
├─ data/                        数据层
│  ├─ NoteRepository · RoomNoteRepository        笔记（含软删 / 恢复 / 彻底删除）
│  ├─ FolderRepository · RoomFolderRepository    文件夹（颜色 / 排序 / 重命名 / 删除）
│  ├─ TagRepository · RoomTagRepository          标签（颜色 / 排序 / 重命名 / 删除）
│  └─ db/                       AppDatabase(v7) · NoteDao · FolderDao · TagDao · *Entity · Converters
├─ feature/                     业务页面（每个含 Route / Screen / ViewModel / UiState）
│  ├─ home/                     首页（笔记列表、更多菜单）；NoteItem 共享 UI 模型在 feature/note/
│  ├─ create/                   笔记创建 / 编辑（支持只读态，用于回收站查看）
│  │  ├─ components/            CreateTopBar · CreateMetaRow · FormattingToolbar · NoteContentEditor
│  │  ├─ editor/                块模型与富文本：NoteEditorState · RichTextState · NoteDocument · ImageStore
│  │  ├─ folder/                Folder 模型 · FolderPickerSheet
│  │  └─ tag/                   Tag 模型 · TagChip · TagPickerSheet · tagmanager/（标签管理页 + CreateTagSheet）
│  ├─ library/                  库：LibraryScreen · LibraryDrawer · FolderRow · CreateFolderSheet
│  ├─ recyclebin/               回收站页
│  └─ calendar/                 日历
└─ ui/                          主题与共享组件
   ├─ colors/                   设计系统：Palette + 语义令牌（Background/Text/Border/Icon Colors）
   ├─ components/               BackButton 等共享组件
   └─ theme/                    AppTheme · 排版
```

## 架构约定

每个 feature 页面遵循统一的 MVVM 单向数据流：

- **UiState**：不可变数据类，描述页面全部状态；列表字段用 `ImmutableList` 以保证 Compose 稳定性；
- **Event**：`sealed class`，UI 把用户操作以事件形式上报；
- **ViewModel**：持有 `StateFlow<UiState>`，在 `onEvent` 中处理事件并产出新状态；
- **Route → Screen**：`Route` 负责接 ViewModel 与导航，`Screen` 为无状态可组合函数，便于 `@Preview`。

正文文档以结构化 JSON 序列化后存入 `Note.body`（无独立数据库列），`NoteDocument` 负责块 JSON 与纯文本预览之间的转换。

### Compose 约定（强制）

- **颜色只用 `ui/colors`**：组件内不得硬编码 `Color(0x…)`，统一引用设计系统语义令牌（`Palette` / `TextColors` / `BackgroundColors` / `BorderColors` / `IconColors`），通过 `DualColor.current()` 随主题深浅解析；惯例是每个所需颜色一个 `private @Composable @ReadOnlyComposable get()`（参考 `feature/note`、`feature/library` 各组件）。
- **新增组件必带 `@Preview`**：任何新可组合函数都应附带至少一个 `@Preview`（包在 `AppTheme` 内），覆盖代表性状态；预览无法加载的内容（如 `AsyncImage` 读本地文件）用 `LocalInspectionMode` 显示占位。

## 构建与运行

```bash
# 命令行构建 Debug 包
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

或用 Android Studio 打开根目录，Sync 后直接 Run。`@Preview` 组件可在 Design 面板预览（首页卡片、各编辑器组件等）。

## 文档

- `docs/编辑页光标键盘方案.md` — 光标与键盘交互的完整技术方案（adjustNothing + 按需滚动）。

## 目录约定

- 颜色集中在 `ui/colors/`（`Palette` 原始色阶 + 语义令牌），可调配置集中在 `common/config/AppConfig.kt`（如 `Folder.COLORS`）；
- 列表渲染统一在循环内使用 `key(id)`，减少重组开销；
- 拖拽排序、左滑删除等交互在 `LibraryScreen` / `TagManagerScreen` 中有可参考实现。
