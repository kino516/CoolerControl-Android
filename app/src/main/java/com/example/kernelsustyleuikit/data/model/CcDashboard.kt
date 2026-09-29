package com.example.kernelsustyleuikit.data.model

import org.json.JSONObject

/**
 * 网页端配置的仪表盘（`/settings/ui` 的 `dashboards[]`）。
 *
 * `homeDashboard` 字段指向其中被设为「主页」的那一个 —— 也就是网页端打开时
 * 直接看到的那个多通道图表。它自带图表类型、时间范围、各类型量程，
 * 以及一组选中的数据源。
 */
data class CcDashboard(
    val uid: String,
    val name: String,
    /** 如 `Time Chart` */
    val chartType: String,
    val timeRangeSeconds: Int,
    /** 选中的数据源，顺序与网页端一致 */
    val channels: List<CcDashboardChannel>,
) {
    companion object {
        fun parse(raw: String): CcDashboard? = runCatching {
            val json = JSONObject(raw)
            val uid = json.optString("uid").takeIf { it.isNotBlank() }
                ?: return@runCatching null

            CcDashboard(
                uid = uid,
                name = json.optString("name").takeIf { it.isNotBlank() } ?: uid,
                chartType = json.optString("chartType"),
                timeRangeSeconds = json.optInt("timeRangeSeconds", 300),
                channels = json.optJSONArray("deviceChannelNames")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        val deviceUid = item.optString("deviceUID")
                        val channel = item.optString("channelName")
                        if (deviceUid.isBlank() || channel.isBlank()) {
                            null
                        } else {
                            CcDashboardChannel(deviceUid, channel)
                        }
                    }
                }.orEmpty(),
            )
        }.getOrNull()
    }
}

/** 仪表盘里的一个数据源：设备 uid + 通道内部名 */
data class CcDashboardChannel(
    val deviceUid: String,
    val channelName: String,
) {
    /** 与 `CcRepository.historyFor` 使用同一个键格式 */
    val key: String get() = "$deviceUid|$channelName"
}
