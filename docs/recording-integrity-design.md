# 笔记 · 录音 · 分片：本地存储与完整性设计

## 1. 关系与目标

层级关系（一对多，两级）：

```
Note (笔记) 1 ──< Recording (录音) 1 ──< RecordingSegment (分片)
```

- 一个笔记可有多个录音；
- 一个录音由多个分片文件组成（`voice_<uuid>_segN.m4a`，见 `AudioRecorder`）；
- **完整性**要求：
  - 每个分片有独立校验值（防止单个文件损坏/被篡改）；
  - 整个录音有一个总校验值（覆盖全部分片与其顺序）；
  - 这两级校验值都随上传发给后端，后端可逐分片校验、整体校验。

存储选型：用 **Room**（关系、外键级联、可查询、与现有 `NoteEntity` 一致），MMKV 只适合扁平配置，不用于此。

---

## 2. 完整性算法

统一用 **SHA-256**。

- **分片完整性**：`segment.sha256 = SHA-256(分片文件全部字节)`，同时记录 `bytes`（字节数）。
- **录音完整性（hash-of-hashes / 逐级哈希）**：按 `index` 升序拼接各分片的十六进制哈希，再求一次 SHA-256：

  ```
  recording.sha256 = SHA-256( seg0.sha256 + "\n" + seg1.sha256 + "\n" + … )
  ```

  好处：不必把所有分片重新读一遍（分片哈希已算过），且天然绑定了**分片内容 + 顺序 + 数量**。后端用收到的各分片哈希按同样规则重算即可比对，无需在服务端拼接原始字节。

  > 备选：对「分片原始字节顺序拼接」整体求 SHA-256。更直观，但要求端/云用完全一致的拼接方式，且要全量读字节。除非后端明确需要「合并文件的哈希」，否则推荐 hash-of-hashes。

**计算时机**（都放 IO 线程，不阻塞主线程）：

- 分片哈希：每当一个分片「封口」时计算（滚动到下一分片时、或停止录音时的最后一片）；
- 录音哈希：停止录音、全部分片封口后计算一次。

---

## 3. Room 数据模型

```kotlin
@Entity(
    tableName = "recordings",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"], childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("noteId")],
)
data class RecordingEntity(
    @PrimaryKey val id: String,          // UUID（= AudioRecorder.sessionId）
    val noteId: String,
    val createdAt: Long,
    val durationMs: Long,
    val totalBytes: Long,
    val segmentCount: Int,
    val sha256: String,                  // 整体完整性（hash-of-hashes）
    val uploadStatus: String = UploadStatus.LOCAL_ONLY.name,
    val remoteUrl: String? = null,       // 后端 finalize 后返回
)

@Entity(
    tableName = "recording_segments",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"], childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("recordingId"),
        Index(value = ["recordingId", "index"], unique = true), // 顺序唯一
    ],
)
data class RecordingSegmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val index: Int,                      // 0-based 顺序
    val path: String,                    // 内部存储绝对路径
    val bytes: Long,
    val durationMs: Long,
    val sha256: String,                  // 分片完整性
    val uploadStatus: String = UploadStatus.PENDING.name,
)
```

状态枚举（存字符串，避免迁移负担）：

```kotlin
enum class UploadStatus { LOCAL_ONLY, PENDING, UPLOADING, UPLOADED, FAILED, VERIFIED }
```

- 分片用 `PENDING → UPLOADING → UPLOADED / FAILED`；
- 录音用 `LOCAL_ONLY → UPLOADING → VERIFIED / FAILED`（`VERIFIED` = 后端整体校验通过）。

关联查询：

```kotlin
data class RecordingWithSegments(
    @Embedded val recording: RecordingEntity,
    @Relation(parentColumn = "id", entityColumn = "recordingId")
    val segments: List<RecordingSegmentEntity>, // 用时按 index 排序
)

@Dao interface RecordingDao {
    @Upsert suspend fun upsertRecording(r: RecordingEntity)
    @Upsert suspend fun upsertSegments(s: List<RecordingSegmentEntity>)

    @Transaction
    @Query("SELECT * FROM recordings WHERE noteId = :noteId ORDER BY createdAt")
    fun recordingsOfNote(noteId: String): Flow<List<RecordingWithSegments>>

    @Transaction
    @Query("SELECT * FROM recordings WHERE uploadStatus != 'VERIFIED'")
    suspend fun pendingUploads(): List<RecordingWithSegments>

    @Query("UPDATE recording_segments SET uploadStatus = :st WHERE id = :id")
    suspend fun setSegmentStatus(id: String, st: String)

    @Query("UPDATE recordings SET uploadStatus = :st, remoteUrl = :url WHERE id = :id")
    suspend fun setRecordingStatus(id: String, st: String, url: String?)

    @Query("SELECT path FROM recording_segments WHERE recordingId = :id")
    suspend fun segmentPaths(id: String): List<String>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecording(id: String) // 级联删 segments 行
}
```

> 调试阶段沿用 `fallbackToDestructiveMigration(dropAllTables = true)`（与现有约定一致）；上线前为新增两张表写正式 Migration。

---

## 4. 完整性工具

```kotlin
object FileIntegrity {
    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").let { md ->
            file.inputStream().use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }

    /** 录音整体哈希：按顺序拼接分片哈希再求 SHA-256。 */
    fun recordingHash(segmentHashesInOrder: List<String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(segmentHashesInOrder.joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

本地「保存即校验」：录音停止时算好两级哈希入库；上传前可选地重算一次分片哈希与库里比对，提前发现磁盘损坏（`integrity check`）。

---

## 5. 保存流程（停止录音后）

`RecordingService.handleStop()` 拿到 `AudioRecorder.segmentPaths` 后交给 Repository：

```kotlin
suspend fun saveRecording(noteId: String, sessionId: String, segmentFiles: List<File>, durationMs: Long) {
    val segs = segmentFiles.mapIndexed { i, f ->
        RecordingSegmentEntity(
            id = UUID.randomUUID().toString(),
            recordingId = sessionId, index = i, path = f.absolutePath,
            bytes = f.length(), durationMs = /* 每片时长，可估算 */ 0,
            sha256 = FileIntegrity.sha256(f),
        )
    }
    val recording = RecordingEntity(
        id = sessionId, noteId = noteId, createdAt = System.currentTimeMillis(),
        durationMs = durationMs, totalBytes = segs.sumOf { it.bytes },
        segmentCount = segs.size,
        sha256 = FileIntegrity.recordingHash(segs.map { it.sha256 }),
    )
    dao.upsertRecording(recording)
    dao.upsertSegments(segs)
}
```

---

## 6. 上传契约（后端）

三段式，天然支持断点续传与逐分片/整体校验：

1. **创建/登记 manifest** — `POST /recordings`
   ```json
   {
     "recordingId": "...", "noteId": "...",
     "segmentCount": 3, "totalBytes": 1234567, "durationMs": 65000,
     "sha256": "<整体哈希>",
     "segments": [
       {"index":0,"sha256":"...","bytes":40000},
       {"index":1,"sha256":"...","bytes":40000},
       {"index":2,"sha256":"...","bytes":4567}
     ]
   }
   ```
   后端据此知道要收哪几片、各自该是什么哈希。

2. **逐分片上传** — `PUT /recordings/{id}/segments/{index}`（multipart/二进制 + `X-Sha256` 头）
   - 后端收完即对字节重算 SHA-256，与 manifest 中该分片哈希比对，不符则 4xx；
   - 客户端把该分片标 `UPLOADED`，失败标 `FAILED`；
   - **幂等**：以 `(recordingId, index, sha256)` 为键，重传同一片不产生重复。

3. **完成/校验** — `POST /recordings/{id}/complete`
   - 后端用收到的各分片哈希按同样规则重算 `recordingHash`，与 manifest 的 `sha256` 比对；全部一致则落库并返回 `remoteUrl`；
   - 客户端把录音标 `VERIFIED` 并写入 `remoteUrl`。

**续传**：App 启动或网络恢复时查 `pendingUploads()`，只重传 `PENDING/FAILED` 的分片，再 `complete`。建议用 **WorkManager**（带网络约束 + 指数退避）驱动，保证后台可靠、不丢。

---

## 7. 文件与数据库一致性

- **删除**：删 `recordings` 行会级联删 `recording_segments` 行，但**不会删磁盘文件**。Repository 删录音时要先 `segmentPaths(id)` 取路径删文件，再删行（可复用/配合 `RecordingCleaner`）。
- **孤儿清理**：`RecordingCleaner` 增加一轮——扫描录音目录里「DB 中已无对应分片行」的文件并删除，反向也校验「DB 有行但文件缺失」标记为损坏。
- **删笔记**：`NoteEntity` 删除经外键级联到 recordings/segments；同样需要先取分片路径删文件。

---

## 8. 状态机小结

```
分片:  PENDING ──upload──> UPLOADING ──ok──> UPLOADED
                               └─fail─> FAILED ──retry──> UPLOADING
录音:  LOCAL_ONLY ──开始上传──> UPLOADING ──complete校验通过──> VERIFIED
                                   └─任一分片/整体校验失败─> FAILED
```

---

## 9. 落地步骤建议

1. 新增 `RecordingEntity` / `RecordingSegmentEntity` + `RecordingDao`，`AppDatabase` 注册两张表（调试期 destructive fallback）。
2. 新增 `FileIntegrity` 工具与 `RecordingRepository`。
3. `RecordingService.handleStop` 接 `saveRecording`（传入当前笔记 id —— 需要把 noteId 传进录音流程）。
4. 新增 `RecordingUploadWorker`（WorkManager）实现三段式上传 + 续传。
5. `RecordingCleaner` 增加孤儿文件清理。
```
