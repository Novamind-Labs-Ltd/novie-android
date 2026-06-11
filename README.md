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

- **图文笔记**：正文采用块编辑器（文本块 / 图片块），图片内联插入并持久化；
- **富文本**：选中或续写文字的加粗、斜体；
- **组织能力**：文件夹（单选）、标签（多选），均带搜索框与「创建新项」入口；
- **光标 / 键盘一致性**：采用 `adjustNothing` 窗口策略，键盘弹出不重排，仅在光标被遮时按需滚动（详见 `docs/编辑页光标键盘方案.md`）；
- **撤销 / 重做**、自动保存。

## 模块结构

```
com.novamind.app
├─ MainActivity.kt              应用入口，承载页面切换（AnimatedContent）
├─ data/                        数据层
│  ├─ NoteRepository.kt         仓库接口
│  ├─ RoomNoteRepository.kt     Room 实现
│  └─ db/                       AppDatabase · NoteDao · NoteEntity · Converters
├─ feature/                     业务页面（每个含 Route / Screen / ViewModel / UiState）
│  ├─ home/                     首页（笔记列表、更多菜单）
│  ├─ create/                   笔记创建 / 编辑
│  │  ├─ components/            CreateTopBar · CreateMetaRow · FormattingToolbar · NoteContentEditor
│  │  ├─ editor/                块模型与富文本：NoteEditorState · RichTextState · NoteDocument · ImageStore
│  │  ├─ folder/                Folder 模型 · FolderPickerSheet
│  │  ├─ tag/                   Tag 模型 · TagChip · TagPickerSheet
│  │  └─ model/                 Note 领域模型
│  ├─ library/                  库
│  └─ calendar/                 日历
└─ ui/                          主题与共享组件（theme/ · components/）
```

## 架构约定

每个 feature 页面遵循统一的 MVVM 单向数据流：

- **UiState**：不可变数据类，描述页面全部状态；列表字段用 `ImmutableList` 以保证 Compose 稳定性；
- **Event**：`sealed class`，UI 把用户操作以事件形式上报；
- **ViewModel**：持有 `StateFlow<UiState>`，在 `onEvent` 中处理事件并产出新状态；
- **Route → Screen**：`Route` 负责接 ViewModel 与导航，`Screen` 为无状态可组合函数，便于 `@Preview`。

正文文档以结构化 JSON 序列化后存入 `Note.body`（无独立数据库列），`NoteDocument` 负责块 JSON 与纯文本预览之间的转换。

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

- 共享颜色集中在 `feature/create/components/CreateColors.kt` 与 `ui/theme/`；
- 列表渲染统一在循环内使用 `key(id)`，配合不可变集合减少重组开销。
