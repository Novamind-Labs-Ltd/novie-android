# 笔记离线优先（offline-first）与同步设计

## 1. 原则

- **本地 Room 为唯一数据源(SoT)**：UI 只订阅本地（已是 `Flow`），永不直连后端取数据。
- **先写本地，再异步同步**：用户操作立刻落本地并即时生效（乐观更新），同步在后台进行。
- **同步是「状态机 + 队列」**：每条记录带同步状态，后台 Worker 负责把本地变更推上去、把远端变更拉下来。
- **删除用 tombstone（软删）**：否则同步端无法感知"被删除"。

```
UI ──订阅──> Room(SoT) <──本地写── Repository
                  ▲                       │ 标记 DIRTY
                  └──应用远端变更── SyncWorker ──push/pull──> 后端
```

---

## 2. NoteEntity 字段演进

在现有列（`id,title,body,tagsJson,folderJson,borderColorHex,createdAt,updatedAt`）基础上新增：

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,          // 本地 id（UUID，离线即可生成，永不变）
    // …现有内容字段…
    val createdAt: Long,
    val updatedAt: Long,                  // 内容最后修改时间（本地权威）

    // —— 同步相关 ——
    val serverId: String? = null,         // 后端 id；未上传过为 null
    val rev: Long = 0,                    // 服务端版本号/修订号，用于冲突判定
    val syncStatus: String = SyncStatus.LOCAL.name,
    val deleted: Boolean = false,         // tombstone：软删，同步后再物理清理
    val lastSyncedAt: Long? = null,       // 最近一次与后端对齐的时间
)

enum class SyncStatus { LOCAL, DIRTY, SYNCING, SYNCED, CONFLICT }
```

- `id` 用本地 UUID 作主键（你们现在就是），**离线也能创建**，上传后用 `serverId` 关联，避免改主键。
- `rev`：后端每次接受写入就 +1 并返回；客户端拉取时比对。
- `syncStatus`：
  - `LOCAL` 新建未同步、`DIRTY` 有未同步的本地改动、`SYNCING` 推送中、`SYNCED` 已对齐、`CONFLICT` 需要解决。

---

## 3. 写入路径（本地优先）

任何编辑（含删除）只动本地，并打标记，UI 立刻刷新：

```kotlin
suspend fun upsertLocal(note: Note) {
    dao.upsert(note.copy(updatedAt = now(), syncStatus = DIRTY))
    SyncScheduler.requestSync()          // 触发一次后台同步（防抖）
}

suspend fun deleteLocal(id: String) {
    dao.markDeleted(id, now())           // deleted=true, syncStatus=DIRTY（tombstone）
    SyncScheduler.requestSync()
}
```

> 配合你们刚修的「内容没变就不写、不刷新 updatedAt」——只有真正变化才会进入 DIRTY，避免无谓同步。

---

## 4. 同步流程（SyncWorker）

一次同步分 **push → pull**（或反过来，按后端约定）：

**Push（上行）**：取 `syncStatus IN (LOCAL, DIRTY)` 的笔记，批量提交：
```
POST /notes/batch
[ { id, serverId?, rev, deleted, title, body, tags, folder, updatedAt }, … ]
→ 返回每条 { id, serverId, rev, accepted | conflict }
```
- `accepted`：本地写回 `serverId/rev`，置 `SYNCED`；若是 tombstone 则物理删除本地行。
- `conflict`（服务端 rev 比客户端基线新）：置 `CONFLICT`，进入冲突解决。

**Pull（下行）**：用游标增量拉取：
```
GET /notes?since=<lastCursor>
→ { changes:[ {serverId, rev, deleted, …fields} ], nextCursor }
```
- 远端新增/更新 → 按 `serverId` 找本地行，本地是 `SYNCED` 直接覆盖；本地是 `DIRTY` 则进入冲突解决。
- 远端 `deleted=true` → 删本地行（若本地无未同步改动）。
- 保存 `nextCursor` 供下次增量。

---

## 5. 冲突解决

从简到繁三档，按需要选：

1. **Last-Write-Wins（推荐起步）**：比较 `updatedAt`（或服务端 `rev`），晚者胜，覆盖另一边。实现简单，偶尔丢改动。
2. **字段级合并**：标题/正文/标签分别比对合并，减少整篇覆盖。
3. **CRDT / OT**：实时协作笔记才需要（Notion 级别），成本高，单人多端通常不必。

建议：**先 LWW**，必要时对"正文"单独做更细的合并或保留双版本（生成一条"冲突副本"）。

---

## 6. 删除与清理

- 删除 = `deleted=true`（tombstone）+ `DIRTY`，列表查询统一过滤 `WHERE deleted = 0`。
- push 成功后再物理删除本地行（以及关联录音文件/分片，复用 `RecordingRepository.deleteRecordingFilesOfNote`）。
- 远端下发的删除：本地无脏改动则直接删。

---

## 7. 同步触发（WorkManager）

- 编辑后防抖触发一次（`requestSync()`，合并短时间内多次请求）；
- 周期性兜底（如 15 分钟，带网络约束）；
- 网络从无到有时触发；
- App 启动/回前台触发一次。

WorkManager 自带约束 + 指数退避 + 持久化，断网/被杀后能续上，和录音上传 Worker 用同一套设施。

---

## 8. DAO 增量

```kotlin
@Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
fun visibleNotes(): Flow<List<NoteEntity>>

@Query("SELECT * FROM notes WHERE syncStatus IN ('LOCAL','DIRTY')")
suspend fun dirtyNotes(): List<NoteEntity>

@Query("UPDATE notes SET deleted = 1, syncStatus = 'DIRTY', updatedAt = :ts WHERE id = :id")
suspend fun markDeleted(id: String, ts: Long)

@Query("UPDATE notes SET serverId = :sid, rev = :rev, syncStatus = 'SYNCED', lastSyncedAt = :ts WHERE id = :id")
suspend fun markSynced(id: String, sid: String, rev: Long, ts: Long)
```

> 现有 `getAllNotes()` 改成 `visibleNotes()`（过滤 tombstone）。

---

## 9. 落地步骤

1. `NoteEntity` 增列（调试期 destructive fallback；上线前写迁移）。
2. `NoteDao` 增 `visibleNotes / dirtyNotes / markDeleted / markSynced`，首页改用 `visibleNotes`。
3. `NoteRepository`：写入打 `DIRTY`、删除走 tombstone。
4. 定义后端契约 `POST /notes/batch`、`GET /notes?since=`。
5. `NoteSyncWorker`（WorkManager）实现 push/pull + LWW 冲突。
6. `SyncScheduler`：编辑后防抖 + 周期 + 网络恢复触发。

---

## 10. 什么时候不必这么做

- 纯在线工具、无离线诉求；
- 内容极少（用 MMKV/文件即可）；
- 纯文件型笔记（Markdown 文件即库，如 Obsidian）。

笔记类（结构化、标签、搜索、离线）→ 用本地 DB，且按上面做同步层。

---

## 11. 落地进度（2026-07-04）

**已实现**：
- §2 `NoteEntity` 同步列（serverId/rev/syncStatus/deleted/lastSyncedAt）+ `SyncStatus` —— 已在。
- §3 写入打 `DIRTY` 且保留 serverId/rev（`RoomNoteRepository.addOrUpdate`）；§6 删除走 tombstone/物理删（按是否同步过）——已在。
- §8 DAO：`getAllNotes`(过滤 tombstone)/`dirtyNotes`/`markDeleted`/`markSynced`/`restore` 已在；本次补 `pendingPushNotes`(未删待推) 与 `markConflict`。
- §4/§7 **`NoteSyncWorker`（@HiltWorker）+ `NoteSyncScheduler`**：push-only 上行同步（create/update 凭 rev，applied=false→CONFLICT），网络约束 + 指数退避；触发点：仓库写入/恢复后 `requestSync`、App 启动、周期(15min)。复用录音那套 WorkManager/Hilt 设施。

**受后端限制未做（缺口）**：
- **Pull 增量**（§4 `GET /notes?since=`）、**批量 push**（`POST /notes/batch`）、**删除同步**（后端无 DELETE /notes）：现后端只有 `POST /notes`、`GET /notes/{id}`、`PUT /notes/{id}`。故当前**只上行、不下行**，tombstone 不推送（本地软删仅用于回收站）。待后端补齐上述端点后，再加 pull 增量、批量与删除同步，并把 `CONFLICT` 用 pull 到的版本做 LWW/字段级合并。
- content 约定当前为 `{"body": <编辑器文档字符串>}`（见 `RemoteNotesRepository`），后续可与 `NoteDocument` 结构对齐。

> 上线前：`AppDatabase` 关闭 destructive fallback 并为同步列写正式迁移。
