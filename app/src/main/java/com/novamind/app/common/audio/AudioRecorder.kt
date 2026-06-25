package com.novamind.app.common.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.DebugLog
import java.io.File
import java.util.UUID

/**
 * 基于 [MediaRecorder] 的录音器：录制为 m4a（AAC）写入应用内部存储。
 * 支持开始 / 暂停 / 继续 / 停止 / 取消（暂停继续需 API 24+，本应用 minSdk 26 满足）。
 *
 * 分片：单个文件超过 [SEGMENT_BYTES] 时无缝滚动到下一片（[MediaRecorder.setNextOutputFile]），
 * 同一次录音产生 voice_<uuid>_seg1.m4a / _seg2.m4a … 多个文件。分片大小见 [SEGMENT_BYTES]。
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var sessionId: String = ""
    private val segments = mutableListOf<File>()
    private var nextIndex = 1

    /** 首个分片路径（停止后有效）；保持与单文件时代的兼容语义。 */
    val outputPath: String? get() = segments.firstOrNull()?.absolutePath

    /** 本次录音全部分片的绝对路径（按顺序）。 */
    val segmentPaths: List<String> get() = segments.map { it.absolutePath }

    /** 开始录音。成功返回 true。 */
    fun start(): Boolean = try {
        File(context.filesDir, AUDIO_DIR).mkdirs()
        sessionId = UUID.randomUUID().toString()
        segments.clear()
        nextIndex = 1
        val first = newSegmentFile()

        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(first.absolutePath)
            // 达到/接近上限时滚动到下一片
            setMaxFileSize(SEGMENT_BYTES)
            setOnInfoListener { mr, what, _ ->
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING ->
                        runCatching { mr.setNextOutputFile(newSegmentFile()) }
                            .onFailure { DebugLog.w(TAG, "setNextOutputFile failed: ${it.message}") }
                    MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED ->
                        DebugLog.i(TAG, "segment rolled -> ${segments.lastOrNull()?.name}")
                }
            }
            prepare()
            start()
        }
        recorder = rec
        true
    } catch (e: Exception) {
        DebugLog.w(TAG, "start failed: ${e.message}")
        releaseQuietly()
        deleteSegments()
        false
    }

    /** 自上次调用以来的最大振幅（0..32767）；未录音返回 0。 */
    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun pause() {
        runCatching { recorder?.pause() }
    }

    fun resume() {
        runCatching { recorder?.resume() }
    }

    /** 停止并返回首个分片路径；失败返回 null。 */
    fun stop(): String? {
        return try {
            recorder?.stop()
            releaseQuietly()
            outputPath
        } catch (_: Exception) {
            releaseQuietly()
            deleteSegments()
            null
        }
    }

    /** 取消录音并删除全部分片。 */
    fun cancel() {
        runCatching { recorder?.stop() }
        releaseQuietly()
        deleteSegments()
    }

    /** 生成并登记下一个分片文件。 */
    private fun newSegmentFile(): File {
        val f = File(context.filesDir, "$AUDIO_DIR/voice_${sessionId}_seg${nextIndex++}.m4a")
        segments.add(f)
        return f
    }

    private fun deleteSegments() {
        segments.forEach { runCatching { it.delete() } }
        segments.clear()
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
    }

    private companion object {
        const val AUDIO_DIR = "note_audio"
        const val TAG = "AudioRecorder"

        /** 单个录音分片的大小上限（字节）。集中配置见 [AppConfig.Media.AUDIO_SEGMENT_BYTES]。 */
        const val SEGMENT_BYTES = AppConfig.Media.AUDIO_SEGMENT_BYTES
    }
}
