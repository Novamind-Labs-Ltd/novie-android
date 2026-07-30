package com.novamind.app.common.audio

import android.media.MediaRecorder

/** 录音文件封装格式；编码均使用 AAC。 */
enum class AudioRecordingFormat(
    val fileExtension: String,
    val mimeType: String,
    internal val outputFormat: Int,
) {
    /** 笔记录音：原始 AAC/ADTS，对应后端 audio/aac 白名单。 */
    AAC(
        fileExtension = "aac",
        mimeType = "audio/aac",
        outputFormat = MediaRecorder.OutputFormat.AAC_ADTS,
    ),

    /** Ask Novie：MPEG-4 容器内封装 AAC，上传时使用标准 MIME audio/mp4。 */
    M4A(
        fileExtension = "m4a",
        mimeType = "audio/mp4",
        outputFormat = MediaRecorder.OutputFormat.MPEG_4,
    ),
}
