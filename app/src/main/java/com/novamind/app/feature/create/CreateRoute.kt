package com.novamind.app.feature.create

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.common.config.AppConfig

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun CreateRoute(
    onBack: () -> Unit = {},
    noteId: String? = null,           // 非 null 时加载已有笔记
    onFullscreenChange: (Boolean) -> Unit = {},  // 全屏页（图片预览）显隐 → 宿主隐藏底部导航
    maxImages: Int = AppConfig.Media.MAX_IMAGE_PICK,  // 一次最多可选图片数
    readOnly: Boolean = false,        // 回收站只读态：仅查看，不可编辑；顶栏显示 Restore / Delete
    onRestore: () -> Unit = {},       // 只读态「Restore」：由回收站宿主处理（恢复笔记）
    onDeleteForever: () -> Unit = {}, // 只读态「Delete」：由回收站宿主处理（彻底删除）
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = viewModel(),
) {
    // 进入页面时：有 noteId 则加载已有笔记，否则新建
    LaunchedEffect(noteId) {
        if (noteId != null) viewModel.loadNote(noteId)
        else viewModel.reset()
    }
    // 离开笔记页（切 tab / 返回，本 Route 离开组合）即停止转写轮询，不再调用 /transcription；
    // 重新进入会由 loadNote → syncTranscriptionOnOpen 按服务端状态恢复。
    DisposableEffect(Unit) {
        onDispose { viewModel.stopTranscriptionPolling() }
    }
    // 收一次性导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
    }
    // 保存失败一次性提示（POST/PUT 业务或网络错误；含源录音上传失败）
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.saveError.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    // 源录音上传成功一次性提示
    LaunchedEffect(Unit) {
        viewModel.recordingUploaded.collect {
            Toast.makeText(context, "Recording uploaded", Toast.LENGTH_SHORT).show()
        }
    }

    // 生命周期兜底：退后台(ON_STOP)或离开本页(onDispose)时立即落盘，
    // 让自动保存的防抖/封顶可以放心拉长而不丢数据。
    // 只读态（回收站查看）不落盘——不应改动已删除的笔记。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, readOnly) {
        if (readOnly) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val attachmentUrls by viewModel.attachmentUrls.collectAsStateWithLifecycle()
    CreateScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        autoFocusBody = noteId == null && !readOnly,   // 新建笔记自动聚焦正文并弹出键盘；只读态不聚焦
        onFullscreenChange = onFullscreenChange,
        maxImages = maxImages,
        readOnly = readOnly,
        onRestore = onRestore,
        onDeleteForever = onDeleteForever,
        onUploadImage = viewModel::uploadImage,
        attachmentUrls = attachmentUrls,
        onUploadRecording = viewModel::uploadRecording,
        onCancelUploadRecording = viewModel::cancelAudioUpload,
        recordingUploaded = viewModel.recordingUploaded,
        transcriptionReady = viewModel.transcriptionReady,
        modifier = modifier,
    )
}
