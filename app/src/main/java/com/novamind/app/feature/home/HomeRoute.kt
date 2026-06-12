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
import com.novamind.app.common.notifications.NotificationListScreen
import com.novamind.app.common.notifications.sampleNotifications
import com.novamind.app.common.notifications.unreadCount

/** Home 下的子页面 */
private enum class HomeOverlay { None, Notifications, Upcoming }

@Composable
fun HomeRoute(
    onUpcomingSeeAll: () -> Unit = {},
    onNotesSeeAll: () -> Unit = {},
    onNoteClick: (noteId: String) -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},   // 子页全屏 → 宿主隐藏底部导航
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var notifications by remember { mutableStateOf(sampleNotifications) }
    var overlay by remember { mutableStateOf(HomeOverlay.None) }
    LaunchedEffect(overlay) { onFullscreenChange(overlay != HomeOverlay.None) }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    // Home ↔ 子页：与 MainActivity 一致的页面转场（含底层页视差）
    AnimatedContent(
        targetState = overlay,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState != HomeOverlay.None   // 进入子页为前进
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
