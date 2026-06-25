package com.novamind.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 上传 / 完整性状态（以字符串入库，避免枚举改动带来的迁移负担）。
 * - 分片：PENDING → UPLOADING → UPLOADED / FAILED
 * - 录音：LOCAL_ONLY → UPLOADING → VERIFIED / FAILED
 */
enum class UploadStatus { LOCAL_ONLY, PENDING, UPLOADING, UPLOADED, FAILED, VERIFIED }

/**
 * 录音：归属某个笔记，由多个有序分片组成。
 * [sha256] 为整体完整性（各分片哈希按 index 顺序再求一次 SHA-256，见 FileIntegrity）。
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
    val totalBytes: Long,
    val segmentCount: Int,
    val sha256: String,                  // 整体完整性（hash-of-hashes）
    val uploadStatus: String = UploadStatus.LOCAL_ONLY.name,
    val remoteUrl: String? = null,       // 后端 finalize 后返回
)

/**
 * 录音分片：内部存储中的单个 m4a 文件，[index] 决定顺序，[sha256] 为该分片完整性。
 */
@Entity(
    tableName = "recording_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recordingId"),
        Index(value = ["recordingId", "index"], unique = true),
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

/** 录音 + 其全部分片（查询用；使用前请按 index 排序）。 */
data class RecordingWithSegments(
    @Embedded val recording: RecordingEntity,
    @Relation(parentColumn = "id", entityColumn = "recordingId")
    val segments: List<RecordingSegmentEntity>,
) {
    /** 按顺序的分片列表。 */
    val orderedSegments: List<RecordingSegmentEntity> get() = segments.sortedBy { it.index }
}
