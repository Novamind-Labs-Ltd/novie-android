package com.novamind.app.common.audio

import com.novamind.app.common.log.AppLog
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.novamind.app.common.config.AppConfig
import java.io.File
import java.util.UUID

/**
 * 基于 [MediaRecorder] 的录音器：**单文件、不分片**，录制为 M4A（MPEG-4 容器 + AAC）
 * 写入应用内部存储，MIME 对齐后端 `audio/mp4`。支持开始/暂停/继续/停止/取消
 * （暂停继续需 API 24+，本应用 minSdk 26 满足）。
 *
 * 编码参数集中在 [AppConfig.Media]：码率 [AppConfig.Media.AUDIO_BITRATE]、
 * 采样率 [AppConfig.Media.AUDIO_SAMPLE_RATE]、声道 [AppConfig.Media.AUDIO_CHANNELS]。
 * 达 [AppConfig.Media.MAX_RECORD_MS]（默认 30 分钟）自动停止，经 [onMaxDurationReached] 通知宿主收尾。
 *
 * @param onMaxDurationReached 达最大时长时回调（在 MediaRecorder 线程触发，宿主应切回自身线程收尾）。
 */
class AudioRecorder(
    private val context: Context,
    private val onMaxDurationReached: (() -> Unit)? = null,
) {

    private var recorder: MediaRecorder? = null
    private var sessionId: String = ""
    private var outputFile: File? = null

    /** 录音文件绝对路径（停止后有效）。 */
    val outputPath: String? get() = outputFile?.absolutePath

    /** 开始录音。成功返回 true。 */
    fun start(): Boolean = try {
        File(context.filesDir, AUDIO_DIR).mkdirs()
        sessionId = UUID.randomUUID().toString()
        val out = File(context.filesDir, "$AUDIO_DIR/voice_$sessionId.m4a")
        outputFile = out

        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // M4A 使用 MPEG-4 容器封装 AAC，上传 MIME 为 audio/mp4。
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(AppConfig.Media.AUDIO_BITRATE)
            setAudioSamplingRate(AppConfig.Media.AUDIO_SAMPLE_RATE)
            setAudioChannels(AppConfig.Media.AUDIO_CHANNELS)
            setOutputFile(out.absolutePath)
            // 达最大时长自动停止（计的是实际录制时长，暂停不计）
            setMaxDuration(AppConfig.Media.MAX_RECORD_MS.toInt())
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    AppLog.i(TAG) { "max duration reached -> auto stop" }
                    onMaxDurationReached?.invoke()
                }
            }
            prepare()
            start()
        }
        recorder = rec
        true
    } catch (e: Exception) {
        AppLog.w(TAG) { "start failed: ${e.message}" }
        releaseQuietly()
        deleteOutput()
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

    /** 停止并返回录音文件路径；失败返回 null（并删除残file）。 */
    fun stop(): String? {
        return try {
            recorder?.stop()
            releaseQuietly()
            outputPath
        } catch (_: Exception) {
            releaseQuietly()
            deleteOutput()
            null
        }
    }

    /** 取消录音并删除文件。 */
    fun cancel() {
        runCatching { recorder?.stop() }
        releaseQuietly()
        deleteOutput()
    }

    private fun deleteOutput() {
        outputFile?.let { f -> runCatching { f.delete() } }
        outputFile = null
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
    }

    private companion object {
        const val AUDIO_DIR = "note_audio"
        const val TAG = "AudioRecorder"
    }
}
