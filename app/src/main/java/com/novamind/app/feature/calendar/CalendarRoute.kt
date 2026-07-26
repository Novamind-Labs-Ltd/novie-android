package com.novamind.app.feature.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.common.google.GoogleCalendarAuthManager
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.tasks.CalendarTask
import kotlinx.coroutines.launch

/**
 * Calendar 的有状态路由：连接 [CalendarViewModel] 与无状态的 [CalendarScreen]。
 *
 * 首次连接通过 Google AuthorizationClient 让用户选择设备上的 Google 账号；授权成功后
 * ViewModel 读取主日历邮箱并绑定到当前 Novie 用户。冷启动续期仍由 ViewModel 对已绑定
 * Google 账号执行，不会误用 Auth0 登录邮箱。
 */
@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    onFullscreenChange: (Boolean) -> Unit = {},
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { GoogleCalendarAuthManager(context) }

    // 新增/编辑任务覆盖层（点顶部「+」或任务行打开）；打开时隐藏底部导航栏。
    var showAddTask by rememberSaveable { mutableStateOf(false) }
    // 正在编辑的任务；null = 新增模式。CalendarTask 非 Parcelable，进程重建后
    // 覆盖层可能退化为新增模式，可接受（编辑内容本就未保存）。
    var editingTask by remember { mutableStateOf<CalendarTask?>(null) }
    // 会议详情覆盖层（点活动行打开）；CalendarEvent 非 Parcelable，进程重建后覆盖层关闭，可接受（只读页）。
    // showDetail 控制显隐，selectedEvent 保留内容（关闭时不清，供退出动画期间继续渲染）。
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    // 编辑会议覆盖层（详情页铅笔打开）。
    var showEditMeeting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showAddTask, showDetail, showEditMeeting) {
        onFullscreenChange(showAddTask || showDetail || showEditMeeting)
    }
    // 覆盖层开着时切走 tab（本 Route 离开组合）→ 恢复底栏，避免 hideBottomNav 卡住。
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }
    // 系统返回由 AddTaskScreen 内部的 BackHandler 接管（含未保存变更的放弃确认），
    // 此处不再拦截，避免绕过脏检查直接关闭。

    // Google 账号选择/授权结果：解析 access token 后交给 ViewModel 建立绑定。
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { authManager.tokenFromAuthorizationResult(result.data) }
                .onSuccess { token ->
                    if (token != null) {
                        viewModel.onEvent(CalendarUiEvent.GoogleTokenObtained(token))
                    } else {
                        viewModel.onEvent(CalendarUiEvent.AuthFailed("No access token returned"))
                    }
                }
                .onFailure { error ->
                    viewModel.onEvent(CalendarUiEvent.AuthFailed(error.message))
                }
        } else {
            viewModel.onEvent(CalendarUiEvent.AuthFailed("Authorization cancelled"))
        }
    }

    fun startGoogleAuthorization() {
        scope.launch {
            runCatching { authManager.requestAuthorization() }
                .onSuccess { outcome ->
                    when (outcome) {
                        is GoogleCalendarAuthManager.AuthorizationOutcome.Authorized ->
                            viewModel.onEvent(CalendarUiEvent.GoogleTokenObtained(outcome.accessToken))

                        is GoogleCalendarAuthManager.AuthorizationOutcome.NeedsConsent ->
                            authorizationLauncher.launch(
                                IntentSenderRequest.Builder(outcome.intentSender).build(),
                            )
                    }
                }
                .onFailure { error ->
                    viewModel.onEvent(CalendarUiEvent.AuthFailed(error.message))
                }
        }
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
                    // 首次连接：让用户选择设备上的 Google 账号并授权。
                    CalendarUiEvent.Connect -> startGoogleAuthorization()
                    // 换账号：先清/吊销旧授权，再重新弹出 Google 账号选择。
                    CalendarUiEvent.SwitchAccount -> scope.launch {
                        viewModel.prepareAccountSwitch()
                        startGoogleAuthorization()
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
                    // 点击活动/会议行 → 打开只读详情页（Figma 1032-42662，含提醒/邀请人等）；
                    // 详情页点右上角铅笔再进编辑页（详情→编辑的连接见下方 MeetingDetailScreen.onEdit）。
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
                        // 暂隐藏编辑入口：不传 onEdit（默认 null）→ 详情页不显示右上角铅笔。
                        // 编辑会议流程（showEditMeeting）保留以便日后恢复。
                        onEdit = null,
                    )
                }
            }
        }

        // 编辑会议页（全屏覆盖，压在详情页之上；Save 写回 Google，返回回到详情）
        AnimatedVisibility(
            visible = showEditMeeting,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            key(selectedEvent?.id) {
                selectedEvent?.let { event ->
                    EditMeetingScreen(
                        event = event,
                        onSave = { updated ->
                            showEditMeeting = false
                            showDetail = false
                            selectedEvent = updated
                            viewModel.onEvent(CalendarUiEvent.UpdateMeeting(updated))
                        },
                        onBack = { showEditMeeting = false },
                    )
                }
            }
        }
    }
}
