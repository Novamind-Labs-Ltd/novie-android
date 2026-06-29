package com.novamind.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** 网络连接类型。 */
enum class NetworkType { NONE, WIFI, CELLULAR, ETHERNET, OTHER }

/**
 * 网络工具：连通性判断、网络类型查询、连通状态实时监听。
 *
 * 依赖 `ACCESS_NETWORK_STATE` 权限（已在 Manifest 声明）。minSdk 26，统一使用
 * [ConnectivityManager.getNetworkCapabilities] 等新 API。
 */
object NetworkUtils {

    /**
     * 当前是否已联网（有具备上网能力且已校验可达的活动网络）。
     * 用 `VALIDATED` 排除「连上 Wi‑Fi 但无法访问外网」的情况。
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 当前活动网络的类型；无网络返回 [NetworkType.NONE]。 */
    fun currentType(context: Context): NetworkType {
        val cm = context.getSystemService<ConnectivityManager>() ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /** 是否为计费网络（移动数据等）：用于「省流量」分支判断。 */
    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        @Suppress("DEPRECATION")
        return cm.isActiveNetworkMetered
    }

    /**
     * 监听联网状态变化的冷流：订阅时立即发出当前状态，之后随网络可用 / 丢失更新；
     * 取消订阅时自动注销回调。去重，只在真正变化时发射。
     */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        fun hasInternet(network: Network?): Boolean {
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasInternet(network))
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        // 先发当前状态，避免订阅后无变化时收不到初值
        trySend(isOnline(context))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()
}
