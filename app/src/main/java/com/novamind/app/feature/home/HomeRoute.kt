package com.novamind.app.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.novamind.app.common.notifications.NotificationListScreen
import com.novamind.app.common.notifications.sampleNotifications
import com.novamind.app.common.notifications.unreadCount
import com.novamind.app.common.profile.ProfileDrawerContent
import com.novamind.app.common.profile.ProfileStore
import com.novamind.app.feature.create.editor.ImageStore
import kotlinx.coroutines.launch

/** Home 下的子页面 */
private enum class HomeOverlay { None, Notifications, Upcoming }

@Composable
fun HomeRoute(
    onUpcomingSeeAll: () -> Unit = {},
    onNotesSeeAll: () -> Unit = {},
    onNoteClick: (noteId: String) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},   // 子页/抽屉打开 → 宿主隐藏底部导航
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 个人资料（头像）
    LaunchedEffect(Unit) { ProfileStore.load(context) }
    val avatarPath by ProfileStore.avatarPath.collectAsState()
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageStore.copyToInternal(context, uri)?.let { ProfileStore.setAvatar(context, it) }
        }
    }

    var notifications by remember { mutableStateOf(sampleNotifications) }
    var overlay by remember { mutableStateOf(HomeOverlay.None) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerOpen = drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(overlay, drawerOpen) { onFullscreenChange(overlay != HomeOverlay.None || drawerOpen) }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 仅在首页允许侧滑开抽屉；抽屉已开时允许侧滑关
        gesturesEnabled = overlay == HomeOverlay.None || drawerState.isOpen,
        drawerContent = {
            ProfileDrawerContent(
                avatarPath = avatarPath,
                name = "chenbin.zhou",
                email = "chenbin.zhou@novamind-labs.ai",
                onChangeAvatar = {
                    avatarPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
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
                    onAvatarClick = { scope.launch { drawerState.open() } },
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
            }
        }
    }
}
