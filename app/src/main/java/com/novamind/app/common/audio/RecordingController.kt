package com.novamind.app.common.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次录音完成（停止保存）的结果。 */
data class RecordingResult(
    val path: String,
    val durationSeconds: Int,
    val peakAmplitude: Int,
)

/**
 * 录音的全局快照状态。UI（[com.novamind.app.ui.components.VoiceRecordingBar]）与
 * 前台服务（[RecordingService]）共用这一个数据源，使得锁屏 / 后台时录音不中断，
 * 回到前台 UI 能立即同步计时、波形与最终结果。
 *
 * [result] 与 [cancelled] 为一次性事件，UI 消费后需调用 [RecordingController.consumeResult] /
 * [RecordingController.consumeCancelled] 清除。
 */
data class RecordingSnapshot(
    /** 服务是否在录音中（含暂停）。 */
    val active: Boolean = false,
    val paused: Boolean = false,
    val elapsedSeconds: Int = 0,
    /** 最近一次最大振幅（0..32767）。 */
    val amplitude: Int = 0,
    /** 整段录音的峰值振幅，用于「没有声音」判定。 */
    val peakAmplitude: Int = 0,
    /** 一次性事件：录音完成。 */
    val result: RecordingResult? = null,
    /** 一次性事件：录音被取消 / 启动失败。 */
    val cancelled: Boolean = false,
)

/** 录音共享状态的单一数据源。 */
object RecordingController {

    private val _state = MutableStateFlow(RecordingSnapshot())
    val state: StateFlow<RecordingSnapshot> = _state.asStateFlow()

    internal fun update(block: (RecordingSnapshot) -> RecordingSnapshot) {
        _state.value = block(_state.value)
    }

    /** UI 消费「完成」事件后清除。 */
    fun consumeResult() {
        _state.value = _state.value.copy(result = null)
    }

    /** UI 消费「取消」事件后清除。 */
    fun consumeCancelled() {
        _state.value = _state.value.copy(cancelled = false)
    }

    /** 重置为初始状态（开始新录音前调用）。 */
    fun reset() {
        _state.value = RecordingSnapshot()
    }
}
