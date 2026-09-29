package com.example.kernelsustyleuikit.data.model

/**
 * 报警规则（`GET /alerts` 的 `alerts[]`，即 `AlertDto`）。
 *
 * 注意：`PUT /alerts` 要求回传**完整对象**，缺字段会被服务器以 422 拒绝，
 * 因此更新时必须基于 daemon 返回的原始 JSON 改键，而不是用本模型重新序列化。
 */
data class CcAlert(
    val uid: String,
    val name: String,
    val min: Double,
    val max: Double,
    val enabled: Boolean,
    /** Active / Inactive / Error / WarmUp / Cooldown */
    val state: String,
    /** ISO8601，静默截止时间；null 表示未静默 */
    val silencedUntil: String? = null,
    val channelSourceDeviceUid: String? = null,
    val channelSourceTempName: String? = null,
) {
    val isActive: Boolean get() = state.equals("Active", ignoreCase = true)
    val isError: Boolean get() = state.equals("Error", ignoreCase = true)
}

/** 报警日志（`GET /alerts` 的 `logs[]`） */
data class CcAlertLog(
    val uid: String,
    val name: String,
    val message: String,
    /** Active / Inactive / Error */
    val state: String,
    /** triggered / stillActive / resolved / error / errorResolved / unknown */
    val kind: String? = null,
    val timestamp: String,
    val resolved: Boolean = false,
    val silenced: Boolean = false,
)
