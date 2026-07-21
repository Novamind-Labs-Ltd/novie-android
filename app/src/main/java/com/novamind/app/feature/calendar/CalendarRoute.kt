package com.novamind.app.feature.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.tasks.CalendarTask
import kotlinx.coroutines.launch

/**
 * Calendar 的有状态路由：连接 [CalendarViewModel] 与无状态的 [CalendarScreen]。
 *
 * 日历账户跟随 App 登录账户，**不弹账号选择器**：
 * - [CalendarUiEvent.Connect] / [CalendarUiEvent.SwitchAccount] → 直接用当前登录账户取 token；
 * - VM 取 token 若需用户同意（首次授权 calendar 范围），通过 [CalendarViewModel.consentRequest]
 *   请求启动 OAuth 同意页，返回后调 [CalendarViewModel.onConsentGranted] 重试。
 */
@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    onFullscreenChange: (Boolean) -> Unit = {},
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 新增/编辑任务覆盖层（点顶部「+」或任务行打开）；打开时隐藏底部导航栏。
    var showAddTask by rememberSaveable { mutableStateOf(false) }
    // 正在编辑的任务；null = 新增模式。CalendarTask 非 Parcelable，进程重建后
    // 覆盖层可能退化为新增模式，可接受（编辑内容本就未保存）。
    var editingTask by remember { mutableStateOf<CalendarTask?>(null) }
    // 会议详情覆盖层（点活动行打开）；CalendarEvent 非 Parcelable，进程重建后覆盖层关闭，可接受（只读页）。
    // showDetail 控制显隐，selectedEvent 保留内容（关闭时不清，供退出动画期间继续渲染）。
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    LaunchedEffect(showAddTask, showDetail) {
        onFullscreenChange(showAddTask || showDetail)
    }
    // 覆盖层开着时切走 tab（本 Route 离开组合）→ 恢复底栏，避免 hideBottomNav 卡住。
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }
    // 系统返回由 AddTaskScreen 内部的 BackHandler 接管（含未保存变更的放弃确认），
    // 此处不再拦截，避免绕过脏检查直接关闭。

    // 恢复授权（OAuth 同意）结果：同意后用登录账户重试取 token。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentGranted()
        } else {
            viewModel.onEvent(CalendarUiEvent.AuthFailed("Authorization cancelled"))
        }
    }

    // VM 请求同意时启动恢复意图。
    LaunchedEffect(Unit) {
        viewModel.consentRequest.collect { intent -> consentLauncher.launch(intent) }
    }

    // 每次页面显示（进入/切回 tab/回前台）都刷新。
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenShown()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CalendarScreen(
            uiState = uiState,
            onEvent = { event ->
                when (event) {
                    // 连接：直接用当前登录账户取 token。
                    CalendarUiEvent.Connect -> viewModel.connectWithCurrentAccount()
                    // 重新授权当前账户：先清/吊销旧授权，再用登录账户重连。
                    CalendarUiEvent.SwitchAccount -> scope.launch {
                        viewModel.prepareAccountSwitch()
                        viewModel.connectWithCurrentAccount()
                    }
                    // 新增任务：仅已连接时打开（未连接创建必然失败）。
                    CalendarUiEvent.AddTaskClicked -> if (uiState.isConnected) {
                        editingTask = null
                        showAddTask = true
                    }
                    // 点击任务行 → 编辑模式打开同一覆盖层。
                    is CalendarUiEvent.TaskClicked -> {
                        editingTask = event.task
                        showAddTask = true
                    }
                    // 点击活动/会议行 → 打开会议详情覆盖层。
                    is CalendarUiEvent.EventClicked -> {
                        selectedEvent = event.event
                        showDetail = true
                    }
                    else -> viewModel.onEvent(event)
                }
            },
        )

        // 新增任务页（全屏覆盖，自带返回；与其他子页一致的左右滑动转场）
        AnimatedVisibility(
            visible = showAddTask,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            // key：在「新增 ↔ 编辑不同任务」之间切换时强制重建表单状态（rememberSaveable 只取首次初值）。
            key(editingTask?.id) {
                AddTaskScreen(
                    initialDue = editingTask?.due ?: uiState.selectedDate,
                    initialTitle = editingTask?.title.orEmpty(),
                    initialNotes = editingTask?.notes.orEmpty(),
                    onSave = { title, notes, due ->
                        showAddTask = false
                        val editing = editingTask
                        viewModel.onEvent(
                            if (editing != null) {
                                CalendarUiEvent.UpdateTask(editing, title, notes, due)
                            } else {
                                CalendarUiEvent.CreateTask(title, notes, due)
                            },
                        )
                    },
                    onBack = { showAddTask = false },
                    completed = editingTask?.isCompleted,
                    onToggleCompleted = {
                        editingTask?.let { task ->
                            showAddTask = false
                            viewModel.onEvent(
                                CalendarUiEvent.SetTaskCompleted(task, completed = !task.isCompleted),
                            )
                        }
                    },
                    onDelete = editingTask?.let { task ->
                        {
                            showAddTask = false
                            viewModel.onEvent(CalendarUiEvent.DeleteTask(task))
                        }
                    },
                )
            }
        }

        // 会议详情页（全屏覆盖，自带返回；与其他子页一致的左右滑动转场）
        AnimatedVisibility(
            visible = showDetail,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            // key：切换到另一个活动时强制重建。关闭时仅置 showDetail=false，
            // 保留 selectedEvent 供退出动画期间继续渲染（与 AddTaskScreen 一致）。
            key(selectedEvent?.id) {
                selectedEvent?.let { event ->
                    MeetingDetailScreen(
                        event = event,
                        onBack = { showDetail = false },
                    )
                }
            }
        }
    }
}
