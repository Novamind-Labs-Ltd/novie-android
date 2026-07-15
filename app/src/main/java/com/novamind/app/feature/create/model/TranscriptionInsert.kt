package com.novamind.app.feature.create.model

import kotlinx.coroutines.CompletableDeferred

/**
 * 转写结果插入事件：[html] 为渲染好的富文本，[ack] 供 UI 追加并回流 body 后 complete，
 * VM 据此确认「UI 已更新完成」再保存（见 CreateViewModel.startTranscriptionPolling 的 READY 分支）。
 */
data class TranscriptionInsert(val html: String, val ack: CompletableDeferred<Unit>)
