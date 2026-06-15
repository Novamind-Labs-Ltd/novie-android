package com.novamind.app.feature.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.result.Credentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 认证 ViewModel：持有单一 [AuthUiState]，对外暴露登录/登出/检查会话。
 * 登录登出需要 Activity（系统浏览器从 Activity 拉起），以参数瞬时传入，不持有引用。
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    /** 启动时检查是否已有有效会话。 */
    private fun checkSession() {
        viewModelScope.launch {
            if (authManager.hasValidCredentials()) {
                runCatching { authManager.getCredentials() }
                    .onSuccess { creds ->
                        _uiState.update {
                            it.copy(
                                isCheckingSession = false,
                                isAuthenticated = true,
                                userName = creds.nameOrNull(),
                                userEmail = creds.emailOrNull(),
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isCheckingSession = false, isAuthenticated = false) }
                    }
            } else {
                _uiState.update { it.copy(isCheckingSession = false, isAuthenticated = false) }
            }
        }
    }

    fun login(activity: Activity) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authManager.login(activity) }
                .onSuccess { creds ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            userName = creds.nameOrNull(),
                            userEmail = creds.emailOrNull(),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "登录失败") }
                }
        }
    }

    fun logout(activity: Activity) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authManager.logout(activity) }
                .onSuccess {
                    _uiState.update {
                        AuthUiState(isCheckingSession = false, isAuthenticated = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "登出失败") }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

// 从 id_token（JWT）中解析用户信息，缺失时安全降级为 null。
// 直接解 JWT payload，避免依赖特定 SDK 版本的 Credentials.user。
private fun Credentials.nameOrNull(): String? = idTokenClaim("name")
private fun Credentials.emailOrNull(): String? = idTokenClaim("email")

private fun Credentials.idTokenClaim(key: String): String? = runCatching {
    val parts = idToken.split(".")
    if (parts.size < 2) return null
    val json = String(
        android.util.Base64.decode(
            parts[1],
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        ),
        Charsets.UTF_8,
    )
    org.json.JSONObject(json).optString(key).takeIf { it.isNotBlank() }
}.getOrNull()
