package com.example.kernelsustyleuikit.ui.util

import androidx.compose.ui.graphics.Color

/**
 * 温度五档配色（PRD FR-6.3）。
 *
 * 最佳工作区间按设备类型区分：
 * - CPU：**30 – 50 ℃**
 * - 机械硬盘（设备名含 WDC/WD/WUH/HDD/硬盘/机械 等）：**25 – 45 ℃**
 * - 其他：20 – 65 ℃
 *
 * 区间内外都要有可辨的颜色，因此以「最佳区间」为中心做**对称**分档：
 * 明显偏低 -> 深青、略低 -> 蓝、区间内 -> 绿、略高 -> 橙、明显偏高 -> 红。
 *
 * 容差按区间宽度的固定比例推算，而不是写死温度值 —— CPU 的 20 ℃ 区间与
 * 「其他」的 45 ℃ 区间若共用同一个容差，后者的橙档会过早触发。
 */
object CcTemperatureStyle {

    /** 明显偏低（可能是传感器异常或环境过冷） */
    val DeepBlue = Color(0xFF00BCD4)
    /** 略低于最佳区间（通常无害，仅作提示） */
    val Blue = Color(0xFF2196F3)
    /** 处于最佳工作区间 */
    val Green = Color(0xFF36D167)
    /** 略高于最佳区间 */
    val Amber = Color(0xFFFFC107)
    /** 明显超出最佳区间 */
    val Red = Color(0xFFF72727)

    /** 容差 = 最佳区间宽度 × 该比例 */
    private const val TOLERANCE_RATIO = 0.3

    private val hddKeywords = listOf("wdc", "wd", "wuh", "hdd", "硬盘", "机械", "disk")

    data class Range(val low: Double, val high: Double) {
        val tolerance: Double get() = (high - low) * TOLERANCE_RATIO
    }

    fun rangeFor(deviceType: String, deviceName: String): Range {
        val type = deviceType.lowercase()
        val name = deviceName.lowercase()
        return when {
            type.contains("cpu") -> Range(30.0, 50.0)
            hddKeywords.any { name.contains(it) } -> Range(25.0, 45.0)
            else -> Range(20.0, 65.0)
        }
    }

    fun colorFor(temp: Double?, deviceType: String, deviceName: String): Color {
        if (temp == null) return Amber
        val range = rangeFor(deviceType, deviceName)
        val tolerance = range.tolerance
        return when {
            temp < range.low - tolerance -> DeepBlue
            temp < range.low -> Blue
            temp <= range.high -> Green
            temp <= range.high + tolerance -> Amber
            else -> Red
        }
    }

    /** 是否处于最佳工作区间（用于「最佳」标记） */
    fun isOptimal(temp: Double?, deviceType: String, deviceName: String): Boolean {
        if (temp == null) return false
        val range = rangeFor(deviceType, deviceName)
        return temp in range.low..range.high
    }
}
