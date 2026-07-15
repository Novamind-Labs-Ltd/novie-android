package com.novamind.app.common.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局「会话已失效」信号（进程级）。用于把**底层探测到的鉴权失效**（refresh_token 续期失败、
 * 或 `/me` 返回 40101）上抛给 UI 认证层，从而真正弹回登录页。
 *
 * 认证层（[com.novamind.app.feature.auth.AuthViewModel]）订阅 [forceLogout]，收到即清本地凭证并置未登录。
 * 与「用户主动登出」区分：主动登出直接走 AuthViewModel，不经此信号，避免回环。
 */
object AuthSessionSignal {

    private val _forceLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 会话失效事件流；订阅方收到即应强制登出到登录页。 */
    val forceLogout: SharedFlow<Unit> = _forceLogout.asSharedFlow()

    /** 通知会话已失效（续期失败 / 鉴权过期）。幂等：多次触发只驱动一次登出。 */
    fun notifySessionExpired() {
        _forceLogout.tryEmit(Unit)
    }
}
