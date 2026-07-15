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

    /**
     * 同步续期钩子：由认证层在启动时注入（见 NovieApplication）。
     * 用 refresh_token **静默**续期，成功回写 [accessToken] 并返回新 token；失败返回 null（并由注入方触发失效登出）。
     * 供 [TokenAuthenticator] 在收到 401 时调用（OkHttp Authenticator 运行在 IO 线程，可阻塞）。
     */
    @Volatile
    var renew: (() -> String?)? = null
}
