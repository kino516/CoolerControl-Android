package com.example.kernelsustyleuikit.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 一帧 SSE 数据。
 *
 * 注意事件名是**单数**（`status` / `log` / `mode` / `alert` / `notification` / `health`），
 * 而 `events` 查询参数用**复数**子流名（`status,logs,modes,alerts,notifications,health`）——
 * 两者不一致，写错会直接被服务器以 HTTP 400 拒绝。
 */
data class CcSseFrame(
    val event: String,
    val data: String,
)

/**
 * 用项目已有的 OkHttp 手写 SSE 解析，不引入 `okhttp-sse` 依赖。
 *
 * 传进来的 [client] 必须把读超时设为 0，否则 OkHttp 会主动断开长连接。
 * 断线重连与退避由上层（Repository）负责，这里只负责「一次连接」的流式读取。
 *
 * 需要提前终止连接时调用 [cancel] —— 协程取消**无法**中断阻塞中的 socket 读。
 */
class CcSseClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    /** 逗号分隔的**复数**子流名 */
    private val events: String = DEFAULT_EVENTS,
) {

    /**
     * 当前正在执行的请求。
     *
     * 协程被 cancel 时，阻塞在 `readUtf8Line()` 上的线程不会收到任何信号
     * （OkHttp 的 socket 读也不响应 `Thread.interrupt`），必须显式 cancel
     * 才能让它立刻以 IOException 返回。
     */
    @Volatile
    private var currentCall: Call? = null

    /** 中断当前连接：会让阻塞中的读取立即抛 IOException 返回 */
    fun cancel() {
        currentCall?.cancel()
    }

    fun stream(): Flow<CcSseFrame> = flow {
        var currentEvents = events
        var downgraded = false

        while (true) {
            val url = baseUrl.newBuilder()
                .addPathSegment("sse")
                .addQueryParameter("events", currentEvents)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val call = client.newCall(request)
            currentCall = call
            try {
                val response = call.execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    // 服务器对 events 参数校验严格：混入未支持的子流名会直接 400。
                    // 此时去掉可选子流（health）重试一次，避免整条实时流不可用。
                    if (code == 400 && !downgraded && currentEvents.contains(HEALTH_EVENT)) {
                        currentEvents = currentEvents.replace(",$HEALTH_EVENT", "")
                        downgraded = true
                        continue
                    }
                    throw IOException("SSE 连接失败：HTTP $code")
                }

                response.use { resp ->
                    val source = resp.body.source()

                    var eventName: String? = null
                    val data = StringBuilder()

                    while (currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break

                        when {
                            // 空行 = 一帧结束
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) {
                                    emit(CcSseFrame(eventName ?: "message", data.toString()))
                                }
                                eventName = null
                                data.setLength(0)
                            }
                            // 以冒号开头是注释 / 心跳
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") -> eventName = line.substring(6).trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substring(5).trim())
                            }
                            else -> Unit
                        }
                    }
                }
            } finally {
                // 只清理自己注册的那次，避免覆盖后续循环新建的 call
                if (currentCall === call) currentCall = null
            }
            break
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** 实测可用的复数子流名组合 */
        const val DEFAULT_EVENTS = "status,logs,modes,alerts,notifications,health"

        /** 可选子流：若服务器不接受，连接会 400，此时自动降级去掉它 */
        private const val HEALTH_EVENT = "health"
    }
}
