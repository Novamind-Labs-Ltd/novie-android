package com.novamind.app.feature.home

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.BuildConfig
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
private enum class HomeOverlay { None, Notifications, Upcoming, Permissions, Avatar, About }

@Composable
fun HomeRoute(
    onUpcomingSeeAll: () -> Unit = {},
    onNotesSeeAll: () -> Unit = {},
    onNoteClick: (noteId: String) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},   // 子页/抽屉打开 → 宿主隐藏底部导航
    onLogout: () -> Unit = {},                     // 退出登录（由宿主交给 AuthViewModel 处理）
    onSwitchToLogin: () -> Unit = {},              // 游客切换到登录页
    userName: String? = null,                      // 显示昵称（来自全局 UserSession.profile，游客为空）
    userEmail: String? = null,                     // 登录邮箱（来自 UserSession.profile/userKey，游客为空）
    isGuest: Boolean = false,                      // 游客模式（无 Auth0 会话，隐藏退出登录）
    biometricAvailable: Boolean = false,           // 设备是否支持生物识别（已录入）
    biometricEnabled: Boolean = false,             // 是否已开启指纹登录
    onToggleBiometric: (Boolean) -> Unit = {},     // 切换指纹登录开关
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 每次回到首页（HomeRoute 重新进入组合，如底栏切换 / 从编辑器返回）都静默重拉笔记列表
    LaunchedEffect(Unit) { viewModel.reload() }

    // 个人资料（头像）
    LaunchedEffect(Unit) { ProfileStore.load(context) }
    val avatarPath by ProfileStore.avatarPath.collectAsState()

    var notifications by remember { mutableStateOf(sampleNotifications) }
    var overlay by remember { mutableStateOf(HomeOverlay.None) }
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
                name = userName?.takeIf { it.isNotBlank() } ?: "Guest",
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
                showLogout = !isGuest,
                onLogin = {
                    scope.launch { drawerState.close() }
                    onSwitchToLogin()
                },
                showLogin = isGuest,
                // 指纹登录开关：仅真实登录用户、且设备支持生物识别时显示
                showBiometricToggle = biometricAvailable && !isGuest,
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
                val forward = targetState != HomeOverlay.None
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
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onUpcomingSeeAll = { overlay = HomeOverlay.Upcoming },
                    onNotesSeeAll = onNotesSeeAll,
                    onNoteClick = onNoteClick,
                    onNotificationsClick = { overlay = HomeOverlay.Notifications },
                    onMenuAction = { item ->
                        // 「更多」底部菜单动作：Permissions / About 有对应页面，其余暂为占位
                        when (item) {
                            HomeMenuItem.Permissions -> overlay = HomeOverlay.Permissions
                            HomeMenuItem.AboutMyNovie -> overlay = HomeOverlay.About
                            else -> Unit
                        }
                    },
                    onAvatarClick = { scope.launch { drawerState.open() } },
                    onRefresh = viewModel::onRefresh,
                    avatarPath = avatarPath,
                    notificationCount = notifications.unreadCount(),
                )

                HomeOverlay.Notifications -> NotificationListScreen(
                    notifications = notifications,
                    onBack = { overlay = HomeOverlay.None },
                    onMarkAllRead = { notifications = notifications.map { it.copy(read = true) } },
                )

                HomeOverlay.Upcoming -> UpcomingListScreen(
                    items = sampleUpcoming,
                    onBack = { overlay = HomeOverlay.None },
                )

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
