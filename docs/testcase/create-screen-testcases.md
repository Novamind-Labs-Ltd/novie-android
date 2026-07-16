# CreateScreen 测试用例

被测对象：`com.novamind.app.feature.create.CreateScreen` 及其宿主 `CreateRoute`（新建 / 编辑 / 回收站查看笔记页）。

## 0. 测试范围与策略

**分层**：`CreateScreen` 是无状态 Composable（`uiState` 注入、`onEvent` + 回调向上抛、`recordingUploaded`/`transcriptionReady` 两条一次性 Flow）。`CreateRoute` 负责一次性事件消费（导航、`saveError`/`recordingUploaded` Toast、离线 `NoNetworkView`）、生命周期落盘（`ON_STOP`/`onDispose` → `VM.flush()`，只读态除外）。用例标注层级：

- **[UI]** 纯 Composable 单测（Compose UI Test）。
- **[VM]** ViewModel 行为（含仓库 mock）。
- **[INT]** 集成 / 端到端（宿主 + VM + WorkManager + 系统能力）。

**断言方式**：渲染断言（可见性/文案/只读/颜色）、事件断言（捕获 `CreateEvent` / 回调及参数）、一次性事件（向 Flow 发射后断言副作用）。

**优先级**：

- **P0 阻断级**：数据正确性与主干（内容派发、保存/自动保存/去重/防重复建、保存失败提示、返回落盘、删除、只读强制、录音/转写只读与结果、附件插入上传、离线不丢）。任一失败不可发版。
- **P1 重要**：完整功能（选择器、撤销重做、计数、工具栏、附件上限/类型、预览、上传进度、恢复、分享、加载遮罩、全屏联动、元信息、自动聚焦、错误分支细节、a11y 主干）。
- **P2 边界/增强**：边界值、组合态、动画防闪、文案优先级、幂等、输入法计数、显示适配、并发、性能、隐私。

**关键常量**（`AppConfig`）：`Editor.MAX_INPUT_CHARS`(正文上限)、`TITLE_MAX_CHARS`(标题上限=50)、`TITLE_LIMIT_TOAST_INTERVAL_MS`(标题超限 Toast 节流)、`COUNT_DISPLAY_THRESHOLD`、`MAX_HISTORY`、`AUTO_SAVE_DELAY_MS`、`SAVE_MAX_INTERVAL_MS`、`Media.MAX_ATTACHMENTS`、`MAX_IMAGE_PICK`、`MAX_UPLOAD_CONCURRENCY`、`MAX_DOCUMENT_SIZE`(16MB)、`DOCUMENT_MIME_TYPES`。
**入参**：`autoFocusBody`(=新建且非只读)、`isEditing`(=打开已有)、`maxImages`、`readOnly`、`forceToolbarVisible`、`attachmentUrls`。

---

## 1. 编辑与内容派发

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-EDIT-01 | P1 | UI | `title=""` | 进入 | 显示占位符「New note」；有内容不显示 |
| TC-EDIT-02 | P0 | UI | 新建、非只读 | 标题输入「Hello」 | 派发 `TitleChanged("Hello")` |
| TC-EDIT-03 | P0 | UI | 非只读 | 正文输入文本 | 派发 `ContentChanged(documentJson)` |
| TC-EDIT-05 | P0 | UI | 正文含图 | 插入/删除图片致结构变化 | 派发 `ContentChanged`，body JSON 增/删对应块 |
| TC-EDIT-04 | P0 | UI | 录音中 / 转写中 / 只读 任一 | 尝试编辑标题+正文 | 均只读、不弹键盘、不接收输入、不派发事件 |

## 2. 字数限制与计数

> 标题与正文是**两条独立**限制：标题 ≤ `TITLE_MAX_CHARS`(50)，正文 ≤ `MAX_INPUT_CHARS`；**标题不计入正文总字数**，右下角计数只反映正文。

**标题（独立上限 50，`TITLE_MAX_CHARS`）**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-LIMIT-01 | P0 | UI | 标题 < 50 | 标题输入 | 正常接收，派发 `TitleChanged`（不受正文长度影响） |
| TC-LIMIT-02 | P0 | UI | 标题 == 50 | 继续增字（增长型） | 拒绝、不派发；弹英文 Toast「Title cannot exceed 50 characters」 |
| TC-LIMIT-02b | P0 | UI | 标题 == 50 | 删字（新长 ≤ 原长） | 允许，派发 `TitleChanged` |
| TC-LIMIT-07 | P1 | UI | 标题达 50 | 继续按键（被拒绝） | 光标与文本保持不动（`TextFieldValue` 受控，拒绝时不更新） |
| TC-LIMIT-08 | P2 | UI | 标题达 50 | 连续快速按键 | Toast 按 `TITLE_LIMIT_TOAST_INTERVAL_MS` 节流，不重复刷屏 |
| TC-LIMIT-09 | P2 | UI | 标题 < 50 | 粘贴使总长 > 50 | 整段增长被拒 + Toast；文本/光标不变（不截断已有内容） |
| TC-LIMIT-10 | P1 | UI | 标题有字、正文有内容 | 观察右下角计数 | 计数只等于正文字数，**不含标题**；标题增删不影响正文可输入上限 |

**正文（独立上限 `MAX_INPUT_CHARS`）与计数**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-LIMIT-06 | P0 | UI | 正文接近上限 | 正文继续输入 | 受 `bodyCharLimit = MAX_INPUT_CHARS` 限制（不再扣减标题长度） |
| TC-LIMIT-03 | P1 | UI | 正文字数 < 阈值 | 观察右下角 | 显示实际正文字数 |
| TC-LIMIT-03b | P1 | UI | 正文字数 ≥ 阈值 | 观察 | 显示「Remaining N」形式 |
| TC-LIMIT-04 | P1 | UI | 正文字数 ≥ 上限 | 观察 | 计数用错误色（红） |
| TC-LIMIT-05 | P1 | UI | 录音中 / 只读 | 观察 | 计数不展示 |

## 3. 保存 · 自动保存 · 并发去重

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-SAVE-10 | P0 | VM | 新建有内容 | 停顿 > `AUTO_SAVE_DELAY_MS` | 防抖触发一次 `saveNow`（POST /notes） |
| TC-SAVE-11 | P0 | VM | 持续不停手编辑 | 超过 `SAVE_MAX_INTERVAL_MS` | 封顶流强制落盘一次 |
| TC-SAVE-12 | P0 | VM | 新建、快速连续编辑 | 防抖流+封顶流几乎同时触发 | `saveMutex` 串行化，**只创建 1 条笔记**（首个拿 id，后续走 PUT），无重复 POST |
| TC-SAVE-13 | P0 | VM | 已保存过、内容未变 | 再次触发保存 | `savedSnapshot` 相等 → 跳过，不重复 POST/PUT |
| TC-SAVE-14 | P0 | VM | 标题空且正文 `previewText` 空 | 触发保存 | 视为空笔记，不保存 |
| TC-SAVE-15 | P1 | VM | 已有笔记、`updateNote` 返回 `applied=false`（latest-wins 落后） | 保存 | 不弹错、同步服务端 rev，转写等后续以服务端为准 |
| TC-SAVE-16 | P1 | VM | 新建未保存 | 改边框色 | 先 `saveNow` 创建拿 id，再 PATCH border-color，rev 同步回 remoteRev |

## 4. 保存失败与错误提示

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-ERR-01 | P0 | INT | 保存时后端业务错误 | 触发保存 | `CreateRoute` 弹 `saveError` Toast（后端 message 或「Save failed (code)」）；笔记可编辑、内容不清空 |
| TC-ERR-02 | P0 | INT | 保存时网络错误 | 触发保存 | Toast「Network error, note not saved」；内容保留、可重试 |
| TC-ERR-03 | P1 | UI | `saveError` 连续发射 + 重组 | 观察 | 用 SharedFlow，一次性消费，不因重组重复弹 |
| TC-ERR-04 | P1 | INT | `setBorderColor` 失败 | 改色 | Toast「Failed to set colour」/「Network error, colour not saved」 |
| TC-ERR-05 | P1 | INT | 源录音上传业务/网络失败 | 上传 | Toast（「Audio upload failed」/「Network error, audio not uploaded」）；进度条复位 |
| TC-ERR-06 | P1 | UI | 图片上传失败 | 观察图片块 | 置 FAILED，可点重试再次 `onUploadImage`；部分成功部分失败各自状态正确 |
| TC-ERR-07 | P1 | VM | 转写 `FAILED` | 轮询到 | Toast「Transcription failed」并停止轮询 |
| TC-ERR-08 | P2 | VM | 转写列表接口 业务/网络错误 | 轮询 | 未确认 PROCESSING 前出错即终止补拉详情；已确认在处理才重试 |
| TC-ERR-09 | P2 | VM | 附件 attach/detach 失败 | 保存对账 | 仅记日志、不阻塞正文保存，下次保存重试（最终一致） |
| TC-ERR-10 | P1 | VM | `getNote` 返回 null / 空 body / 脏 JSON | 打开笔记 | 编辑器不崩，正文回退空/原样，打日志 |
| TC-ERR-11 | P2 | INT | 鉴权失效（`BizError.isAuthExpired`） | 保存/加载 | 触发重新登录流程（全局），不静默失败 |

## 5. 返回与保存时机

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-BACK-01 | P0 | UI | 编辑态 | 顶栏返回 / 系统返回 | 收键盘，派发 `SaveNote`（VM 落盘后 `navigateBack` → `onBack`） |
| TC-BACK-02 | P0 | UI | `readOnly=true` | 返回 | 收键盘后直接 `onBack()`，不派发 `SaveNote` |
| TC-BACK-03 | P0 | UI | 录音条展示中 | 系统返回 | 仅关录音条，不返回/不保存 |
| TC-BACK-04 | P1 | UI | AI 骨架(polish)进行中 | 系统返回 | 仅 `editor.clearPolish`，不返回 |
| TC-BACK-05 | P1 | UI | 预览 / BottomSheet / 分享 打开 | 系统返回 | 由各自层处理，不触发保存返回 |

## 6. 撤销 / 重做

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-UNDO-01 | P1 | UI | `canUndo=true` | 点 Undo | 派发 `UndoEdit` |
| TC-UNDO-02 | P2 | UI | `canUndo=false` | 观察 | Undo 禁用 |
| TC-REDO-01 | P1 | UI | `canRedo=true` | 点 Redo | 派发 `RedoEdit` |
| TC-REDO-02 | P2 | UI | `canRedo=false` | 观察 | Redo 禁用 |
| TC-UNDO-03 | P1 | VM | 有编辑历史 | Undo | 标题/正文**真的还原**到上一快照，`canRedo=true` |
| TC-UNDO-04 | P1 | VM | Undo 后 | 新编辑 | redo 栈清空，`canRedo=false` |
| TC-UNDO-05 | P2 | VM | 连续编辑 > `MAX_HISTORY` | 观察栈 | 超出丢最旧快照，不无限增长 |
| TC-UNDO-06 | P2 | UI | 键盘弹出中 | Undo/Redo | 原地更新，不收键盘 |

## 7. 标签选择器

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-TAG-01 | P1 | UI | 非只读 | 点标签入口 | 收键盘清焦点，派发 `ShowTagPicker` |
| TC-TAG-02 | P1 | UI | `showTagPicker=true` | 渲染 | 展示 `TagPickerSheet`，列表=`availableTags`，回显 `selectedTags` |
| TC-TAG-03 | P1 | UI | 选择器打开 | 勾选/取消 | 派发 `TagToggled(tag)` |
| TC-TAG-04 | P1 | UI | 选择器打开 | 输入新名确认 | 派发 `NewTagCreated(name)` |
| TC-TAG-05 | P1 | UI | 选择器打开 | 关闭/遮罩 | 派发 `DismissTagPicker` |
| TC-TAG-06 | P2 | VM | 已选若干标签 | 新选一个 | 新选置于 `selectedTags` 最前；取消保持顺序；不重复 |

## 8. 文件夹选择器

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-FOL-01 | P1 | UI | 非只读 | 点文件夹入口 | 收键盘清焦点，派发 `ShowFolderPicker` |
| TC-FOL-02 | P1 | UI | `showFolderPicker=true` | 渲染 | 展示 `FolderPickerSheet`，列表=`availableFolders` |
| TC-FOL-03 | P1 | UI | 选择器打开 | 选文件夹 | 派发 `FolderSelected(folder)` |
| TC-FOL-04 | P1 | UI | 选择器打开 | 新建文件夹 | 派发 `NewFolderCreated(name)` |
| TC-FOL-05 | P1 | UI | 选择器打开 | 关闭/遮罩 | 派发 `DismissFolderPicker` |
| TC-FOL-06 | P2 | UI | 选择器打开 | 选「Unfiled」 | 派发 `FolderSelected(null)` |
| TC-FOL-07 | P2 | VM | 新建文件夹 | 服务端返回 | 乐观选中→用返回 id 校正选中项，刷新列表 |

## 9. 边框色

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-COL-01 | P1 | UI | 非只读，更多→改色 | 触发 | 清焦点收键盘，派发 `ShowColorPicker` |
| TC-COL-02 | P1 | UI | `showColorPicker=true` | 渲染 | 展示 `BorderColorSheet`，回显 `borderColor` |
| TC-COL-03 | P1 | UI | 打开 | 选颜色 | 派发 `BorderColorSelected(color)` |
| TC-COL-04 | P2 | UI | 打开 | 选「默认」 | 派发 `BorderColorSelected(null)` |
| TC-COL-05 | P1 | UI | 打开 | 关闭/遮罩 | 派发 `DismissColorPicker` |

## 10. 「更多(···)」可用性

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-MORE-01 | P1 | UI | `isEditing=true`（可空） | 观察 | 「更多」可用 |
| TC-MORE-02 | P1 | UI | `isEditing=false` 且标题正文空 | 观察 | 「更多」禁用 |
| TC-MORE-03 | P2 | UI | `isEditing=false` 有内容 | 观察 | 「更多」可用 |

## 11. 附件（图片 / 拍照 / 文档）

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-ATT-01 | P1 | UI | 工具栏可见、未满 | 点插图 | 收键盘，展示 `AttachmentSheet` |
| TC-ATT-02 | P1 | UI | 已达 `MAX_ATTACHMENTS` | 点插图 | Toast「You can add up to N attachments」，不展开 |
| TC-ATT-03 | P0 | INT | 剩余名额 ≤ 1 | 选 1 张图 | 单选选择器；导入插入图片块并 `onUploadImage(path, contentType)` |
| TC-ATT-04 | P0 | INT | 剩余名额 ≥ 2 | 选多张 | 多选（maxItems=剩余名额）；按名额截断后逐张插入并上传 |
| TC-ATT-11 | P0 | UI | 上传返回 fileId | 完成 | 图片块回填 fileId、UPLOADED，`emitContent` 使 body 带 fileId（供对账挂附件） |
| TC-ATT-05 | P1 | INT | 相机权限具备 | 拍照成功 | 落地、读宽高后插入并上传 |
| TC-ATT-05b | P1 | INT | 拍照 | 取消/失败 | 不插入任何块 |
| TC-ATT-06 | P1 | INT | 选 `.md` | 选择 | 非空→Markdown 块；空→兜底文件块 |
| TC-ATT-07 | P1 | INT | 选 `.pdf` | 选择 | 插入 PDF 块 |
| TC-ATT-08 | P1 | INT | 文件 > 16MB | 选择 | Toast「File exceeds 16MB, skipped」，不插入 |
| TC-ATT-12 | P1 | INT | 选其他类型 | 选择 | 作为文件块插入 |
| TC-ATT-16 | P2 | INT | 导入损坏/不支持图（HEIC/webp 等） | 选择 | 优雅失败、不插坏块、不崩 |
| TC-ATT-17 | P2 | VM | 图/PDF/Markdown 混合 | 计数 | 附件上限按三类合计（`attachmentCount`） |
| TC-ATT-13 | P1 | UI | 插入图片后 | 观察焦点 | 焦点落到图后文本块（消费 `pendingFocus`） |
| TC-ATT-10 | P1 | UI | `attachmentUrls` 非空 | 打开含图笔记 | 本地路径失效时以 `remoteUrl` 渲染 |

## 12. 图片预览

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-PREV-01 | P1 | UI | 正文含图 | 点某图 | 收键盘，全屏预览打开到对应下标 |
| TC-PREV-02 | P1 | UI | 预览打开 | 删除当前图 | 按序号删对应块并 `emitContent` |
| TC-PREV-03 | P1 | UI | 预览打开 | 返回 | 关闭预览 |
| TC-PREV-04 | P2 | UI | 退出动画期间 | 快速返回 | 用上次快照续渲染，不闪白 |

## 13. 录音与源录音上传

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-REC-01 | P0 | INT | 录音权限已授予 | 点「Voice」 | 收键盘清焦点，展示 `VoiceRecordingBar` |
| TC-REC-02 | P1 | INT | 未授权 | 点「Voice」 | 申请权限；授予展示录音条，拒绝不展示 |
| TC-REC-03 | P0 | UI | 录音条展示 | 观察 | 标题正文只读，工具栏与计数隐藏 |
| TC-REC-04 | P0 | UI | 录音完成 | 确认 | 回调 `onUploadRecording(path, durationSeconds*1000L)`（秒→毫秒） |
| TC-REC-05 | P1 | UI | `isUploadingAudio=true` | 观察 | 进度条展示，百分比随 `audioUploadProgress` 更新 |
| TC-REC-06 | P1 | UI | 进度条展示 | 点 × | 回调 `onCancelUploadRecording()` |
| TC-REC-07 | P0 | UI | 录音条展示 | 向 `recordingUploaded` 发射 | 录音条关闭 |
| TC-REC-09 | P1 | INT | 上传成功 | 完成 | `CreateRoute` 弹「Recording uploaded」Toast |
| TC-REC-10 | P2 | VM | 新一次录音 | 发起 | 中断上一条 upload/transcription job（互斥） |
| TC-REC-11 | P2 | INT | 录音时长 0 / 极短 / 超长 | 确认 | 边界处理正确，不产坏文件 |

## 14. 转写

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-TRAN-01 | P0 | UI | `isTranscribing=true` | 观察 | 顶栏「Transcribing…」；正文只读；带文案 LoadingOverlay |
| TC-TRAN-02 | P1 | UI | `isLoading=true` | 打开笔记 | 普通 Loading（无文案） |
| TC-TRAN-03 | P0 | UI | 正文已首次加载 | 向 `transcriptionReady` 发射 | `appendHtml` → `emitContent`（派发 `ContentChanged`）→ `ack.complete()` |
| TC-TRAN-05 | P0 | UI | 进页即 READY（body 未加载完） | 发射 | 先等 `bodyLoaded` 再追加，避免被回填覆盖 |
| TC-TRAN-07 | P2 | INT | 页面未订阅 `transcriptionReady` | 发射后超时 | 不追加/不 ack，VM 不保存/不 consume，重进可重试 |
| TC-TRAN-08 | P2 | VM | 转写结果为空/全空白段 | READY | 不追加空内容，正常结束 |

## 15. 格式工具栏

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-BAR-01 | P1 | UI | 键盘可见 且 非录音 且 非只读 | 观察 | 工具栏悬浮键盘上方 |
| TC-BAR-01b | P1 | UI | 上述任一不满足 | 观察 | 工具栏隐藏 |
| TC-BAR-02 | P1 | UI | 有选区 | 点 B / I | 切换加粗/斜体，激活态回显 |
| TC-BAR-03 | P1 | UI | 工具栏可见 | 点有序/无序列表 | 插入列表标记并 `emitContent` |
| TC-BAR-04 | P2 | UI | 工具栏可见 | 收起键盘 | 键盘隐藏 |
| TC-BAR-06 | P1 | UI | 工具栏可见 | 点魔法(Magic) | `editor.startPolish()` 进入骨架态 |
| TC-BAR-05 | P2 | UI | `forceToolbarVisible=true` | 观察 | 强制显示（Preview 用） |

## 16. 加载遮罩 / 分享 / 全屏联动 / 自动聚焦

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-FS-01 | P1 | UI | — | 打开预览/录音条/分享 任一 | 回调 `onFullscreenChange(true)`；全部关闭回调 `false` |
| TC-FS-02 | P1 | UI | 非只读 | 点分享 | 清焦点收键盘，右侧推入 `ShareAccessScreen`，可返回关闭 |
| TC-FS-03 | P1 | UI | 全屏层打开时 | 离开页面 | `onDispose` 回调 `onFullscreenChange(false)` |
| TC-FS-04 | P2 | UI | 预览+分享相继开关 | 组合切换 | `onFullscreenChange` 按「任一打开」正确聚合 |
| TC-FOCUS-01 | P1 | UI | `autoFocusBody=true` | 进入 | 短延时后正文获焦弹键盘 |
| TC-FOCUS-02 | P2 | UI | `autoFocusBody=false` | 进入 | 不自动弹键盘 |

## 17. 只读（回收站）

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-RO-01 | P0 | UI | `readOnly=true` | 进入 | 标题/正文不可编辑；无工具栏；无计数 |
| TC-RO-02 | P0 | UI | 只读 | 点 Restore | 回调 `onRestore()` |
| TC-RO-03 | P0 | UI | 只读 | Delete→确认 | 回调 `onDeleteForever()`；不派发 `DeleteNote`/`PermanentDeleteNote` |
| TC-RO-04 | P0 | UI | 只读 | 观察顶栏 | 展示 Restore/Delete，不展示编辑态操作 |
| TC-RO-05 | P1 | INT | 只读 | 退后台/离开 | **不** `flush`（不改动已删笔记） |

## 18. 删除

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-DEL-01 | P0 | UI | 非只读 | 更多→删除→确认 | 派发 `DeleteNote`（软删入回收站） |
| TC-DEL-03 | P0 | UI | 删除确认弹出 | 取消/遮罩 | 关闭确认，不派发删除 |

## 19. 网络与离线

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-NET-01 | P0 | INT | 打开已有笔记时离线 | 进入 | 铺满 `NoNetworkView`，不拉接口 |
| TC-NET-02 | P1 | INT | `NoNetworkView` 展示 | 联网后点重试 | 隐藏并 `loadNote` 加载 |
| TC-NET-03 | P0 | INT | **新建**笔记离线（新建不判网） | 编辑后触发保存 | 保存网络错误 Toast，内容不丢、可重试 |
| TC-NET-04 | P2 | INT | 慢网/超时 | 保存/上传 | 合理超时与错误提示，UI 不卡死 |
| TC-NET-05 | P2 | INT | 上传中途断网 | 继续 | 失败提示/可重试；分片上传具备续传能力 |

## 20. 应用生命周期 / 进程边界

> 已实现：`CreateRoute` 在 `ON_STOP` 与 `onDispose` 调 `VM.flush()`（只读态除外）。

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-LIFE-01 | P0 | INT | 编辑态、有未保存改动 | 切后台（`ON_STOP`） | `flush()` → `saveNow` 立即落盘，防抖窗口内改动不丢 |
| TC-LIFE-02 | P0 | INT | 编辑态 | 导航离开（`onDispose`） | `flush()` 落盘，再进入内容一致 |
| TC-LIFE-03 | P0 | INT | 已保存笔记 | 杀进程→重启→重开 | `loadNote` 拉服务端最新，标题/正文/附件/边框色一致 |
| TC-LIFE-04 | P0 | INT | 编辑后立即切后台并**在 flush 网络请求返回前**杀进程 | 杀 | 残留丢数据窗口：`flush` 为 `viewModelScope.launch` fire-and-forget，进程被杀会中断在途请求——验证该窗口影响面，评估是否需本地草稿兜底 |
| TC-LIFE-05 | P1 | INT | `isUploadingAudio=true` | 上传中杀→重启 | 断点续传/WorkManager 续传；未 complete 分片不挂笔记；无假进度 |
| TC-LIFE-06 | P1 | INT | `isTranscribing=true` | 杀/退后台→重进 | 按服务端状态恢复：PROCESSING 续轮询 / READY 追加并保存后 consume |
| TC-LIFE-07 | P1 | INT | 本地 DIRTY/LOCAL 笔记 | 杀→联网重启 | `NoteSyncWorker` push 到服务端，状态转 SYNCED |
| TC-LIFE-08 | P1 | INT | 编辑态 | 切后台再回前台 | 状态一致；不重复弹键盘/不重复保存上传；全屏层与底栏正确 |
| TC-LIFE-09 | P2 | INT | 打开系统选择器/相机 | 期间进后台再回 | `pendingCapturePath` 等瞬态可恢复，正确处理结果或安全丢弃 |
| TC-LIFE-10 | P2 | INT | 录音进行中 | 来电/切后台打断 | 录音按策略中断，不产坏文件，UI 回正 |
| TC-LIFE-11 | P2 | INT | 图文混排 | 进程死后重建 | `NoteEditorState`（`remember`，非 saveable）依 `uiState.body` 重解析恢复，不崩 |
| TC-LIFE-12 | P2 | INT | 编辑态 | Activity recreate（进程存活） | 依 `uiState` 恢复标题/正文/选择/弹窗态一致 |

## 21. 可访问性 (a11y)

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-A11Y-01 | P1 | UI | 开 TalkBack | 遍历图标按钮 | Undo/Redo/更多/分享/返回、删除 ×、插图、关闭上传 ×、图片项均有 `contentDescription` |
| TC-A11Y-02 | P1 | UI | 开 TalkBack | 进入 loading/transcribing/saving | 状态变化被播报（非纯视觉） |
| TC-A11Y-03 | P2 | UI | — | 量测触控目标 | 图标按钮 ≥ 48dp |
| TC-A11Y-04 | P2 | UI | 达字数上限 | 观察 | 除红色外有文字/语义提示（不只靠颜色区分） |
| TC-A11Y-05 | P2 | UI | 键盘导航/焦点 | 遍历 | 焦点顺序合理，可达关键操作 |

## 22. 输入法与文本计数口径

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-IME-01 | P2 | UI | 中文/日文拼音 | 组合(composing)输入中 | 上限拦截不误伤组合串；提交后计数正确 |
| TC-IME-02 | P2 | UI | — | 输入 emoji / CJK / 组合字符 | 计数口径明确一致（字符/代码点），上限判定不出错 |
| TC-IME-03 | P2 | UI | — | 粘贴超大 / 富文本 | 按上限规则处理，不崩、不卡 |
| TC-IME-04 | P2 | UI | 硬件键盘/自动更正 | 输入 | 正常派发内容变更 |

## 23. 显示适配（主题 / 字体 / 超长 / RTL / 多窗）

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-DISP-01 | P1 | UI | 深色模式 | 进入 | 颜色（`DualColor`）与对比度正确，文本可读 |
| TC-DISP-02 | P1 | UI | 系统字体/显示放到最大 | 进入 | 标题/计数/工具栏/meta 行不溢出、可用 |
| TC-DISP-03 | P2 | UI | 超长标题 / 超长标签名 / 超长文件夹名 | 观察 | 换行或省略截断，布局不破 |
| TC-DISP-04 | P2 | UI | RTL 语言 | 进入 | 布局镜像正确（若支持） |
| TC-DISP-05 | P2 | UI | 分屏 / 折叠屏 / 小屏 | 进入 | 关键操作可达，无遮挡 |

## 24. 状态组合与互斥

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-COMB-01 | P1 | UI | 任一 BottomSheet 打开 | 试图开另一个 | 同时只存在一个选择器 |
| TC-COMB-02 | P2 | UI | `isSaving=true` | 继续输入 | 保存中仍可编辑（不阻塞输入），时间标签「Saving…」 |
| TC-COMB-03 | P2 | UI | `isUploadingAudio` + `isTranscribing` | 观察 | 进度条与转写遮罩并存不冲突 |
| TC-TIME-03 | P2 | UI | `isTranscribing`+`isSaving` 同真 | 观察 | 时间标签「Transcribing…」（转写>保存>时间戳） |
| TC-TIME-04 | P2 | UI | 仅 `isSaving` | 观察 | 「Saving…」 |
| TC-TIME-01 | P1 | UI | 编辑已有、`updatedAt` 有值、非 saving/transcribing | 观察 | 显示智能时间戳 |
| TC-TIME-02 | P1 | UI | 新建 `updatedAt=null` | 观察 | 显示当前时间智能格式 |
| TC-META-07 | P1 | UI | `selectedFolder` 有值 | 观察 meta 行 | 显示文件夹名 |
| TC-META-08 | P1 | UI | `selectedTags` 非空 | 观察 meta 行 | 显示已选标签（最新在前） |

## 25. 性能

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-PERF-01 | P2 | INT | 超长笔记（大量文本/块） | 滚动 + 输入 | 无明显掉帧；LazyColumn 离屏块不解码图片/不渲 PDF |
| TC-PERF-02 | P2 | INT | 多图（接近上限） | 浏览/预览 | 内存平稳，无 OOM |
| TC-PERF-03 | P2 | VM | 持续快速输入 | 观察 | 自动保存不阻塞输入、不频繁刷 UI（进度节流） |
| TC-PERF-04 | P2 | INT | 一次多选达 `MAX_UPLOAD_CONCURRENCY`+ | 上传 | 超出排队，进度分别回填、无错乱 |

## 26. 安全 / 隐私

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-SEC-01 | P1 | INT | 只读（回收站） | 任意查看操作 | 全程无写请求、不 flush |
| TC-SEC-02 | P2 | INT | 附件签名 URL 过期（`expiresAt`） | 渲染 | 过期后能刷新/重取，不长期缓存失效 URL |
| TC-SEC-03 | P2 | VM | 转写文本含特殊字符/HTML | 追加 | 正确转义，不破坏文档结构/无注入 |

## 27. AI 润色骨架（Polish）

> 现状：点格式工具栏「Magic」→ `editor.startPolish()` 仅显示**扫光骨架占位**，退出**只有**系统返回 `clearPolish()`，**没有 AI 结果回填 / 超时 / 自动结束**。TC-POL-19~22 标注了「无终止出口」这一最佳实践缺口，待接后端润色能力后补齐。

**触发与覆盖范围**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-POL-01 | P1 | UI/VM | 聚焦文本块内选中一段 | 点 Magic | 仅该块 [min,max) 区间显示骨架（`polishTarget`）；光标折叠到选区末尾 `sel.max`，`polishAll=false` |
| TC-POL-02 | P1 | UI/VM | 光标无选区、笔记有文字 | 点 Magic | 全文骨架（`polishAll=true`），每个文本块都覆盖，`polishTarget=null` |
| TC-POL-03 | P1 | VM | 笔记无任何文字 | 点 Magic | `startPolish()` 返回 false，不进入骨架态，`isPolishing=false` |
| TC-POL-04 | P2 | UI | polishAll 生效 | 观察 | 仅文本块画骨架；图片/PDF/Markdown 块不受影响 |
| TC-POL-05 | P2 | UI | 选区在某块 | 观察 | 仅选区所属块画骨架，其它块不画 |

**骨架渲染（回归重点）**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-POL-06 | P1 | UI | 多行选区/全文 | 观察 | 骨架**一行一行**，不连成整块 |
| TC-POL-07 | P1 | UI | 多行骨架 | 观察 | 各行骨架条**等高**（上下各内缩 `LINE_GAP_DP/2`） |
| TC-POL-08 | P1 | UI | 整段全选 | 观察首/末行 | 首行顶部、末行底部文字**不透出** |
| TC-POL-09 | P2 | UI | 多行骨架 | 观察 | 行间留白一致（`LINE_GAP_DP`），圆角 `CORNER_RADIUS_DP` |
| TC-POL-10 | P2 | UI | 选区跨行 | 观察 | 首行从 start 起、末行到 end 止，中间整行宽；末行短则条也短 |
| TC-POL-11 | P1 | UI | 骨架态 | 观察 | 扫光渐变持续动画（`PolishBar` 基/亮色）；退出后停止 |
| TC-POL-12 | P2 | UI | 深色模式 | 观察 | 骨架色与对比度正常 |

**交互 / 退出 / 生命周期**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-POL-13 | P1 | UI | 骨架态 | 系统返回 | `clearPolish()`（`isPolishing=false`）；不触发保存返回、不 `onBack` |
| TC-POL-14 | P1 | UI | 骨架态 | 尝试编辑/滚动 | 明确预期：当前正文**非只读**（polish 不置只读）；确认行为符合设计，若需锁定输入属潜在缺口 |
| TC-POL-15 | P2 | UI | 骨架态 | 点其它工具栏动作 | 行为符合预期，不残留骨架、状态不冲突 |
| TC-POL-16 | P2 | UI | 已在骨架态 | 再次触发（有/无选区） | 无选区→保持 `polishAll`；有选区→切换为该选区 `polishTarget` |
| TC-POL-17 | P2 | INT | 骨架态 | 旋转/进程重建 | `polishTarget`/`polishAll` 在 `NoteEditorState`（`remember`，非 saveable）→ 重建后骨架态丢失；确认应重置、不残留 |
| TC-POL-18 | P2 | UI | 录音中/转写中 | 骨架态叠加 | 正文本就只读；骨架与只读态叠加不冲突，退出后状态正确 |

**终止 / 结果（占位现状 + 待接后端）**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-POL-19 | P1 | INT | 骨架态 | 除返回外的所有出口 | 现状仅「返回」可退出；验证不出现「永久骨架」无法退出的死角 |
| TC-POL-20 | P1 | INT | （待接后端）润色成功 | AI 返回 | 用润色文本替换 [start,end) 区间、清骨架并保存；loading→success 过渡 |
| TC-POL-21 | P1 | INT | （待接后端）润色失败/超时 | 错误/超时 | 提示错误、还原原文、清骨架，可重试 |
| TC-POL-22 | P2 | INT | 润色进行中 | 返回/退后台/杀进程 | 不落坏数据；重入状态一致，骨架不被当作内容持久化 |

**可访问性**

| ID | P | 层 | 前置 | 步骤 | 预期 |
|---|---|---|---|---|---|
| TC-POL-23 | P2 | UI | 开 TalkBack | 进入骨架态 | 有「正在润色」类可感知播报；骨架不吞焦点、可返回退出 |

---

## 附录 A · P0 冒烟清单（发版门禁）

TC-EDIT-02/03/04/05、TC-LIMIT-01/02/02b/06、TC-SAVE-10/11/12/13/14、TC-ERR-01/02、TC-BACK-01/02/03、TC-DEL-01/03、TC-RO-01/02/03/04、TC-REC-01/03/04/07、TC-TRAN-01/03/05、TC-ATT-03/04/11、TC-NET-01/03、TC-LIFE-01/02/03/04。

## 附录 B · 事件覆盖矩阵

| CreateEvent | 用例 |
|---|---|
| TitleChanged | TC-EDIT-02, TC-LIMIT-01/02/02b, TC-IME-* |
| ContentChanged | TC-EDIT-03/05, TC-BAR-03, TC-PREV-02, TC-TRAN-03, TC-ATT-11 |
| TagToggled / NewTagCreated | TC-TAG-03 / TC-TAG-04 |
| FolderSelected / NewFolderCreated | TC-FOL-03/06 / TC-FOL-04 |
| SaveNote | TC-BACK-01 |
| DeleteNote | TC-DEL-01 |
| PermanentDeleteNote | 说明：本页只读删除走 `onDeleteForever` 回调，不直接派发（宿主处理） |
| UndoEdit / RedoEdit | TC-UNDO-01/03 / TC-REDO-01 |
| Show/Dismiss TagPicker | TC-TAG-01 / TC-TAG-05 |
| Show/Dismiss FolderPicker | TC-FOL-01 / TC-FOL-05 |
| Show/Dismiss ColorPicker | TC-COL-01 / TC-COL-05 |
| BorderColorSelected | TC-COL-03/04 |

## 附录 C · uiState 字段覆盖矩阵

| 字段 | 用例 |
|---|---|
| editingNoteId / isEditing | TC-MORE-*, TC-TIME-01/02 |
| updatedAt | TC-TIME-01/02 |
| title / body | TC-EDIT-*, TC-LIMIT-*, TC-IME-* |
| selectedTags / selectedFolder / borderColor | TC-META-07/08, TC-COL-02 |
| availableTags / availableFolders | TC-TAG-02, TC-FOL-02 |
| showTagPicker / showFolderPicker / showColorPicker | TC-TAG-02, TC-FOL-02, TC-COL-02, TC-COMB-01 |
| canUndo / canRedo | TC-UNDO-01/02, TC-REDO-01/02 |
| isSaving | TC-TIME-04, TC-COMB-02 |
| isUploadingAudio / audioUploadProgress | TC-REC-05/06, TC-COMB-03 |
| isTranscribing | TC-TRAN-01, TC-EDIT-04, TC-TIME-03 |
| isLoading | TC-TRAN-02 |

## 附录 D · 自动化建议

- **[UI] 可直接自动化**（`createComposeRule`，注入 `uiState` + 捕获 `onEvent`）：第 1/2/5/6/7/8/9/10/12/15/16/17/18 节的渲染与事件类；一次性事件用测试 `MutableSharedFlow`（TC-REC-07、TC-TRAN-03）。
- **[VM] 单测**（mock 仓库）：第 3（保存并发/去重/竞态）、4（错误分支）、6（撤销栈）、14（转写轮询）、TC-FOL-07、TC-REC-10。第 3 节的**防重复建**是最高价值单测。
- **[INT] 集成/端到端**：附件系统选择器/相机/权限、录音、离线与 `NoNetworkView`、生命周期与进程死、`NoteSyncWorker`、性能。需把 `ActivityResultContracts`、权限判断、IME 可见性抽象为可注入依赖，或用 `forceToolbarVisible` 等入参旁路。
- **落地前补 `testTag`**：标题输入、正文编辑器、字数计数、格式工具栏及各按钮、顶栏各按钮、录音条、上传进度条与 ×、各 BottomSheet、预览层与删除、Restore/Delete、`NoNetworkView` 重试。
