package com.novamind.app.data.calendar

import com.novamind.app.common.google.GoogleTokenProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Google Calendar 专用网络栈，与 [com.novamind.app.common.net.NetworkModule] 完全独立：
 * - 目标 host 是 googleapis.com，不能套用业务后端的证书钉定与 Auth0 拦截器；
 * - Authorization 头取自 [GoogleTokenProvider]（Google OAuth token，非 Auth0 token）。
 *
 * token 缺失时不加 Authorization 头，请求会得到 401，由上层提示重新授权。
 */
object GoogleCalendarNetwork {

    private const val BASE_URL = "https://www.googleapis.com/calendar/v3/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = GoogleTokenProvider.accessToken
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    val api: GoogleCalendarApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GoogleCalendarApi::class.java)
    }
}
