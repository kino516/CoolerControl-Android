package com.example.kernelsustyleuikit.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.templateApp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * CoolerControl 相关的展示格式化。
 *
 * [time24] 与 [frequencyPrecision] 来自网页端的 `/settings/ui`，由 Repository 在
 * 设置变化时写入。用 `mutableStateOf` 而不是普通 var，是为了让已经渲染出来的
 * 时间文本能跟着重组，而不必等下一次数据刷新。
 */
object CcFormat {

    /** 24 小时制（网页端 `time24`） */
    var time24 by mutableStateOf(false)

    /** 频率小数位（网页端 `frequencyPrecision`） */
    var frequencyPrecision by mutableStateOf(1)

    /** 图表线宽档位（网页端 `chartLineScale`），1–5 档 */
    var chartLineScale by mutableStateOf(2)

    /** 温度：42.0 -> "42°" */
    fun temperature(value: Double?): String =
        value?.let { "${it.roundToInt()}°" } ?: "--"

    /** 温度（带单位）：42.0 -> "42.0 °C" */
    fun temperatureWithUnit(value: Double?): String =
        value?.let { String.format("%.1f °C", it) } ?: "--"

    /** 占空比：48.6 -> "49%" */
    fun duty(value: Double?): String =
        value?.let { "${it.roundToInt()}%" } ?: "--"

    /** 频率：3200 -> "3.2 GHz"，800 -> "800 MHz" */
    fun frequency(mhz: Int?): String {
        if (mhz == null) return "--"
        return if (mhz >= 1000) {
            String.format("%.${frequencyPrecision.coerceIn(0, 3)}f GHz", mhz / 1000.0)
        } else {
            "$mhz MHz"
        }
    }

    /** 把 ISO8601 时间戳转成相对时间文案 */
    fun relativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val instant = parseInstant(iso) ?: return ""
        val minutes = (System.currentTimeMillis() - instant.toEpochMilli()) / 60_000
        val app = templateApp
        return when {
            minutes < 1 -> app.getString(R.string.cc_time_now)
            minutes < 60 -> app.getString(R.string.cc_time_minutes, minutes.toInt())
            minutes < 60 * 24 -> app.getString(R.string.cc_time_hours, (minutes / 60).toInt())
            else -> app.getString(R.string.cc_time_days, (minutes / (60 * 24)).toInt())
        }
    }

    /** 绝对时间（用于报警记录、静默截止时间等），跟随 [time24] 切换 12/24 小时制 */
    fun shortDateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val instant = parseInstant(iso) ?: return ""
        val pattern = if (time24) "MM-dd HH:mm" else "MM-dd hh:mm a"
        return runCatching {
            DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }.getOrDefault("")
    }

    private fun parseInstant(iso: String): Instant? =
        runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { Instant.parse(iso) }
            .recoverCatching {
                java.time.LocalDateTime.parse(iso)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }
            .getOrNull()
}
