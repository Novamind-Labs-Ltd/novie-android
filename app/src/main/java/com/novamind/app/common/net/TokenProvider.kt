package com.novamind.app.common.net

/**
 * 内存态 access token 提供者。
 *
 * OkHttp 拦截器是同步的，不能在其中调用会弹生物识别的挂起取凭证方法，
 * 因此由认证层（登录成功 / 静默取凭证后）把 access token 写到这里，
 * [AuthInterceptor] 同步读取并加到 Authorization 头。登出时置空。
 */
object TokenProvider {
    @Volatile
    var accessToken: String? = null
}
