package com.example.kernelsustyleuikit.data.session

import com.example.kernelsustyleuikit.data.model.CcEndpoint
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.model.CcProbeResult
import com.example.kernelsustyleuikit.data.remote.CcApiClient
import com.example.kernelsustyleuikit.data.remote.CcNetworkBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 多端点并行探测与择优。
 *
 * 排序规则（实测结论）：**可达优先 -> 内网优先 -> 延迟低优先**。
 * 探测本身不建立会话，因此使用独立的短超时客户端，不碰 CookieJar。
 */
object CcEndpointProbe {

    private const val TIMEOUT_MS = 1500L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun probeAll(endpoints: List<CcEndpoint>): List<CcProbeResult> = coroutineScope {
        endpoints.map { endpoint -> async(Dispatchers.IO) { probe(endpoint) } }.awaitAll()
    }

    suspend fun probe(endpoint: CcEndpoint): CcProbeResult = withContext(Dispatchers.IO) {
        val url = CcApiClient.normalizeEndpoint(endpoint.url)?.toHttpUrlOrNull()
            ?: return@withContext CcProbeResult(endpoint, reachable = false, latencyMs = -1L)

        val startedAt = System.currentTimeMillis()

        // 内网地址显式绑定 Wi-Fi：否则系统可能把探测请求路由到移动数据，
        // 结果是「明明连着 Wi-Fi 却提示内网不可达」。
        val network = CcNetworkBinder.networkFor(endpoint.url, endpoint.kind)
        val boundClient = if (network != null) {
            client.newBuilder().socketFactory(network.socketFactory).build()
        } else {
            client
        }

        var reachable = attempt(boundClient, url)
        // 绑定网络失败时回退到系统默认网络（例如 Wi-Fi 刚好断开）
        if (!reachable && network != null) {
            reachable = attempt(client, url)
        }

        CcProbeResult(
            endpoint = endpoint,
            reachable = reachable,
            latencyMs = System.currentTimeMillis() - startedAt,
        )
    }

    private fun attempt(httpClient: OkHttpClient, url: okhttp3.HttpUrl): Boolean =
        runCatching {
            val request = Request.Builder()
                .url(url.newBuilder().addPathSegment("handshake").build())
                .get()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)

    /** 返回最优端点；全部不可达时返回 null */
    fun selectBest(results: List<CcProbeResult>): CcProbeResult? =
        results.asSequence()
            .filter { it.reachable }
            .sortedWith(
                compareBy(
                    { if (it.endpoint.kind == CcEndpointKind.Lan) 0 else 1 },
                    { it.latencyMs },
                )
            )
            .firstOrNull()
}
