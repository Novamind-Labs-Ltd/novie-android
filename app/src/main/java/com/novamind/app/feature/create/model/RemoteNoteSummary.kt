package com.novamind.app.feature.create.model

/**
 * 云端笔记列表项领域模型（对应后端 NoteListItem，纯 Kotlin，无框架/序列化类型）。
 *
 * 与 [RemoteNote] 区分：这是列表 / 首页 feed 用的**轻量投影**，**不含正文 content**、
 * 也不含 rev/schemaVersion；要正文用 GET /notes/{id}。时间保持原始 ISO-8601 文本，
 * 由上层按需解析。
 */
data class RemoteNoteSummary(
    val id: String,
    val title: String?,
    /** 纯文本摘要（后端 preview：正文首行、上限约 30-50 字）。 */
    val preview: String?,
    val borderColorHex: String?,
    val createdAt: String?,   // ISO-8601
    val updatedAt: String?,   // ISO-8601
    /** 所属文件夹 id；null = 未归档。 */
    val folderId: String?,
    /** 是否在回收站（deletedAt != null 时为 true）。 */
    val trashed: Boolean,
    /** 移入回收站时间；null = 活跃。 */
    val deletedAt: String?,
    /** 从后端 thumbnails 中选取的首张缩略图预签名 URL；null = 无图或尚未生成。 */
    val thumbnailUrl: String? = null,
)

/**
 * 笔记列表分页结果（对应后端 NotePageView）：条目 + 下一页游标。
 * [nextCursor] 为 null 表示已到底；否则作为下一页的 cursor 传回。
 */
data class RemoteNotePage(
    val items: List<RemoteNoteSummary>,
    val nextCursor: String?,
)
