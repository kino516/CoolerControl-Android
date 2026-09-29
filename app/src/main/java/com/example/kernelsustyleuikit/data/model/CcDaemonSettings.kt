package com.example.kernelsustyleuikit.data.model

import org.json.JSONObject

/**
 * daemon 全局设置（`GET /settings`）。
 *
 * 只挑选在 APP 里调整是安全的项。刻意**不做**进界面的字段：
 * - `no_init`、`apply_on_boot` 会改变 daemon 启动行为
 * - `allow_unencrypted`、`protocol_header` 涉及连接协议，误改可能导致远程连不上
 * - `compress`、`device_listener_enabled` 属于内部实现细节
 *
 * 写回时仍需整体回传完整对象（daemon 是替换语义），由 Repository 负责。
 */
data class CcDaemonSettings(
    /** 轮询速率（秒），网页端叫「轮询速率」 */
    val pollRate: Double = 1.0,
    /** 启动延迟（秒） */
    val startupDelay: Int = 10,
    /** 隐藏重复设备 */
    val hideDuplicateDevices: Boolean = true,
    /** 传感器自动检测 */
    val sensorsAutoDetect: Boolean = true,
    /** Liquidctl 集成 */
    val liquidctlIntegration: Boolean = false,
    /** 磁盘休眠时暂停读数 */
    val drivetempSuspend: Boolean = true,
) {
    companion object {
        fun parse(raw: String): CcDaemonSettings = runCatching {
            val json = JSONObject(raw)
            CcDaemonSettings(
                pollRate = json.optDouble("poll_rate", 1.0),
                startupDelay = json.optInt("startup_delay", 10),
                hideDuplicateDevices = json.optBoolean("hide_duplicate_devices", true),
                sensorsAutoDetect = json.optBoolean("sensors_auto_detect", true),
                liquidctlIntegration = json.optBoolean("liquidctl_integration", false),
                drivetempSuspend = json.optBoolean("drivetemp_suspend", true),
            )
        }.getOrDefault(CcDaemonSettings())
    }
}
