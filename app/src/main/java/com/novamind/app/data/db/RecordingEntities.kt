package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 上传 / 完整性状态（以字符串入库，避免枚举改动带来的迁移负担）。
 * 录音：LOCAL_ONLY → UPLOADING → UPLOADED / VERIFIED / FAILED。
 */
enum class UploadStatus { LOCAL_ONLY, PENDING, UPLOADING, UPLOADED, FAILED, VERIFIED }

/**
 * 录音：归属某个笔记，**单文件（不分片）**。
 *
 * [path] 为内部存储绝对路径，[sha256] 为整段文件完整性（confirm 后可与后端校验对齐）。
 * [fileId] 为后端 files 服务在 confirm 后返回的标识，用于把录音挂到笔记 attachments。
 */
@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class RecordingEntity(
    @PrimaryKey val id: String,          // UUID（建议复用 AudioRecorder.sessionId）
    val noteId: String,
    val createdAt: Long,
    val durationMs: Long,
    val path: String,                    // 内部存储绝对路径（单文件 .m4a；兼容历史 .aac）
    val bytes: Long,                     // 文件字节数
    val sha256: String,                  // 整段文件完整性
    val uploadStatus: String = UploadStatus.LOCAL_ONLY.name,
    val fileId: String? = null,          // 后端 files fileId（confirm 后）
    val remoteUrl: String? = null,       // 后端返回的可用地址（可空）
)
