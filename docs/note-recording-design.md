# 笔记录音能力设计

- **日期**：2026-07-03
- **范围**：`common/audio/*`、`feature/create/recording/*`、`data/*Recording*`、`common/config/AppConfig.Media`、后端 files API 对接
- **相关文档**：`recording-integrity-design.md`、`../code-review/`、后端 `doc/api-reference.md`

## 目标（本次需求）

1. 录音**不分片**，单文件落盘
2. 本地存储，按磁盘可用空间**滚动删除**
3. 具备**上传**能力
4. **AAC** 编码，最大录制 **30 分钟**（时长阈值抽成变量）
5. 补齐其他易忽略的细节

---

## 一、现状评估

已有一套较完整的录音基建，本次是「改造」而非「从零」：

| 模块 | 现状 | 与需求差距 |
|------|------|-----------|
| `AudioRecorder` | MediaRecorder，AAC 写入 MPEG_4(.m4a)，**按 5MB 分片**（`setNextOutputFile`） | 要去分片；无 30 分钟上限 |
| `RecordingService` | 前台服务，锁屏/后台不中断，通知栏暂停/停止 | 基本可用 |
| `RecordingController` | 录音共享快照（计时/振幅/结果） | 可用 |
| `RecordingCleaner` | 低空间时按最旧 mtime 删除 | **会误删未上传/已挂笔记的录音**（见 §五） |
| `RecordingFlowStateMachine` | 录制→文件→上传 全生命周期状态机 | 上传器是 `None` 占位 |
| `RecordingRepository` + Room | recordings→segments，两级 SHA-256，`UploadStatus` | 分片模型需简化；上传状态未真正驱动 |
| 上传 | **无**（`RecordingUploader.None`） | 需对接后端 files API |
| 权限 | RECORD_AUDIO / FGS microphone / POST_NOTIFICATIONS 均已声明 | 就绪 |

---

## 二、配置变量（集中到 `AppConfig.Media`）

把散落/新增的阈值统一提出来，尤其是 30 分钟上限与用于反推文件大小的码率：

```kotlin
object Media {
    // —— 录音编码 ——
    /** 录音最大时长（秒）。30 分钟。 */
    const val MAX_RECORD_SECONDS = 30 * 60            // 1800
    /** 录音最大时长（毫秒），供 MediaRecorder.setMaxDuration。 */
    const val MAX_RECORD_MS = MAX_RECORD_SECONDS * 1000L
    /** AAC 编码码率（bps）。64k：兼顾音质与体积，30min ≈ 14.4MB < 后端 16MiB 上限。 */
    const val AUDIO_BITRATE = 64_000
    /** 采样率（Hz）。用于 AI 语音分析，16k 单声道即 ASR 标准输入，且显著减小体积。 */
    const val AUDIO_SAMPLE_RATE = 16_000
    /** 声道数。语音/AI 分析用单声道。 */
    const val AUDIO_CHANNELS = 1

    /** 录音上传的 MIME（须在后端 files 允许类型内）。 */
    const val AUDIO_MIME = "audio/aac"

    // —— 本地存储滚动删除（沿用现有，语义不变）——
    const val STORAGE_MIN_FREE_MB = 30L
    const val STORAGE_TARGET_FREE_MB = 200L
    /** 开始录音前要求的最低可用空间（MB），不足则先清理，再不足则拦截。 */
    const val RECORD_MIN_FREE_MB = 50L

    // —— 产品判定（沿用现有）——
    const val MIN_RECORD_SECONDS = 3
    const val NO_VOICE_THRESHOLD = 1800

    // ❌ 删除：AUDIO_SEGMENT_BYTES（不再分片）
}
```

**大小自检（重要不变式）**：`AUDIO_BITRATE / 8 * MAX_RECORD_SECONDS` 必须 < 后端单文件上限（`MAX_DOCUMENT_SIZE = 16 MiB`）。
- **64kbps × 1800s ≈ 14.4 MB ✅（已选）**——留约 1.6MiB 余量应对容器开销。
- 48kbps × 1800s ≈ 10.8 MB ✅（更保守的备选）
- 128kbps × 1800s ≈ **28.8 MB ❌ 超限**——故不采用；如需更高码率须同步抬高后端上限。

> AI 分析说明：录音用于语音转写 / 内容分析，采样率与声道固定为 **16kHz 单声道**（ASR 标准输入），对识别质量的影响远大于码率；64kbps 已足够，无需为 AI 堆高码率。

建议在初始化处加一行断言，防止后人改码率或时长后破坏该不变式：
```kotlin
require(AUDIO_BITRATE / 8L * MAX_RECORD_SECONDS < MAX_DOCUMENT_SIZE) { "录音上限可能超过后端文件大小限制" }
```

---

## 三、不分片改造（`AudioRecorder`）

去掉 `segments`/`nextIndex`/`setNextOutputFile`/`setMaxFileSize` 与 `MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING`，改为单文件；同时接 30 分钟自动停止。

```kotlin
fun start(): Boolean = try {
    File(context.filesDir, AUDIO_DIR).mkdirs()
    sessionId = UUID.randomUUID().toString()
    outputFile = File(context.filesDir, "$AUDIO_DIR/voice_$sessionId.aac")

    val rec = if (SDK_INT >= S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    rec.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)   // 原始 AAC(.aac)，匹配后端 audio/aac
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(AppConfig.Media.AUDIO_BITRATE)
        setAudioSamplingRate(AppConfig.Media.AUDIO_SAMPLE_RATE)
        setAudioChannels(AppConfig.Media.AUDIO_CHANNELS)
        setOutputFile(outputFile!!.absolutePath)
        setMaxDuration(AppConfig.Media.MAX_RECORD_MS.toInt())   // 30min 自动停止
        setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                // 触达上限：等同用户「停止」，由服务优雅收尾（见 §四）
                onMaxDurationReached?.invoke()
            }
        }
        prepare(); start()
    }
    recorder = rec; true
} catch (e: Exception) { … 失败清理单文件 … ; false }
```

- `outputPath` 直接返回 `outputFile`；删除 `segmentPaths`。
- **容器选择**：`AAC_ADTS` 产出 `.aac`（`audio/aac`），与后端允许类型一致，也贴合「AAC 编码」字面要求。代价：raw AAC 不带时长元数据，播放时长以库里 `durationMs` 为准（我们本就记录）。
  - 备选：保留 `.m4a`（`MPEG_4`），但需**后端 files 允许类型加 `audio/mp4`**（前后端一体化可改）。二选一，本设计取 ADTS 免改后端。

**数据模型（已定：方案 B —— 合并单表）**：去掉 `recording_segments` 表，把 `path` / `bytes` / `sha256` / `uploadStatus` 直接并入 `recordings` 行（1 文件 = 1 录音），删除两级 hash-of-hashes（改为对单文件直接算 sha256）。因录音功能尚未上线、无存量数据，做一次 Room 迁移（升版本，重建/替换表）风险低。
- 若日后**恢复分片**：需再改结构，但那是**新增一张 `recording_segments` 表**的**加法式迁移**（`CREATE TABLE` + 升版本，不动 `recordings` 表、不丢数据），风险可控。故现在按 YAGNI 直接合表，不为「以后可能分片」预留死结构。

---

## 四、30 分钟上限与自动收尾

`RecordingService` 持有 recorder，`onMaxDurationReached` 回调里走与 `handleStop()` 相同的收尾（停止→回写 `result`→退前台），保证：
- 通知栏计时到 30:00 自动结束；
- UI 收到 `RecordingResult` 与手动停止**同一路径**，自动进入上传；
- 注意 `setMaxDuration` 计的是**实际录制时长**（暂停不计），与我们 loop 里「暂停不累加」一致。

---

## 五、本地存储 + 滚动删除（修正现有隐患）

滚动删除已存在，但 **`RecordingCleaner` 当前按 mtime 盲删 `note_audio` 下最旧文件，会删掉「尚未上传」或「已挂到笔记」的录音，造成数据丢失**。改造：

1. **只删「可安全回收」的录音**：`uploadStatus ∈ {VERIFIED, UPLOADED}` 且未被当前编辑引用。删除经 `RecordingRepository.deleteRecording()` 走（连带删库行），而非直接删文件。
2. **孤儿文件清理**：`note_audio` 下、不在 `knownSegmentPaths()` 里的文件可直接删（录制中断残留）。
3. **录制前预检**：`start()` 前若可用空间 < `RECORD_MIN_FREE_MB`，先 `cleanupIfNeeded()`；仍不足则**拦截录制并提示**，不要录到一半失败。
4. 触发时机：保留启动空闲触发，另加「每次开始录音前」一次。

删除优先级建议：孤儿文件 → 已上传(VERIFIED) 的最旧 → （仍不足才提示用户）。**永不**自动删 `LOCAL_ONLY/PENDING/UPLOADING`。

---

## 六、上传能力（对接后端 files API）

后端三段式（见 `api-reference.md` §5）：`presign → 直传对象存储 → confirm`。落地为真实 `RecordingUploader`，替换 `None`。

**新增网络层**（复用 §统一响应 `apiCall`）：
```kotlin
interface FilesApi {
    @POST("api/v1.0/files/presign")
    suspend fun presign(@Body body: PresignReq): Response<ApiResponse<PresignResp>>
    @POST("api/v1.0/files/{id}/confirm")
    suspend fun confirm(@Path("id") id: String): Response<ApiResponse<FileView>>
}
// 直传：presigned POST 需先带 policy fields，再带名为 "file" 的文件 part
@Multipart @POST
suspend fun uploadToStorage(@Url url: String,
    @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
    @Part file: MultipartBody.Part): Response<ResponseBody>
```
> 现有 `ApiService.upload` 只发单个 file part，缺 policy fields，需按上式补 `@PartMap`。

**上传步骤**（`FilesRepository.upload(file): Result<String?>`）：
1. `declaredSizeBytes = File(path).length()`、`contentType = AUDIO_MIME`、`originalFilename`；调 `presign`。
2. 用 `upload.fields` + 文件直传对象存储（S3 presigned POST，字段顺序在前）。
3. 调 `confirm(fileId)` → `FileView(status=READY)`；后端会核对 Content-Length 与声明值，故第 1 步大小必须精确。
4. 回写 `RecordingRepository.updateRecordingStatus(UPLOADING→VERIFIED, remoteUrl)`；返回可用引用。

**可靠性——建议用 WorkManager 承载上传，而非 `viewModelScope`**：
- 现状态机在 `scope`（通常 viewModelScope）里上传，**离开编辑页/进程被杀即中断**，大文件（~10MB）尤其需要后台续传。
- 方案：状态机 `stopAndUpload` 后置 `LOCAL_ONLY→PENDING` 入库，入队一个带 `NetworkType.CONNECTED` 约束的 `UploadWorker`；Worker 执行上述三步并更新 `UploadStatus`。App 启动 / 网络恢复时用 `pendingUploads()` 重新入队。
- 状态机的 `Uploading/Uploaded/UploadFailed` 仍用于**前台可见**的即时反馈；持久真相以 Room `UploadStatus` 为准。

---

## 七、其他易忽略的细节（需求 #5）

1. **附件接口需扩音频类型（已定方案 A）**：后端 `POST /notes/{noteId}/attachments` 的 `kind` 枚举当前**仅 `IMAGE`**，音频挂笔记会得 `40004`。**决策：扩 `attachments.kind = AUDIO`**，音频复用现有附件生命周期（挂载/列出/解绑、签名下载 URL 自动续签、删笔记级联清理）。理由：客户端已把「录音↔笔记」建成关系模型（Room `recordings` 带 `noteId` 外键 + `uploadStatus` + `remoteUrl`），天然对应服务端一个关系；音频文件大，级联删除与服务端配额比塞进 `content` 更省心。
   - 需后端确认两点：(1) 附件类型校验放行 `audio/aac`；(2) 音频是否共用「合计 5 个」附件上限，还是单独计数/放宽（录音可能一条笔记多段，建议单独计数）。
2. **游客不能上传**：presign 需 JWT。`AppUserProvider.isGuest` 为真时保持 `LOCAL_ONLY`，登录后再补传（`pendingUploads()`）。UI 需提示「登录后自动同步」。
3. **MIME/容器一致性**：ADTS `.aac` = `audio/aac`（匹配）；若改用 `.m4a` 则是 `audio/mp4`（后端需放行）。三处（编码输出、presign contentType、后端允许类型）必须一致。
4. **码率×时长 ≤ 16MiB 不变式**：见 §二，改任一参数都要复核；建议加运行时 `require`。
5. **presign 有效期 5 分钟**：大文件在弱网可能直传超时，需重试整个 presign→upload（fileId 作废重来），confirm 幂等性以后端为准。
6. **暂停对 `setMaxDuration` 的语义**：仅计实际录制时长，暂停不消耗额度，与 UI 计时口径一致（已核对）。
7. **播放时长来源**：raw AAC 无容器时长，播放器/进度条用库里 `durationMs`，勿依赖文件解析。
8. **存储位置**：`filesDir/note_audio`（应用私有、不进公共媒体库、随卸载清除）——正确；勿放 `cacheDir`（系统可能随时清）也勿放外部存储。
9. **完整性哈希**：单文件后 `sha256` 仍建议保留，供 confirm 后端校验/本地防篡改；两级哈希退化为单级。
10. **并发丢弃竞态**：`discard` 与上传回调竞态在状态机已处理（回调前校验仍在本次 `Uploading`），改 WorkManager 后要用 `recordingId` 而非对象相等来判定，避免误落状态。
11. **通知**：达上限自动停止时，通知应先反映「录音完成」再消失，别让用户以为丢录。

---

## 八、改动清单（落地顺序）

1. `AppConfig.Media`：加录音编码/时长/预检变量，删 `AUDIO_SEGMENT_BYTES`，加不变式 `require`。
2. `AudioRecorder`：去分片 → 单文件 `.aac`（ADTS）；接 `setMaxDuration` + 回调。
3. `RecordingService`：处理 `onMaxDurationReached`，复用停止收尾。
4. `RecordingCleaner`：改为经仓库安全删除 + 孤儿清理 + 录制前预检。
5. 网络层：`FilesApi` + `FilesRepository`（presign/直传/confirm），补 `@PartMap` 直传。
6. `RecordingUploader` 真实实现；`UploadWorker`（WorkManager）承载续传；状态机接线。
7. 数据模型：合并单表（方案 B）——删 `recording_segments`，字段并入 `recordings`，写 Room 迁移升版本。
8. **后端（方案 A）**：附件 `kind` 枚举加 `AUDIO`；附件类型校验放行 `audio/aac`；确定音频的附件上限口径（建议单独计数）。files 允许类型 `audio/aac` 已在。
9. **客户端挂载**：录音 confirm 成功后调 `POST /notes/{noteId}/attachments {fileId}` 挂到笔记；`AttachmentView.kind` 增加 `AUDIO` 分支，列表/播放用其签名 `downloadUrl`。

---

## 十、落地进度（2026-07-03）

**已实现（本次）**：
- `AppConfig.Media`：录音编码/时长/预检变量，删 `AUDIO_SEGMENT_BYTES`，加大小不变式 `require`（改动清单 #1）。
- `AudioRecorder`：去分片 → 单文件 `.aac`（AAC_ADTS），`setMaxDuration(30min)` + `onMaxDurationReached` 回调（#2）。
- `RecordingService`：接自动收尾（重入保护）、录制前空间预检（不足先清理再拦截）（#3）。
- 上传网络层：`common/net/FilesApi`（presign/直传 `@PartMap`/confirm + DTO）、`NetworkModule.filesApi`、`data/FilesRepository`（三段式，走 `apiCall`）（#5）。
- `feature/create/recording/FileRecordingUploader`：真实上传器，游客短路（#6 的上传部分）。
- **数据模型合表（#7）**：删 `RecordingSegmentEntity`/`recording_segments`，字段（path/bytes/sha256/fileId/remoteUrl）并入 `RecordingEntity`；`RecordingDao`/`RecordingRepository`/`RoomRecordingRepository` 单文件化；`AppDatabase` 去 segment 实体、版本 7→8（`fallbackToDestructiveMigration` 销毁重建，无需迁移脚本）。
- **`RecordingCleaner` 安全删除（#4）**：改 suspend + DB 感知——只回收 `UPLOADED/VERIFIED` 的最旧录音、孤儿文件带 10min 年龄保护；新增 `ensureSpaceForRecording`，`RecordingService` 起前台后于 IO 线程预检（满足 FGS 5s 约束）。

- **WorkManager 续传（#6 增强）**：依赖 `work-runtime-ktx` + `androidx.hilt:hilt-work`(+KSP `hilt-compiler`)；`NovieApplication` 实现 `Configuration.Provider` 注入 `HiltWorkerFactory`、Manifest 移除默认 `WorkManagerInitializer`（on-demand 初始化）；`@HiltWorker UploadWorker` 走 `FilesRepository` 上传并回写 `RecordingRepository` 状态（网络约束 + 指数退避）；`RecordingUploadScheduler.enqueue/resumeOnIdle` 唯一入队与断点续传（启动空闲重排未完成上传）。DI：`DataModule` 提供 `RecordingRepository`、`FilesRepository` 加 `@Inject`、DAO 加 `getById`。

**待落地（下一阶段）**：
- 状态机接线到录音 ViewModel/UI：停止后 `saveRecording` 落库并 `RecordingUploadScheduler.enqueue(recordingId)`；前台即时反馈用状态机的 `Uploading/Uploaded`，持久真相以 Room `uploadStatus` 为准。可用 `FileRecordingUploader` 做前台即时上传，或统一走 Worker。
- confirm 成功后调 `POST /notes/{noteId}/attachments {fileId}` 挂载到笔记。
- **后端**：附件 `kind=AUDIO` + 放行 `audio/aac`（#8）。
- 上线前：`AppDatabase` 关闭 `fallbackToDestructiveMigration`、`exportSchema=true` 并补写迁移。

> 遗留：`FileUtils.recordingHash`（分片时代的 hash-of-hashes）已无调用方，可后续清理。

> 说明：本环境无 Android SDK，未能编译验证；改动均按现有 net/audio 风格与统一响应层对齐，经人工审查。
