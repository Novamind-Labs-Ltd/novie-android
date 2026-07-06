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
    val borderColorHex: String?,
    val createdAt: String?,
    val updatedAt: String?,
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
