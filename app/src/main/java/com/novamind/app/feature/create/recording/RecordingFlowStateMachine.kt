package com.novamind.app.feature.create.recording

import android.content.Context
import com.novamind.app.common.audio.RecordingController
import com.novamind.app.common.audio.RecordingService
import com.novamind.app.common.audio.RecordingSnapshot
import com.novamind.app.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 录音服务指令的抽象（可注入假实现做单元测试）。默认实现转发到前台服务。 */
interface RecorderCommands {
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun cancel()
}

/** 默认实现：转发到 [RecordingService]（前台服务，锁屏/后台录音不中断）。 */
class ServiceRecorderCommands(context: Context) : RecorderCommands {
    private val appContext = context.applicationContext
    override fun start() = RecordingService.start(appContext)
    override fun pause() = RecordingService.pause(appContext)
    override fun resume() = RecordingService.resume(appContext)
    override fun stop() = RecordingService.stop(appContext)
    override fun cancel() = RecordingService.cancel(appContext)
}

/** 云上传器抽象：返回 remoteUrl（可空）。接入真实后端前可用 [None] 占位（直接成功）。 */
fun interface RecordingUploader {
    suspend fun upload(file: RecordedFile): Result<String?>

    companion object {
        /** 占位实现：不上传、直接成功（当前录音仅本地插入笔记时使用）。 */
        val None = RecordingUploader { Result.success(null) }
    }
}

/**
 * 录音全流程状态机：开始录音 → 生成文件 → 上传云端（状态图见 [RecordingFlowState]）。
 *
 * 职责与边界：
 * - **单一数据源**：对外只暴露 [state]；录制期的暂停/计时/振幅从 [RecordingController]
 *   快照映射进 [RecordingFlowState.Recording]，UI 不再直接观察 Controller；
 * - **指令下发**：start/pause/resume/stop/cancel 经 [RecorderCommands] 转发到前台服务，
 *   通知栏操作（暂停/停止）与界面操作殊途同归——都以服务回写的快照为准；
 * - **非法迁移**：一律忽略并打日志（如 Uploading 中调 pause），保证状态图收敛；
 * - 上传状态若需持久化（断点续传），由 [RecordingUploader] 实现方写
 *   [com.novamind.app.data.RecordingRepository] 的 UploadStatus，状态机不落库。
 *
 * @param scope 驱动快照收集与上传的协程作用域（通常为 viewModelScope）。
 */
class RecordingFlowStateMachine(
    private val scope: CoroutineScope,
    private val commands: RecorderCommands,
    private val uploader: RecordingUploader = RecordingUploader.None,
    snapshots: Flow<RecordingSnapshot> = RecordingController.state,
) {

    private val _state = MutableStateFlow<RecordingFlowState>(RecordingFlowState.Idle)
    val state = _state.asStateFlow()

    init {
        scope.launch { snapshots.collect(::onSnapshot) }
    }

    // ── 对外指令（UI 事件入口） ──

    /** Idle → Recording：重置共享快照并启动前台录音服务。 */
    fun start() {
        transitionGuard<RecordingFlowState.Idle>("start") ?: return
        RecordingController.reset()
        commands.start()
        _state.value = RecordingFlowState.Recording(paused = false, elapsedSeconds = 0, amplitude = 0)
    }

    fun pause() {
        transitionGuard<RecordingFlowState.Recording>("pause") ?: return
        commands.pause()
    }

    fun resume() {
        transitionGuard<RecordingFlowState.Recording>("resume") ?: return
        commands.resume()
    }

    /** Recording → Finalizing：下发停止，等待服务回写文件结果后自动上传。 */
    fun stopAndUpload() {
        transitionGuard<RecordingFlowState.Recording>("stopAndUpload") ?: return
        _state.value = RecordingFlowState.Finalizing
        commands.stop()
    }

    /** UploadFailed → Uploading：重传同一文件。 */
    fun retryUpload() {
        val failed = transitionGuard<RecordingFlowState.UploadFailed>("retryUpload") ?: return
        beginUpload(failed.file)
    }

    /**
     * 丢弃当前流程回到 Idle：
     * - 录制/文件生成中 → 取消服务（服务侧删除分片）；
     * - 已有文件（FileReady/Uploading/UploadFailed）→ 删除本地文件。
     */
    fun discard() {
        when (val s = _state.value) {
            is RecordingFlowState.Recording, RecordingFlowState.Finalizing -> commands.cancel()
            is RecordingFlowState.FileReady -> deleteAndReset(s.file)
            is RecordingFlowState.Uploading -> deleteAndReset(s.file) // 上传结果回来后被忽略（已回 Idle）
            is RecordingFlowState.UploadFailed -> deleteAndReset(s.file)
            RecordingFlowState.Idle, is RecordingFlowState.Uploaded ->
                LogUtils.w("discard ignored in ${s.stateName()}", tag = TAG)
        }
    }

    /** 终态 Uploaded 消费完毕后归位（如 UI 已插入笔记）。 */
    fun reset() {
        _state.value = RecordingFlowState.Idle
        RecordingController.reset()
    }

    // ── 服务快照 → 状态迁移 ──

    private fun onSnapshot(snapshot: RecordingSnapshot) {
        // 一次性事件优先：完成（文件已生成）/ 取消。
        val result = snapshot.result
        if (result != null) {
            RecordingController.consumeResult()
            when (_state.value) {
                is RecordingFlowState.Recording, RecordingFlowState.Finalizing -> {
                    val file = RecordedFile(result.path, result.durationSeconds)
                    _state.value = RecordingFlowState.FileReady(file)
                    beginUpload(file)
                }
                // 其它状态（如已 discard 回 Idle）：结果作废，删除文件防泄漏。
                else -> runCatching { File(result.path).delete() }
            }
            return
        }
        if (snapshot.cancelled) {
            RecordingController.consumeCancelled()
            if (_state.value is RecordingFlowState.Recording || _state.value == RecordingFlowState.Finalizing) {
                _state.value = RecordingFlowState.Idle
            }
            return
        }
        // 录制期：把服务快照映射为 Recording 状态（含通知栏发起的暂停/继续）。
        if (snapshot.active && _state.value is RecordingFlowState.Recording) {
            _state.value = RecordingFlowState.Recording(
                paused = snapshot.paused,
                elapsedSeconds = snapshot.elapsedSeconds,
                amplitude = snapshot.amplitude,
            )
        }
    }

    // ── 内部 ──

    private fun beginUpload(file: RecordedFile) {
        _state.value = RecordingFlowState.Uploading(file)
        scope.launch {
            val outcome = uploader.upload(file)
            // discard 等并发操作可能已把状态推走：仅在仍处于本次 Uploading 时落结果。
            if (_state.value != RecordingFlowState.Uploading(file)) return@launch
            _state.value = outcome.fold(
                onSuccess = { url -> RecordingFlowState.Uploaded(file, url) },
                onFailure = { e ->
                    LogUtils.w("upload failed: ${file.path}", e, TAG)
                    RecordingFlowState.UploadFailed(file, e)
                },
            )
        }
    }

    private fun deleteAndReset(file: RecordedFile) {
        runCatching { File(file.path).delete() }
        _state.value = RecordingFlowState.Idle
    }

    /** 当前状态为 [T] 时返回它，否则打日志并返回 null（忽略非法迁移）。 */
    private inline fun <reified T : RecordingFlowState> transitionGuard(action: String): T? {
        val s = _state.value
        return (s as? T) ?: run {
            LogUtils.w("$action ignored in ${s.stateName()}", tag = TAG)
            null
        }
    }

    private fun RecordingFlowState.stateName(): String = this::class.simpleName ?: toString()

    private companion object {
        const val TAG = "RecordingFlow"
    }
}
