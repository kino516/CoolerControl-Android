package com.example.kernelsustyleuikit.data.model

/**
 * 累计统计（`GET /stats`）。
 *
 * 这是 daemon **自启动以来**的累计 min/avg/max，不是按时间段查询的结果 ——
 * 官方 5.x 不提供任何历史时序接口。
 */
data class CcChannelStats(
    val min: Double,
    val max: Double,
    val avg: Double,
    val count: Int,
)

data class CcDeviceStats(
    val uid: String,
    /** 通道指标（转速 / 占空比等）的累计统计 */
    val channels: Map<String, CcChannelStats>,
    /** 温度通道的累计统计 */
    val temps: Map<String, CcChannelStats>,
)
