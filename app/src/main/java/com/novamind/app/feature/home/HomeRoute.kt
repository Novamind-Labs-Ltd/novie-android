package com.novamind.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.feature.notifications.NotificationListScreen
import com.novamind.app.feature.notifications.sampleNotifications
import com.novamind.app.feature.notifications.unreadCount

@Composable
fun HomeRoute(
    onUpcomingSeeAll: () -> Unit = {},
    onNotesSeeAll: () -> Unit = {},
    onNoteClick: (noteId: String) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},   // 通知列表全屏页 → 宿主隐藏底部导航
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var notifications by remember { mutableStateOf(sampleNotifications) }
    var showNotifications by remember { mutableStateOf(false) }
    LaunchedEffect(showNotifications) { onFullscreenChange(showNotifications) }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    // 首页 / 通知页之间用与 MainActivity 完全一致的页面转场（含底层页视差）
    AnimatedContent(
        targetState = showNotifications,
        modifier = modifier,
        transitionSpec = {
            if (targetState) {
                // 进入通知页（前进）
                (slideInHorizontally { it } + fadeIn(initialAlpha = 0.3f))
                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                // 退出通知页 / 返回首页（后退）
                (slideInHorizontally { -it / 3 } + fadeIn(initialAlpha = 0.3f))
                    .togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "home_notifications",
    ) { showing ->
        if (showing) {
            NotificationListScreen(
                notifications = notifications,
                onBack = { showNotifications = false },
                onMarkAllRead = { notifications = notifications.map { it.copy(read = true) } },
            )
        } else {
            HomeScreen(
                uiState = uiState,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onUpcomingSeeAll = onUpcomingSeeAll,
                onNotesSeeAll = onNotesSeeAll,
                onNoteClick = onNoteClick,
                onNotificationsClick = { showNotifications = true },
                notificationCount = notifications.unreadCount(),
            )
        }
    }
}
