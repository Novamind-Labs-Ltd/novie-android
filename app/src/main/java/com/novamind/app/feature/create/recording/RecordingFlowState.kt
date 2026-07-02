package com.novamind.app.feature.create.recording

/** 一段已落盘的录音文件（状态机在「文件生成」后携带的载荷）。 */
data class RecordedFile(
    /** 内部存储绝对路径。 */
    val path: String,
    val durationSeconds: Int,
)

/**
 * 录音全流程状态（Create 模块）：开始录音 → 生成文件 → 上传云端。
 *
 * ```
 *            start            stopAndUpload         (service 回写 result)
 *  Idle ───────────► Recording ───────────► Finalizing ───────────► FileReady
 *   ▲                │ pause/resume                                     │ (自动)
 *   │ discard        ▼                                                  ▼
 *   ◄─────────── (cancelled)                UploadFailed ◄────────── Uploading
 *   ▲                                            │   retryUpload        │
 *   └──────────── discard（删除文件）◄───────────┘                      ▼
 *                                                                    Uploaded（终态）
 * ```
 *
 * 说明：
 * - [Recording] 内含暂停/计时/振幅，均来自前台服务的快照（锁屏/后台不中断）；
 * - [Finalizing]：停止指令已下发，等待服务完成分片落盘与哈希（文件生成中）；
 * - [FileReady] 是瞬态：文件生成即自动进入 [Uploading]，保留此态便于观察与埋点；
 * - 时长不足 / 无声等**产品判定**不在状态机内，由 UI 层（VoiceRecordingBar）消费
 *   结果时先行拦截——状态机只负责「录制 → 文件 → 上传」的技术生命周期。
 */
sealed interface RecordingFlowState {

    /** 未开始 / 已结束回收。 */
    data object Idle : RecordingFlowState

    /** 录音中（含暂停）。 */
    data class Recording(
        val paused: Boolean,
        val elapsedSeconds: Int,
        /** 最近一次最大振幅（0..32767），驱动波形。 */
        val amplitude: Int,
    ) : RecordingFlowState

    /** 停止指令已下发，等待录音文件生成（分片落盘 + 完整性哈希）。 */
    data object Finalizing : RecordingFlowState

    /** 文件已生成（瞬态：随即自动开始上传）。 */
    data class FileReady(val file: RecordedFile) : RecordingFlowState

    /** 上传云端中。 */
    data class Uploading(val file: RecordedFile) : RecordingFlowState

    /** 上传完成（终态）。[remoteUrl] 为后端 finalize 返回的地址，可能为空。 */
    data class Uploaded(val file: RecordedFile, val remoteUrl: String?) : RecordingFlowState

    /** 上传失败：文件保留在本地，可 retryUpload 重传或 discard 丢弃。 */
    data class UploadFailed(val file: RecordedFile, val error: Throwable?) : RecordingFlowState
}
