# 笔记图片上传设计（CreateScreen）

> 目标：让 CreateScreen 里插入的图片从「仅本地」升级为「上传到服务端并挂到笔记」，
> 使笔记在多端/重装后仍能看到图片。基于现有接口文档（`my-novie-backend/doc/frontend-api.md`
> §5 附件、§6 文件上传）与现有客户端基建设计，尽量复用。

## 1. 背景与现状

CreateScreen 目前的图片是**纯本地**的：

- 选图/拍照 → `ImageStore.importImage` 落到内部存储 → `editor.insertImage(localPath, w, h)`
  插入一个 `ImageBlock(path, width, height)`；
- `ImageBlock.path` 是本地文件路径，序列化进笔记 `content`（`{"body": <文档 JSON>}`）；
- 换设备/重装后本地文件丢失，图片即失效。

同时后端 + 客户端已具备上传所需的全部零件，本设计**主要是把它们接起来**，不新造轮子。

### 可复用的现有基建

| 能力 | 位置 | 说明 |
|---|---|---|
| 文件三段式上传 | `data/FilesRepository.uploadFile(file, contentType): Result<fileId>` | presign → 直传对象存储 → confirm，返回 `fileId`。**已实现，直接复用**。 |
| 文件接口 | `common/net/FilesApi`（presign/uploadToStorage/confirm） | 已实现。 |
| 图片落盘/压缩/降采样 | `feature/create/editor/ImageStore` | 选图/拍照后本地缓存，供即时预览与上传。 |
| 富文本图片块 | `feature/create/editor/NoteEditorState.ImageBlock` | 需扩展字段（见 §4）。 |
| 附件数量上限 | `AppConfig.Media.MAX_ATTACHMENTS = 5`、`editor.attachmentCount` | 与后端「一笔记最多 5 附件」一致。 |
| 笔记服务端持久化 | `CreateViewModel.saveNow`（POST/PUT） | 图片挂载依赖笔记先有服务端 id。 |

## 2. 后端接口（引用，不新增）

**文件上传（§6，无需 noteId）**：
- `POST /files/presign` `{contentType, declaredSizeBytes, originalFilename}` → `{fileId, upload{url,fields/headers}, expiresAt}`
- 直传对象存储（presigned POST/PUT，不走信封）
- `POST /files/{id}/confirm` → `FileView{status=READY}`
- 允许类型：`image/jpeg`、`image/png`、`image/webp`（本特性只用图片）；≤ 16 MiB（超限 `40005`，类型不符 `40004`）

**笔记附件（§5，需 noteId）**：
- `POST /notes/{noteId}/attachments` `{fileId}` → `AttachmentView{fileId, kind:"IMAGE", originalFilename, sizeBytes, downloadUrl, expiresAt}`。**幂等**（重复挂同一 fileId 仍 200）。
- `GET /notes/{noteId}/attachments` → `AttachmentView[]`（每次返回**新鲜的**签名 `downloadUrl` + `expiresAt`）
- `DELETE /notes/{noteId}/attachments/{fileId}` → 204
- 错误：数量超限 `40903`、文件未 confirm `40902`、文件不存在 `40405`、笔记在回收站 `40905`、类型非图片 `400`

关键约束：`downloadUrl` **有时效**，过期后需重新 `GET attachments` 换新 URL；因此**不把 downloadUrl 长期写进 content**，content 只存稳定的 `fileId`（见 §4、§6）。

## 3. 总体流程

图片有两个独立阶段，**解耦**处理：

1. **上传（拿 fileId）** —— 不依赖 noteId，插入图片时即可后台进行。
2. **挂载（attach 到笔记）** —— 依赖 noteId，笔记落盘（POST）拿到 id 后再做。

```
插入图片
  → ImageStore 落本地缓存 (path)         # 即时预览，用户不等待
  → 插入 ImageBlock(path, state=UPLOADING)
  → 后台 FilesRepository.uploadFile(file) → fileId
      成功: ImageBlock.state=UPLOADED, fileId=xxx
      失败: ImageBlock.state=FAILED（可重试）

保存笔记 (saveNow)
  → 若无 noteId 先 POST 创建（沿用现有逻辑，见 border-color 同款「先建后挂」）
  → 对 body 中所有 state=UPLOADED 且未挂载的 ImageBlock:
        POST /notes/{noteId}/attachments {fileId}   # 幂等
  → 对已从 body 删除的历史附件:
        DELETE /notes/{noteId}/attachments/{fileId}  # 解除挂载
  → PUT/POST 笔记正文（content 内 ImageBlock 已带 fileId）

打开已有笔记 (loadNote)
  → GET /notes/{id} 取 content（含各 ImageBlock 的 fileId）
  → GET /notes/{id}/attachments 取 fileId → downloadUrl 映射
  → 渲染：本地 path 命中则用本地；否则用 downloadUrl（Coil 加载 + 缓存）
```

## 4. 数据模型改动

### ImageBlock 扩展（`NoteEditorState.kt`）

```kotlin
class ImageBlock(
    val path: String,               // 本地缓存路径（可能失效）；即时预览用
    val width: Int = 0,
    val height: Int = 0,
    val fileId: String? = null,     // 服务端文件 id（上传成功后回填）——content 的稳定引用
    val uploadState: UploadState = UploadState.LOCAL,
    override val id: String = UUID.randomUUID().toString(),
) : EditorBlock

enum class UploadState { LOCAL, UPLOADING, UPLOADED, FAILED }
```

因 `ImageBlock` 目前是不可变类、且 `NoteEditorState` 用 `_blocks` 列表管理，需新增
「按 blockId 替换某 ImageBlock」的能力（更新 uploadState / fileId），例如
`fun updateImage(id, transform: (ImageBlock)->ImageBlock)`。

### content 序列化（`NoteEditorState` 的 toJson/fromJson）

ImageBlock 序列化**新增 `fileId`**（保留 `path/width/height` 以便同机离线快速预览）：

```json
{ "type": "image", "path": "...", "width": 800, "height": 600, "fileId": "uuid-or-absent" }
```

- 反序列化：`fileId` 可缺省（老数据 / 尚未上传）。
- **不序列化 downloadUrl / uploadState**：URL 有时效、状态是运行期的。

## 5. 组件与分层

遵循现有分层（ViewModel → Repository → apiCall，DTO 不外泄）：

- **新增 `common/net/AttachmentsApi`**（对齐 §5）：
  - `@POST("api/v1.0/notes/{noteId}/attachments") attach(@Path noteId, @Body {fileId}) : Response<ApiResponse<AttachmentDto>>`
  - `@GET(".../attachments") list(@Path noteId) : Response<ApiResponse<List<AttachmentDto>>>`
  - `@DELETE(".../attachments/{fileId}") detach(...) : Response<ApiResponse<Unit>>`（204）
  - `AttachmentDto{fileId, kind, originalFilename, sizeBytes, downloadUrl, expiresAt}`
  - `NetworkModule.attachmentsApi` 懒加载。
- **新增 `data/AttachmentsRepository` + 实现**：领域模型 `RemoteAttachment(fileId, downloadUrl, expiresAt, ...)`；
  `attach(noteId, fileId)`、`listAttachments(noteId)`、`detach(noteId, fileId)`，走统一 `apiCall` 三态。
- **复用 `FilesRepository.uploadFile`** 做上传。
- **`CreateViewModel`**：
  - 新增 `uploadImage(blockId, localFile, contentType)`：调 `FilesRepository.uploadFile` → 回填 `ImageBlock.fileId/uploadState`（经 editor 的 updateImage）。
  - `saveNow` 之后（笔记已有 id）：对齐/同步附件——挂新增、摘删除（见 §3、§6）。可与「先建后挂」逻辑一致（参考 border-color 的 `applyBorderColor`：先确保有 id 再打附件端点）。
  - 失败经已有的 `saveError`/新的一次性事件反馈到 UI（Toast）。
- **`CreateScreen` / 编辑器渲染**：
  - 图片渲染改为 `path` 优先、`downloadUrl` 兜底（`loadNote` 时由 `GET attachments` 提供 URL 映射）；
  - 每张图叠加上传态角标：UPLOADING（转圈）、FAILED（重试按钮）。
  - 现有选图/拍照插入逻辑基本不变，只是在插入后追加一次 `viewModel.uploadImage(...)`。

## 6. 关键决策

1. **上传时机 = 插入即传，挂载 = 保存时**。好处：用户不等待、图片先本地即显；挂载依赖 noteId，
   自然放到 saveNow 之后（与 border-color「先建后挂」一致）。
2. **content 只存 `fileId`，不存 `downloadUrl`**（URL 有时效）。渲染时的 URL 由 `GET attachments` 现取，
   过期就重列。
3. **附件集合 = 正文中的图片集合**。保存时做一次「对账」：body 里的 `fileId` 集合与服务端 attachments 求差，
   多的 `attach`、少的 `detach`。`attach` 幂等，重复安全。
4. **数量上限 5（一次最多选 5 张）**：单条笔记图片附件上限 5（`AppConfig.Media.MAX_ATTACHMENTS`）；
   **一次多选也上限 5**——`MAX_IMAGE_PICK` 由 9 调整为 **5**，并仍受剩余槽位约束
   `imagePickMax = minOf(5, remainingSlots)`（`remainingSlots = 5 - attachmentCount`）。
   前置用 `attachmentCount` 拦截，服务端 `40903` 作兜底提示。一次多选时**并发上传限 2–3**、其余排队。
5. **上传格式：按 alpha 二选一（PNG / JPEG），不无条件转 JPEG**。
   - 含透明通道（alpha）→ **`image/png`**（无损、保透明）；不含 → **`image/jpeg`**（`IMAGE_JPEG_QUALITY=85`）。
   - 理由（含 iOS 兼容）：JPEG/PNG 两端均可**原生**编码（Android `Bitmap.compress`、iOS `UIImage.jpegData/pngData`），
     无需第三方库；WebP 体积更小但 **iOS 编码需额外库**，先不选、留作后续优化。无条件转 JPEG 会毁掉截图/贴纸的透明底。
   - 两端统一预处理（跨端一致关键）：上传前按 `IMAGE_MAX_DIMENSION=2048` 降采样；把 **EXIF 旋转烘焙进像素**并清除 orientation
     （否则 iOS/Android 对 EXIF 方向处理差异会导致显示旋转不一致）。
   - `presign` 用规范化后文件的真实 `contentType`（`image/png` 或 `image/jpeg`）+ `declaredSizeBytes`。
6. **上传进度：只做两态**（上传中 / 完成 / 失败重试），不做百分比。细粒度进度其实可拿到
   （Android 用 OkHttp 计数 `RequestBody`、iOS 用 `URLSession` delegate），但图片经降采样后秒级传完、百分比会一闪而过，
   不值得；两态也与 `ImageBlock.uploadState` 天然对齐。大文件（PDF/音频）或弱网场景再补百分比。

## 7. 错误与边界

| 场景 | 处理 |
|---|---|
| 上传失败（网络/`40004`/`40005`） | ImageBlock=FAILED，显示重试；不阻塞其它图片与文字编辑。 |
| 保存时 attach 失败（`40902` 未就绪） | 说明该图未 confirm/上传未完成——保存正文成功，attach 下次保存重试。 |
| 数量超限 `40903` | 插入前用 `attachmentCount` 拦截；仍触发则提示并回滚该 block。 |
| 笔记在回收站 `40905` | 只读态本就不可编辑/挂附件，不触发。 |
| downloadUrl 过期 | 打开笔记 `GET attachments` 现取；Coil 加载失败时可重列一次。 |
| 删除图片 | 从 body 移除 block；保存对账时 `DELETE attachment`（若已挂载）。 |
| 离线 | 本地 path 仍可预览；fileId 为空，联网后再传/挂（可后续接入 note-offline-sync）。 |

## 8. 落地步骤（建议顺序）

1. `AttachmentsApi` + DTO + `NetworkModule.attachmentsApi`。
2. `AttachmentsRepository` + 实现（attach/list/detach）+ DI 绑定。
3. `ImageBlock` 加 `fileId/uploadState` + `NoteEditorState.updateImage` + 序列化加 `fileId`。
4. `CreateViewModel.uploadImage`（复用 FilesRepository）+ saveNow 后的附件对账。
5. `CreateScreen`：插入后触发上传；渲染 path/URL 兜底 + 上传态角标 + 重试。
6. `loadNote`：`GET attachments` 建 fileId→URL 映射供渲染。
7. 联调 + 单测（对账逻辑、序列化含 fileId、三态映射）。
