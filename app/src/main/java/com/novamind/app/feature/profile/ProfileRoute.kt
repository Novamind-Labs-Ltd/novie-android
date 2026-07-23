package com.novamind.app.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ProfileStore.load(context) }
    val avatarPath by ProfileStore.avatarPath.collectAsState()

    ProfileScreen(
        name = userName?.takeIf { it.isNotBlank() } ?: "",
        email = userEmail.orEmpty(),
        avatarPath = avatarPath,
        onEditAvatar = onEditAvatar,
        onOpenPermissions = onOpenPermissions,
        onAbout = onAbout,
        onLogout = onLogout,
        modifier = modifier,
    )
}
