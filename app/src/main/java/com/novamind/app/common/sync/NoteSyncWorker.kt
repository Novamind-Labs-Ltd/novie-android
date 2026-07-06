package com.novamind.app.common.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.NotesRepository
import com.novamind.app.data.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 笔记同步 Worker（离线优先，见 docs/note-offline-sync-design.md §4）。
 *
 * **当前为 push-only**：后端仅有 `POST /notes`、`GET /notes/{id}`、`PUT /notes/{id}`，
 * 缺少批量、增量 pull（`GET /notes?since=`）与删除端点。因此本 Worker 只做上行：
 * 取本地「待推送」笔记（LOCAL/DIRTY 且未软删），逐条创建/更新到云端，回写 serverId/rev/SYNCED。
 *
 * 待后端补齐后再加：pull 增量、批量 push、tombstone 删除同步。
 */
@HiltWorker
class NoteSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notesRepository: NotesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).noteDao()
        val pending = dao.pendingPushNotes()
        if (pending.isEmpty()) return Result.success()

        var networkFailure = false
        val now = System.currentTimeMillis()

        for (note in pending) {
            val title = note.title.ifBlank { null }
            if (note.serverId == null) {
                // 新建：POST
                when (val r = notesRepository.createNote(title, note.body)) {
                    is ApiResult.Success -> r.data?.let { remote ->
                        dao.markSynced(note.id, remote.id, remote.rev, now)
                    }
                    is ApiResult.BizError -> AppLog.w(TAG) { "create 失败 id=${note.id} code=${r.code}" }
                    is ApiResult.NetworkError -> networkFailure = true
                }
            } else {
                // 更新：PUT（latest-wins，凭 rev）
                when (val r = notesRepository.updateNote(note.serverId, note.rev, title, note.body)) {
                    is ApiResult.Success -> {
                        val outcome = r.data
                        when {
                            outcome == null -> Unit
                            outcome.applied ->
                                dao.markSynced(note.id, note.serverId, outcome.note?.rev ?: note.rev, now)
                            else -> {
                                // 被更新版本抢先：暂无 pull 端点合并，先标 CONFLICT
                                AppLog.w(TAG) { "update 冲突 id=${note.id} serverId=${note.serverId}" }
                                dao.markConflict(note.id)
                            }
                        }
                    }
                    is ApiResult.BizError -> AppLog.w(TAG) { "update 失败 id=${note.id} code=${r.code}" }
                    is ApiResult.NetworkError -> networkFailure = true
                }
            }
        }
        // 网络类失败 → 整体重试（WorkManager 指数退避）；业务错误已各自处理，不整体重试。
        return if (networkFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "NoteSync"
    }
}
