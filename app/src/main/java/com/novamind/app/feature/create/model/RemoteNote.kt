package com.novamind.app.feature.create.model

/**
 * 服务端笔记领域模型（对应后端 NoteView，纯 Kotlin，无框架/序列化类型）。
 *
 * 与本地 [Note] 区分：这是「云端笔记」的领域投影，带乐观锁版本 [rev] 与 [schemaVersion]。
 * [content] 以**原始 JSON 文本**承载（后端 content 为任意 jsonb），保持领域层不依赖序列化类型。
 */
data class RemoteNote(
    val id: String,
    val rev: Long,
    val schemaVersion: Int,
    val title: String?,
    val content: String,          // 原始 JSON 文本
    /** 列表/卡片用的纯文本摘要（content 第一行、≤50 字）；由前端提取后随保存传给服务端。 */
    val preview: String?,
    val borderColorHex: String?,
    val createdAt: String?,
    val updatedAt: String?,
    /** 所属文件夹 id；null = 未归档。 */
    val folderId: String? = null,
    /** 所属文件夹名（后端随 NoteView 返回）；null = 未归档或文件夹无名。 */
    val folderName: String? = null,
    /** 详情接口随笔记返回的图片资源；按 fileId 与正文图片块关联。 */
    val images: List<RemoteNoteImage> = emptyList(),
)

data class RemoteNoteImage(
    val fileId: String,
    val thumbnailUrl: String?,
    val downloadUrl: String?,
)

/**
 * 全量更新的领域结果：
 * - [applied] = true：本次写入生效，[note] 为写入后的版本；
 * - [applied] = false：被更新/相等版本抢先，本次编辑未持久化，[note] 为当前胜出版本。
 *
 * 客户端必须以 [applied] 判断成败，不要用 rev 是否相等推断。
 */
data class UpdateNoteOutcome(
    val applied: Boolean,
    val note: RemoteNote?,
)
