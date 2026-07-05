# 数据分层与解耦设计（DTO / Domain / UIState）

- **日期**：2026-07-04（基于现有代码总结并规范化）
- **适用**：my-novie Android 工程全部 feature/data 层
- **相关**：`http-networking-design.md`（统一响应）、`user-center-design.md`、`note-*`

---

## 一、目标与原则

保证**数据纯洁性**：每一层只认「属于自己的模型」，外层的数据格式（后端字段、数据库列、框架注解）**不许渗透**到内层或 UI。这样后端改字段、换存储、调 UI，都被限制在单一层内，改动不外溢。

四条铁律：

1. **单层单模型**：网络有网络的模型（DTO）、存储有存储的模型（Entity）、业务有业务的模型（Domain）、界面有界面的模型（UI）。不共用一个类跨层。
2. **依赖单向向内**：UI → Domain ← Data(DTO/Entity)。Domain 是核心，**不依赖任何框架**（无 Retrofit/Room/Compose 类型与注解）。
3. **边界即翻译**：跨层只能通过**映射函数**转换，绝不把外层对象直接往里传。
4. **框架细节不外泄**：`@Serializable`/`@SerialName`/`@Entity`/`@ColumnInfo`/`Color`/`@Composable` 等只出现在各自层，不进 Domain。

---

## 二、四类模型（对照现有代码）

| 层 | 模型 | 归属包 | 现有例子 | 特征 / 允许的注解与类型 |
|----|------|--------|----------|--------------------------|
| **DTO** | 网络传输对象 | `common/net/*`、`data/*/*Dtos` | `MeDto`、`PresignResp`/`FileView`、`NoteView`(后端)、`TaskDto`、`EventDto`、信封 `ApiResponse<T>` | `@Serializable`/`@SerialName`；字段可空、命名对齐后端；**只做承载，不含业务逻辑** |
| **Entity** | 本地存储对象 | `data/db/*` | `NoteEntity`、`RecordingEntity`、`TagEntity` | `@Entity`/`@PrimaryKey`/`@ForeignKey`；字段是列；状态以字符串入库（如 `uploadStatus`） |
| **Domain** | 业务领域模型 | `feature/*/model`、`data/*`(纯 kotlin) | `Note`、`AuthUser`、`UserProfile`、`CalendarEvent`、`CalendarTask` | **纯 Kotlin data class**，无框架注解、无 Android/Compose 类型；表达业务概念（如 `borderColorHex: String?`、`tags: List<Tag>`） |
| **UI** | 界面模型 | `feature/*`（Item + UiState） | `NoteItem`、`XxxUiState`（如 `HomeUiState`/`AuthUiState`） | 可含 Compose 类型（如 `Color`）、展示派生字段（`description` 摘要、`imagePath` 首图、`isSelected`）、一次性事件、loading/error |

> 关键对照：同是「笔记」，四层是四个类——`NoteView`(DTO) / `NoteEntity`(存储) / `Note`(领域) / `NoteItem`(列表 UI)。各层字段按各自需要裁剪，互不牵连。

---

## 三、依赖方向与边界规则

```
        ┌─────────── UI 层 (Screen/ViewModel) ───────────┐
        │  UiState / NoteItem   ← 只认 Domain + UI 类型     │
        └───────────────▲───────────────────────────────┘
                        │ 映射 (Domain → UI)
        ┌───────────────┴──────── Domain 层 ─────────────┐
        │  Note / UserProfile / CalendarEvent（纯 Kotlin） │
        └───────▲───────────────────────────▲────────────┘
       映射(DTO→Domain)                  映射(Entity→Domain)
        ┌───────┴────────┐          ┌────────┴───────────┐
        │  DTO (net)      │          │  Entity (Room)      │
        │  ApiResponse<T> │          │  NoteEntity …       │
        └─────────────────┘          └─────────────────────┘
```

硬性规则：

- **UI 不 import net/room 包**：ViewModel/Screen 里不得出现 `NoteView`/`NoteEntity`/`ApiResponse`/`RequestBody` 等。UI 只见 Domain 与 UI 模型。
- **Domain 不 import 任何框架**：`feature/*/model` 与领域 data class 里不得出现 `@Serializable`/`@Entity`/`androidx.compose.*`/`retrofit2.*`。
- **DTO/Entity 不上浮**：Repository 是「翻译边界」——对外只返回 Domain（或 `ApiResult<Domain>`），绝不把 DTO/Entity 泄漏给 ViewModel。
- **UI 类型不下沉**：`Color`、`Painter`、`@Composable` 只在 UI 层。领域用 `borderColorHex: String?`，到 UI 才 `ColorUtils.parseHexColor(...)` 成 `Color`（现有 `NoteItem.borderColor` 正是此做法）。

---

## 四、映射约定

沿用现有习惯：**扩展函数 + 单向命名**，放在「目标层或转换发生的边界」。

- `Entity ↔ Domain`：放数据层，如 `data/db/Converters.kt` 的 `NoteEntity.toNote()` / `Note.toEntity()`。
- `DTO → Domain`：放对应 Repository（`private fun XxxDto.toDomain()`），如 `TaskDto.toDomain()`、`EventDto.toDomain()`；`MeDto → AuthUser` 在 `ProfileRepository`。
- `Domain → UI`：放 ViewModel（或 `feature/*` 的 mapper），如 `HomeViewModel` 把 `Note` 组装成 `NoteItem`（`previewText(body)` → description、`tags.map{it.name}`、`parseHexColor(hex)` → Color）。

命名规范：`toDomain()` / `toEntity()` / `toDto()` / `toItem()` / `toUiState()`。**每个方向一个函数**，不要双向糊在一起。映射函数尽量 `internal`/`private`，不外扩可见性。

写入方向对称：UI 事件 → ViewModel 组装/更新 Domain → Repository `Domain.toEntity()` 落库 或 `Domain.toDto()`(请求体) 发网络。

---

## 五、数据流（读 / 写）

**读**（后端/本地 → 界面）：
```
filesApi.presign() : Response<ApiResponse<PresignResp>>   // DTO(信封)
  → apiCall { }      : ApiResult<PresignResp>             // 解包三态
  → repo 映射         : ApiResult<Domain>                  // DTO→Domain
  → ViewModel         : UiState(domain → NoteItem/字段)    // Domain→UI
  → Screen 渲染
```
本地读同理：`dao.getAllNotes()` → `entities.map { it.toNote() }`（现有 `RoomNoteRepository`）→ Domain → UI。

**写**（界面 → 落库/上行）：
```
Screen onEvent → ViewModel 改 Domain → repo.addOrUpdate(note: Note)
  → note.toEntity() 入库 / note.toDto() 发请求
```

统一响应把「传输失败 / 业务错误 / 成功」收敛成 `ApiResult`，ViewModel 只按三态映射到 `UiState`（loading/error/data），错误码/`traceId` 等**不下发**到 UI 文案以外的地方。

---

## 六、纯洁性保障清单

- Domain data class **零框架依赖**（grep 不到 `@Serializable`/`@Entity`/`androidx.compose`/`retrofit`/`okhttp`）。
- DTO 字段全部**可空 + 有默认值**，容忍后端 schema 漂移（配合 `ignoreUnknownKeys`）；解析失败在 Repository 处理，不抛给 UI。
- Entity 的存储细节（字符串枚举、hex、jsonb/字段名）**不出数据层**；Domain 用强类型/语义字段。
- UI 的展示派生（摘要、首图、选中态、Color）**只在 UI 模型**产生，不回灌 Domain。
- Repository 接口签名只出现 Domain / `ApiResult<Domain>` / 基本类型；**不出现 DTO/Entity**。
- ViewModel 不 import `data.db.*` 与 `common.net.*`（除少数纯工具）。
- 一次性事件（跳转/Toast）放 UiState 的可消费字段，不塞进 Domain。

---

## 七、正例与反例

**正例（现有）**：`Note`(领域)持 `borderColorHex: String?`；`NoteItem`(UI)持 `borderColor: Color?`，转换发生在 ViewModel。领域不认识 Compose 的 `Color`，UI 不关心 hex 解析——干净。

**反例（要避免）**：
- 直接把 `NoteEntity` 或 `NoteView` 传进 Composable 渲染 → 后端/DB 改字段直接波及 UI。
- 在 Domain data class 上加 `@Serializable` 让它「顺便当 DTO 用」→ 领域被网络协议绑架，字段被迫可空、命名迁就后端。
- ViewModel 里 `import retrofit2.Response` / 手动解 JSON → 传输细节泄漏到 UI 层。
- UI 直接持有 `ApiResponse`/`ApiResult` 往深层组件传 → 应在 ViewModel 折叠成 UiState。

---

## 八、各域现状对照

| 域 | DTO | Entity | Domain | UI |
|----|-----|--------|--------|-----|
| 笔记 | `NoteView`(后端) | `NoteEntity` | `Note` | `NoteItem` / `HomeUiState` |
| 认证/用户 | `MeDto`、`/me`(registered…) | —（凭据在安全存储） | `AuthUser` / `UserProfile`(规划) | `AuthUiState` / `UserCenterUiState`(规划) |
| 录音 | `PresignResp`/`FileView` | `RecordingEntity` | （可加 `Recording` 领域模型） | `RecordingFlowState` |
| 日历 | `EventDto`/`TaskDto` | `CalendarEventCache` 存储 | `CalendarEvent`/`CalendarTask` | `CalendarUiState` |

> 待补：录音目前 Entity 直接被上层读用，建议补一个 `Recording` 领域模型 + `toDomain()`，与其它域对齐。

---

## 九、落地规范（code review 检查点）

1. 新增接口：先定 DTO（`@Serializable`，字段可空），再定/复用 Domain，Repository 里 `dto.toDomain()`，返回 `ApiResult<Domain>`。
2. 新增页面：Domain → `UiState`/`Item` 的映射写在 ViewModel；Screen 只吃 UiState。
3. 禁止：Domain 带框架注解；UI import net/room；Repository 返回 DTO/Entity。
4. 映射函数单向命名、就近放置、最小可见性。
5. 评审时 grep 关键词做守卫：领域包内 `@Serializable|@Entity|androidx.compose|retrofit2|okhttp3` 应为空；`feature/**/*ViewModel.kt`、`*Screen.kt` 内 `data.db.|common.net.（除工具）` 应为空。
