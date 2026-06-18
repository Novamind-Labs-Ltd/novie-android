package com.novamind.app.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 登录页（有状态）：连接 [AuthViewModel] 与无状态的 [LoginScreen]，
 * 并把当前 Activity 传给 ViewModel 用于拉起系统浏览器。
 */
@Composable
fun LoginRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LoginScreen(
        uiState = uiState,
        onLogin = { context.findActivity()?.let(viewModel::login) },
        onSkipLogin = viewModel::loginAsGuest,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

/** 从 Compose 的 Context 中解出宿主 Activity。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
