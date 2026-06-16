package com.novamind.app.common.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * 基于 [MediaRecorder] 的简易录音器：录制为 m4a（AAC）写入应用内部存储。
 * 支持开始 / 暂停 / 继续 / 停止 / 取消（暂停继续需 API 24+，本应用 minSdk 26 满足）。
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** 当前录音文件绝对路径（停止后有效）。 */
    val outputPath: String? get() = outputFile?.absolutePath

    /** 开始录音。成功返回 true。 */
    fun start(): Boolean = try {
        val dir = File(context.filesDir, AUDIO_DIR).apply { mkdirs() }
        val dest = File(dir, "voice_${UUID.randomUUID()}.m4a")
        outputFile = dest

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
            setOutputFile(dest.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        true
    } catch (_: Exception) {
        releaseQuietly()
        outputFile?.delete()
        outputFile = null
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

    /** 停止并返回录音文件路径；失败返回 null。 */
    fun stop(): String? {
        return try {
            recorder?.stop()
            releaseQuietly()
            outputFile?.absolutePath
        } catch (_: Exception) {
            releaseQuietly()
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    /** 取消录音并删除文件。 */
    fun cancel() {
        runCatching { recorder?.stop() }
        releaseQuietly()
        outputFile?.delete()
        outputFile = null
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
    }

    private companion object {
        const val AUDIO_DIR = "note_audio"
    }
}
