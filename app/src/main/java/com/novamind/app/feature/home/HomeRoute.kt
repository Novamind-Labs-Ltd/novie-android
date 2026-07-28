package com.novamind.app.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.BuildConfig
import com.novamind.app.common.google.GoogleCalendarAuthManager
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.feature.calendar.AddTaskScreen
import com.novamind.app.feature.calendar.MeetingDetailScreen
import com.novamind.app.common.notifications.NotificationListScreen
import com.novamind.app.common.notifications.sampleNotifications
import com.novamind.app.common.notifications.unreadCount
import com.novamind.app.common.permission.PermissionManagerScreen
import com.novamind.app.common.profile.AvatarViewerScreen
import com.novamind.app.common.profile.ProfileDrawerContent
import com.novamind.app.common.profile.ProfileStore
import com.novamind.app.ui.components.DeleteConfirmSheet
import kotlinx.coroutines.launch

/** Home 下的子页面 */
private enum class HomeOverlay { None, Notifications, Upcoming, Permissions, Avatar, About, TaskDetail, MeetingDetail }

@Composable
fun HomeRoute(
    onUpcomingSeeAll: () -> Unit = {},
    onNotesSeeAll: () -> Unit = {},
    onNoteClick: (noteId: String) -> Unit = {},
    onAskNovie: () -> Unit = {},                   // Ask Novie 入口（由宿主接入创建/助手流程）
    onFullscreenChange: (Boolean) -> Unit = {},   // 子页/抽屉打开 → 宿主隐藏底部导航
    onLogout: () -> Unit = {},                     // 退出登录（由宿主交给 AuthViewModel 处理）
    userName: String? = null,                      // 显示昵称（来自全局 UserSession.profile）
    userEmail: String? = null,                     // 登录邮箱（来自 UserSession.profile/userKey）
    biometricAvailable: Boolean = false,           // 设备是否支持生物识别（已录入）
    biometricEnabled: Boolean = false,             // 是否已开启指纹登录
    onToggleBiometric: (Boolean) -> Unit = {},     // 切换指纹登录开关
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarAuthManager = remember { GoogleCalendarAuthManager(context) }

    // 每次回到首页（HomeRoute 重新进入组合，如底栏切换 / 从编辑器返回）都静默重拉笔记列表
    LaunchedEffect(Unit) { viewModel.reload() }
    val currentOnNoteClick by rememberUpdatedState(onNoteClick)
    LaunchedEffect(viewModel) {
        viewModel.openNote.collect { noteId -> currentOnNoteClick(noteId) }
    }

    // Up next 未授权时让用户选择设备上的 Google 账号，与 Calendar 页保持一致。
    val calendarAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { calendarAuthManager.tokenFromAuthorizationResult(result.data) }
                .onSuccess { token ->
                    if (token != null) viewModel.onCalendarTokenObtained(token)
                    else viewModel.onConsentCancelled()
                }
                .onFailure { viewModel.onConsentCancelled() }
        } else viewModel.onConsentCancelled()
    }

    fun connectGoogleCalendar() {
        if (uiState.calendarConnecting) return
        viewModel.beginCalendarConnection()
        scope.launch {
            runCatching { calendarAuthManager.requestAuthorization() }
                .onSuccess { outcome ->
                    when (outcome) {
                        is GoogleCalendarAuthManager.AuthorizationOutcome.Authorized ->
                            viewModel.onCalendarTokenObtained(outcome.accessToken)

                        is GoogleCalendarAuthManager.AuthorizationOutcome.NeedsConsent ->
                            calendarAuthorizationLauncher.launch(
                                IntentSenderRequest.Builder(outcome.intentSender).build(),
                            )
                    }
                }
                .onFailure { viewModel.onConsentCancelled() }
        }
    }

    // 个人资料（头像）
    LaunchedEffect(Unit) { ProfileStore.load(context) }
    val avatarPath by ProfileStore.avatarPath.collectAsState()

    var notifications by remember { mutableStateOf(sampleNotifications) }
    // 离开 Home 打开笔记编辑器时保留当前子页，返回后恢复到原来的 Upcoming 等页面。
    var overlay by rememberSaveable { mutableStateOf(HomeOverlay.None) }
    LaunchedEffect(overlay) {
        if (overlay == HomeOverlay.Upcoming) viewModel.loadUpcomingRange()
    }
    // 当前打开详情的任务（TaskDetail 覆盖层用）
    var selectedTask by remember { mutableStateOf<CalendarTask?>(null) }
    // 当前打开详情的会议（MeetingDetail 覆盖层用）
    var selectedUpcomingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    // 会议详情返回来源：从首页 Up next 返回首页，从 Upcoming 返回 Upcoming。
    var meetingDetailReturnOverlay by remember { mutableStateOf(HomeOverlay.None) }
    // 退出登录二次确认弹窗
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerOpen = drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(overlay, drawerOpen) {
        onFullscreenChange(overlay != HomeOverlay.None || drawerOpen)
    }

    // 打开头像编辑页（关抽屉后切到 Avatar 子页）
    fun openAvatar() {
        scope.launch {
            drawerState.close()
            overlay = HomeOverlay.Avatar
        }
    }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

  Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // 仅在首页允许侧滑开抽屉；抽屉已开时允许侧滑关
        gesturesEnabled = overlay == HomeOverlay.None || drawerState.isOpen,
        drawerContent = {
            ProfileDrawerContent(
                avatarPath = avatarPath,
                name = userName?.takeIf { it.isNotBlank() } ?: "",
                email = userEmail.orEmpty(),
                onChangeAvatar = { openAvatar() },
                onViewAvatar = { openAvatar() },
                onOpenPermissions = {
                    scope.launch {
                        drawerState.close()
                        overlay = HomeOverlay.Permissions
                    }
                },
                // 点退出登录先弹二次确认，确认后才真正登出
                onLogout = { showLogoutConfirm = true },
                // 指纹登录开关：设备支持生物识别时显示
                showBiometricToggle = biometricAvailable,
                biometricEnabled = biometricEnabled,
                onToggleBiometric = onToggleBiometric,
            )
        },
    ) {
        // Home ↔ 子页：与 MainActivity 一致的页面转场
        AnimatedContent(
            targetState = overlay,
            modifier = modifier,
            transitionSpec = {
                // 会议详情既可能从首页也可能从 Upcoming 打开；返回时目标状态仍是一个
                // 子页，因此不能只用 targetState != None 判断，否则 Back 会播放进入动画。
                val forward = targetState != HomeOverlay.None &&
                    initialState != HomeOverlay.MeetingDetail
                if (forward) {
                    (slideInHorizontally { it } + fadeIn(initialAlpha = 0.3f))
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn(initialAlpha = 0.3f))
                        .togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "home_overlay",
        ) { ov ->
            when (ov) {
                HomeOverlay.None -> HomeScreen(
                    uiState = uiState,
                    onUpcomingSeeAll = { overlay = HomeOverlay.Upcoming },
                    onNotesSeeAll = onNotesSeeAll,
                    onNoteClick = onNoteClick,
                    // 点任务卡 → 取回原始 CalendarTask 打开详情/编辑覆盖层
                    onTaskClick = { itemId ->
                        uiState.todayTasks.find { "task_${it.id}" == itemId }?.let { task ->
                            selectedTask = task
                            overlay = HomeOverlay.TaskDetail
                        }
                    },
                    onMeetingClick = { itemId ->
                        uiState.upcomingEvents
                            .firstOrNull { event -> "evt_${event.id}" == itemId }
                            ?.let { event ->
                                selectedUpcomingEvent = event
                                meetingDetailReturnOverlay = HomeOverlay.None
                                overlay = HomeOverlay.MeetingDetail
                            }
                    },
                    onNotificationsClick = { overlay = HomeOverlay.Notifications },
                    onAskNovie = onAskNovie,
                    onMeetingNotesClick = { item ->
                        uiState.upcomingEvents
                            .firstOrNull { event -> "evt_${event.id}" == item.id }
                            ?.let { event -> viewModel.openMeetingNote(event.id, item.noteId) }
                    },
                    onAvatarClick = { scope.launch { drawerState.open() } },
                    onRefresh = viewModel::onRefresh,
                    onConnectCalendar = ::connectGoogleCalendar,
                    userName = userName?.takeIf { it.isNotBlank() } ?: "",
                    avatarPath = avatarPath,
                    notificationCount = notifications.unreadCount(),
                    calendarNeedsAuth = uiState.calendarNeedsAuth,
                    calendarConnecting = uiState.calendarConnecting,
                )

                HomeOverlay.Notifications -> NotificationListScreen(
                    notifications = notifications,
                    onBack = { overlay = HomeOverlay.None },
                    onMarkAllRead = { notifications = notifications.map { it.copy(read = true) } },
                )

                HomeOverlay.Upcoming -> UpcomingListScreen(
                    items = uiState.upcomingRangeItems,
                    onBack = { overlay = HomeOverlay.None },
                    onItemClick = { item ->
                        uiState.upcomingRangeEvents
                            .firstOrNull { event -> "evt_${event.id}" == item.id }
                            ?.let { event ->
                                selectedUpcomingEvent = event
                                meetingDetailReturnOverlay = HomeOverlay.Upcoming
                                overlay = HomeOverlay.MeetingDetail
                            }
                    },
                    onMeetingNotesClick = { item ->
                        uiState.upcomingRangeEvents
                            .firstOrNull { event -> "evt_${event.id}" == item.id }
                            ?.let { event -> viewModel.openMeetingNote(event.id, item.noteId) }
                    },
                    isLoading = uiState.upcomingRangeLoading,
                    calendarNeedsAuth = uiState.calendarNeedsAuth,
                    calendarConnecting = uiState.calendarConnecting,
                    onConnectCalendar = ::connectGoogleCalendar,
                )

                HomeOverlay.MeetingDetail -> {
                    selectedUpcomingEvent?.let { event ->
                        key(event.id) {
                            MeetingDetailScreen(
                                event = event,
                                onBack = { overlay = meetingDetailReturnOverlay },
                                onEdit = null,
                            )
                        }
                    }
                }

                HomeOverlay.Permissions -> PermissionManagerScreen(
                    onBack = { overlay = HomeOverlay.None },
                )

                HomeOverlay.Avatar -> AvatarViewerScreen(
                    avatarPath = avatarPath,
                    onBack = { overlay = HomeOverlay.None },
                    onAvatarPicked = { path -> ProfileStore.setLocalAvatar(context, path) },
                )

                HomeOverlay.About -> AboutMyNovieScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    onBack = { overlay = HomeOverlay.None },
                )

                // 任务详情/编辑：复用日历页的 AddTaskScreen（编辑模式），改动写回 Google Tasks
                HomeOverlay.TaskDetail -> {
                    val task = selectedTask
                    if (task == null) {
                        overlay = HomeOverlay.None
                    } else {
                        // key：切换不同任务时强制重建表单（rememberSaveable 只取首次初值）
                        key(task.id) {
                            AddTaskScreen(
                                initialDue = task.due ?: LocalDate.now(),
                                initialTitle = task.title,
                                initialNotes = task.notes.orEmpty(),
                                completed = task.isCompleted,
                                onSave = { title, notes, due ->
                                    overlay = HomeOverlay.None
                                    viewModel.updateTask(task, title, notes, due)
                                },
                                onToggleCompleted = {
                                    overlay = HomeOverlay.None
                                    viewModel.setTaskCompleted(task, !task.isCompleted)
                                },
                                onDelete = {
                                    overlay = HomeOverlay.None
                                    viewModel.deleteTask(task)
                                },
                                onBack = { overlay = HomeOverlay.None },
                            )
                        }
                    }
                }
            }
        }
    }

    // 退出登录二次确认
    if (showLogoutConfirm) {
        DeleteConfirmSheet(
            onConfirm = {
                showLogoutConfirm = false
                scope.launch { drawerState.close() }
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false },
            title = "Sign out?",
            message = "You'll need to sign in again to keep syncing your notes and settings.",
            confirmLabel = "Sign out",
        )
    }
  }
}
