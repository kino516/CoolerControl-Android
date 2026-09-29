package com.example.kernelsustyleuikit.data.remote

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.templateApp

/**
 * 网络绑定：内网端点走 Wi-Fi。
 *
 * 私网地址显式绑定 Wi-Fi 网络的 socketFactory，避免被系统路由到移动数据。
 */
object CcNetworkBinder {

    /**
     * Android 16 (API 36) 起，访问局域网需要该运行时权限。
     * 缺失时系统会静默丢弃内网流量（表现为连接超时，而非权限异常）。
     */
    const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** 该权限从 API 36 开始存在 */
    const val LOCAL_NETWORK_MIN_API = 36

    private val private172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.")

    /** 是否已获得局域网访问权限（低版本系统视为已具备） */
    fun hasLocalNetworkPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < LOCAL_NETWORK_MIN_API) return true
        return ContextCompat.checkSelfPermission(
            templateApp,
            LOCAL_NETWORK_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 地址是否指向局域网 / 本机 */
    fun isPrivateAddress(url: String): Boolean {
        val host = url.substringAfter("://", url)
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            private172.containsMatchIn(host)
    }

    /** 当前可用的 Wi-Fi 网络；没有则返回 null */
    fun wifiNetwork(): Network? {
        val manager = templateApp.getSystemService(ConnectivityManager::class.java) ?: return null
        return runCatching {
            manager.allNetworks.firstOrNull { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@firstOrNull false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }.getOrNull()
    }

    /**
     * 内网端点 -> 视情况绑定 Wi-Fi；其余地址 -> 交给系统默认网络。
     *
     * [kind] 来自登录页/服务器配置里的「内网 / 外网」输入框：填在内网框的地址即使
     * 是域名（如 `cc.lan`）也按内网处理，否则会被 [isPrivateAddress] 判成外网而漏掉绑定。
     *
     * **默认网络已经是 Wi-Fi 时返回 null**（不绑定）：
     * 实测在 MIUI / Android 17 上，对已走 Wi-Fi 的地址再显式绑定 socketFactory，
     * 反而会导致连内网时 SocketTimeoutException（同地址浏览器却能正常访问）。
     * 只有默认网络不是 Wi-Fi（例如被路由到移动数据）时才需要绑定。
     */
    fun networkFor(url: String, kind: CcEndpointKind? = null): Network? {
        if (kind != CcEndpointKind.Lan && !isPrivateAddress(url)) return null
        val manager = templateApp.getSystemService(ConnectivityManager::class.java) ?: return null

        val activeCapabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        if (activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return null
        }
        return wifiNetwork()
    }
}
