package com.novamind.app.feature.auth

/**
 * 认证界面状态（单一不可变来源）。
 *
 * @property isCheckingSession 启动时正在检查本地会话（决定显示登录页还是主界面）
 * @property isLoading 正在执行登录 / 登出
 * @property isAuthenticated 是否已登录
 * @property needsBiometricUnlock 本地有会话且开启了指纹登录，等待指纹解锁
 * @property biometricAvailable 设备是否支持生物识别（已录入）
 * @property biometricEnabled 用户是否开启了「指纹登录」
 * @property errorMessage 一次性错误提示，消费后置空
 *
 * 注：用户档案（昵称/邮箱/头像）不再放在认证 UI 状态里，改由全局
 * [com.novamind.app.common.session.UserSessionManager] 单一数据源提供，各页从 session 派生。
 */
data class AuthUiState(
    val isCheckingSession: Boolean = true,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val needsBiometricUnlock: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val errorMessage: String? = null,
)
