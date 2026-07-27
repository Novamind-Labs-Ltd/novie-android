package com.novamind.app.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novamind.app.common.profile.AvatarViewerScreen
import com.novamind.app.common.profile.ProfileStore

/**
 * Profile 有状态路由：连接 [ProfileStore] 头像，转发账号/设置回调给无状态 [ProfileScreen]。
 * 昵称/邮箱由宿主（MainActivity 的 UserSession）注入。
 */
@Composable
fun ProfileRoute(
    userName: String? = null,
    userEmail: String? = null,
    onEditAvatar: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onAbout: () -> Unit = {},
    onLogout: () -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ProfileStore.load(context) }
    val avatarPath by ProfileStore.avatarPath.collectAsStateWithLifecycle()
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showAvatarEditor by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showAccount, showAvatarEditor) {
        onFullscreenChange(showAccount || showAvatarEditor)
    }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ProfileScreen(
            name = userName?.takeIf { it.isNotBlank() } ?: "",
            email = userEmail.orEmpty(),
            avatarPath = avatarPath,
            onEditAvatar = { showAccount = true },
            onOpenPermissions = onOpenPermissions,
            onAbout = onAbout,
            onLogout = onLogout,
            modifier = Modifier.fillMaxSize(),
        )

        if (showAccount) {
            AccountScreen(
                name = userName.orEmpty(),
                email = userEmail.orEmpty(),
                avatarPath = avatarPath,
                onBack = { showAccount = false },
                onEdit = {
                    onEditAvatar()
                    showAvatarEditor = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showAvatarEditor) {
            AvatarViewerScreen(
                avatarPath = avatarPath,
                onBack = { showAvatarEditor = false },
                onAvatarPicked = { path -> ProfileStore.setLocalAvatar(context, path) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
