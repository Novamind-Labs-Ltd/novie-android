package com.novamind.app.feature.create

import com.novamind.app.util.ToastUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.components.NoNetworkView
import com.novamind.app.util.NetworkUtils

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
    val context = LocalContext.current
    // 无网络覆盖态：进入拉接口时判断一次网络（不做实时监控），离线则显示 NoNetworkView。
    var offline by remember { mutableStateOf(false) }
    // 进入页面时：新建直接重置；打开已有笔记先判网络——在线才拉接口，离线显示 NoNetworkView。
    LaunchedEffect(noteId) {
        when {
            noteId == null -> { viewModel.reset(); offline = false }
            NetworkUtils.isOnline(context) -> { offline = false; viewModel.loadNote(noteId) }
            else -> offline = true
        }
    }
    // 离开笔记页（切 tab / 返回，本 Route 离开组合）即停止转写轮询，不再调用 /transcription；
    // 重新进入会由 loadNote → startTranscriptionPolling 按服务端状态恢复。
    DisposableEffect(Unit) {
        onDispose { viewModel.stopTranscriptionPolling() }
    }
    // 收一次性导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
    }
    // 保存失败一次性提示（POST/PUT 业务或网络错误；含源录音上传失败）
    LaunchedEffect(Unit) {
        viewModel.saveError.collect { msg ->
            ToastUtils.short(context, msg)
        }
    }
    // 源录音上传成功一次性提示
    LaunchedEffect(Unit) {
        viewModel.recordingUploaded.collect {
            ToastUtils.short(context, "Recording uploaded")
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
    Box(modifier = modifier.fillMaxSize()) {
        CreateScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            autoFocusBody = noteId == null && !readOnly,   // 新建笔记自动聚焦正文并弹出键盘；只读态不聚焦
            isEditing = noteId != null,                    // 编辑进入（打开已有笔记）：允许删除 / 改颜色
            onFullscreenChange = onFullscreenChange,
            maxImages = maxImages,
            readOnly = readOnly,
            onRestore = onRestore,
            onDeleteForever = onDeleteForever,
            onUploadImage = viewModel::uploadImage,
            onPolish = viewModel::polish,
            attachmentUrls = attachmentUrls,
            onUploadRecording = viewModel::uploadRecording,
            onCancelUploadRecording = viewModel::cancelAudioUpload,
            recordingUploaded = viewModel.recordingUploaded,
            transcriptionReady = viewModel.transcriptionReady,
        )
        // 打开已有笔记时若离线：铺满显示无网络页；重试再判一次网络，恢复则加载并隐藏。
        if (offline) {
            NoNetworkView(
                modifier = Modifier.fillMaxSize(),
                onRetry = {
                    if (NetworkUtils.isOnline(context)) {
                        offline = false
                        noteId?.let { viewModel.loadNote(it) }
                    }
                },
            )
        }
    }
}
