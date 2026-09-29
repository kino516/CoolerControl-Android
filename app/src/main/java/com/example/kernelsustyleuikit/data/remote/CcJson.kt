package com.example.kernelsustyleuikit.data.remote

import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.data.model.CcChannelInfo
import com.example.kernelsustyleuikit.data.model.CcChannelSetting
import com.example.kernelsustyleuikit.data.model.CcChannelStats
import com.example.kernelsustyleuikit.data.model.CcChannelStatus
import com.example.kernelsustyleuikit.data.model.CcCurvePoint
import com.example.kernelsustyleuikit.data.model.CcDevice
import com.example.kernelsustyleuikit.data.model.CcDeviceStats
import com.example.kernelsustyleuikit.data.model.CcDeviceStatus
import com.example.kernelsustyleuikit.data.model.CcFunction
import com.example.kernelsustyleuikit.data.model.CcMode
import com.example.kernelsustyleuikit.data.model.CcProfile
import com.example.kernelsustyleuikit.data.model.CcSpeedOptions
import com.example.kernelsustyleuikit.data.model.CcStatus
import com.example.kernelsustyleuikit.data.model.CcTempSource
import com.example.kernelsustyleuikit.data.model.CcTempStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * daemon JSON -> 领域模型。
 *
 * 全部使用 Android 平台内置的 `org.json`，不引入任何序列化依赖；
 * 同时保留原始 `JSONObject` 的能力（报警更新必须整体回传原始对象）。
 */
object CcJson {

    // ---------- 基础取值（区分「缺失/显式 null」与「有值」）----------

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.boolOr(key: String, default: Boolean): Boolean =
        if (has(key) && !isNull(key)) optBoolean(key, default) else default

    private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T?): List<T> =
        (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(block) }

    // ---------- 设备 ----------

    /** `GET /devices` */
    fun parseDevices(body: String): List<CcDevice> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("devices") ?: return emptyList()
        return arr.mapObjects { d ->
            val info = d.optJSONObject("info")

            val channelsObj = info?.optJSONObject("channels")
            val channels = LinkedHashMap<String, CcChannelInfo>()
            channelsObj?.keys()?.forEach { key ->
                val c = channelsObj.optJSONObject(key) ?: return@forEach
                channels[key] = CcChannelInfo(
                    name = key,
                    label = c.stringOrNull("label"),
                    speedOptions = c.optJSONObject("speed_options")?.let { so ->
                        CcSpeedOptions(
                            fixedEnabled = so.boolOr("fixed_enabled", false),
                            minDuty = so.optInt("min_duty", 0),
                            maxDuty = so.optInt("max_duty", 100),
                        )
                    },
                )
            }

            // info.temps 是温度传感器显示名的唯一来源：
            // /status 只返回内部名（sensor1 / temp1），没有 label
            val tempsObj = info?.optJSONObject("temps")
            val temps = LinkedHashMap<String, String>()
            tempsObj?.keys()?.forEach { key ->
                val label = tempsObj.optJSONObject(key)?.stringOrNull("label")
                if (!label.isNullOrBlank()) temps[key] = label
            }

            val driverInfo = info?.optJSONObject("driver_info")
            CcDevice(
                uid = d.optString("uid"),
                name = d.optString("name"),
                // 实测字段名是 type；早期误写成 d_type，导致设备类型恒为空，
                // 连带温度配色的 CPU 区间判定一起失效
                type = d.stringOrNull("type") ?: d.optString("d_type"),
                typeIndex = d.optInt("type_index", 0),
                channels = channels,
                temps = temps,
                driverType = driverInfo?.stringOrNull("drv_type"),
                driverName = driverInfo?.stringOrNull("name"),
                driverVersion = driverInfo?.stringOrNull("version"),
                driverLocations = driverInfo?.optJSONArray("locations")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optString(index).takeIf { it.isNotBlank() }
                    }
                }.orEmpty(),
                model = info?.stringOrNull("model"),
                tempMin = info?.optInt("temp_min").takeIf { info?.has("temp_min") == true },
                tempMax = info?.optInt("temp_max").takeIf { info?.has("temp_max") == true },
            )
        }
    }

    // ---------- 状态 ----------

    /** `GET /status`（取 `status_history` 的最后一条作为当前值） */
    fun parseStatus(body: String): List<CcDeviceStatus> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("devices") ?: return emptyList()
        return arr.mapObjects { d ->
            val history = d.optJSONArray("status_history")
            val latest = if (history != null && history.length() > 0) {
                history.optJSONObject(history.length() - 1)
            } else {
                null
            }
            CcDeviceStatus(uid = d.optString("uid"), status = latest?.let(::parseStatusObject))
        }
    }

    /** SSE `status` 事件的 payload 本身就是 `StatusResponse` */
    fun parseStatusObject(o: JSONObject): CcStatus {
        val channels = o.optJSONArray("channels")?.mapObjects { c ->
            CcChannelStatus(
                name = c.optString("name"),
                duty = c.doubleOrNull("duty"),
                rpm = c.doubleOrNull("rpm")?.toInt(),
                watts = c.doubleOrNull("watts"),
                freq = c.doubleOrNull("freq")?.toInt(),
                pwmMode = c.intOrNull("pwm_mode"),
            )
        } ?: emptyList()

        val temps = o.optJSONArray("temps")?.mapObjects { t ->
            CcTempStatus(name = t.optString("name"), temp = t.optDouble("temp", 0.0))
        } ?: emptyList()

        return CcStatus(
            timestamp = o.optString("timestamp"),
            channels = channels,
            temps = temps,
        )
    }

    /** 从 SSE `status` 事件的完整 JSON 里取出各设备的最新快照 */
    fun parseStatusEvent(data: String): List<CcDeviceStatus> = parseStatus(data)

    // ---------- 报警 ----------

    /** `GET /alerts` -> (规则, 日志) */
    fun parseAlerts(body: String): Pair<List<CcAlert>, List<CcAlertLog>> {
        val root = JSONObject(body)

        val alerts = root.optJSONArray("alerts")?.mapObjects { a ->
            val src = a.optJSONObject("channel_source")
            CcAlert(
                uid = a.optString("uid"),
                name = a.optString("name"),
                min = a.optDouble("min", 0.0),
                max = a.optDouble("max", 0.0),
                enabled = a.boolOr("enabled", true),
                state = parseAlertState(a.opt("state")),
                silencedUntil = a.stringOrNull("silenced_until"),
                channelSourceDeviceUid = src?.stringOrNull("device_uid"),
                channelSourceTempName = src?.stringOrNull("temp_name"),
            )
        } ?: emptyList()

        val logs = root.optJSONArray("logs")?.mapObjects { l ->
            CcAlertLog(
                uid = l.optString("uid"),
                name = l.optString("name"),
                message = l.optString("message"),
                state = parseAlertState(l.opt("state")),
                kind = l.stringOrNull("kind"),
                timestamp = l.optString("timestamp"),
                resolved = l.boolOr("resolved", false),
                silenced = l.boolOr("silenced", false),
            )
        } ?: emptyList()

        return alerts to logs
    }

    /**
     * `AlertState` 可能是字符串（Active/Inactive/Error），
     * 也可能是带时间戳的对象（`{"WarmUp": "..."}` / `{"Cooldown": "..."}`）。
     */
    fun parseAlertState(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "Inactive"
        is String -> value
        is JSONObject -> when {
            value.has("WarmUp") -> "WarmUp"
            value.has("Cooldown") -> "Cooldown"
            else -> "Inactive"
        }
        else -> value.toString()
    }

    /** SSE `alert` 事件推送单条日志 */
    fun parseAlertLogEvent(data: String): CcAlertLog? =
        runCatching { JSONObject(data) }.getOrNull()?.let { l ->
            CcAlertLog(
                uid = l.optString("uid"),
                name = l.optString("name"),
                message = l.optString("message"),
                state = parseAlertState(l.opt("state")),
                kind = l.stringOrNull("kind"),
                timestamp = l.optString("timestamp"),
                resolved = l.boolOr("resolved", false),
                silenced = l.boolOr("silenced", false),
            )
        }

    // ---------- 模式 ----------

    /** `GET /modes` */
    fun parseModes(body: String): List<CcMode> {
        val arr = JSONObject(body).optJSONArray("modes") ?: return emptyList()
        return arr.mapObjects { m ->
            CcMode(uid = m.optString("uid"), name = m.optString("name"))
        }
    }

    /** `GET /modes-active` -> 当前模式 uid */
    fun parseActiveModeUid(body: String): String? =
        runCatching { JSONObject(body).stringOrNull("current_mode_uid") }.getOrNull()

    /** SSE `mode` 事件（`ActiveMode`）-> 当前模式 uid */
    fun parseActiveModeEvent(data: String): String? =
        runCatching { JSONObject(data).stringOrNull("uid") }.getOrNull()

    // ---------- 曲线 ----------

    /** `GET /profiles`，过滤掉内置占位曲线（uid = "0"） */
    fun parseProfiles(body: String): List<CcProfile> {
        val arr = JSONObject(body).optJSONArray("profiles") ?: return emptyList()
        return arr.mapObjects { p ->
            val points = p.optJSONArray("speed_profile")?.let { sp ->
                (0 until sp.length()).mapNotNull { i ->
                    val pair = sp.optJSONArray(i) ?: return@mapNotNull null
                    if (pair.length() < 2) return@mapNotNull null
                    CcCurvePoint(temp = pair.optDouble(0, 0.0), duty = pair.optDouble(1, 0.0).toInt())
                }
            } ?: emptyList()

            val src = p.optJSONObject("temp_source")

            CcProfile(
                uid = p.optString("uid"),
                name = p.optString("name"),
                functionUid = p.optString("function_uid"),
                pType = p.optString("p_type", "Default"),
                speedFixed = p.intOrNull("speed_fixed"),
                speedProfile = points,
                tempMin = p.doubleOrNull("temp_min"),
                tempMax = p.doubleOrNull("temp_max"),
                tempSource = src?.let {
                    CcTempSource(
                        deviceUid = it.optString("device_uid"),
                        tempName = it.optString("temp_name"),
                    )
                },
            )
        }.filter { it.uid != CcProfile.UNMANAGED_UID }
    }

    /** `GET /functions` */
    fun parseFunctions(body: String): List<CcFunction> {
        val arr = JSONObject(body).optJSONArray("functions") ?: return emptyList()
        return arr.mapObjects { f ->
            CcFunction(
                uid = f.optString("uid"),
                name = f.optString("name"),
                fType = f.optString("f_type", "Identity"),
                dutyMinimum = f.optInt("duty_minimum", 0),
                dutyMaximum = f.optInt("duty_maximum", 0),
            )
        }
    }

    // ---------- 通道设置 ----------

    /**
     * `GET /devices/{uid}/settings`
     *
     * 这是判断「通道当前绑定了哪条曲线」的唯一正确来源 ——
     * 用户手动挑曲线属于通道级覆盖，不会写进模式定义。
     */
    fun parseChannelSettings(body: String): List<CcChannelSetting> {
        val arr = JSONObject(body).optJSONArray("settings") ?: return emptyList()
        return arr.mapObjects { s ->
            CcChannelSetting(
                channelName = s.optString("channel_name"),
                profileUid = s.stringOrNull("profile_uid"),
                speedFixed = s.intOrNull("speed_fixed"),
                resetToDefault = s.boolOr("reset_to_default", false),
            )
        }
    }

    // ---------- 统计 ----------

    /** `GET /stats`（daemon 自启动以来的累计值） */
    fun parseStats(body: String): List<CcDeviceStats> {
        val arr = JSONObject(body).optJSONArray("devices") ?: return emptyList()
        return arr.mapObjects { d ->
            CcDeviceStats(
                uid = d.optString("uid"),
                channels = parseStatsMap(d.optJSONObject("channels")),
                temps = parseStatsMap(d.optJSONObject("temps")),
            )
        }
    }

    private fun parseStatsMap(obj: JSONObject?): Map<String, CcChannelStats> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, CcChannelStats>()
        obj.keys().forEach { key ->
            val s = obj.optJSONObject(key) ?: return@forEach
            out[key] = CcChannelStats(
                min = s.optDouble("min", 0.0),
                max = s.optDouble("max", 0.0),
                avg = s.optDouble("avg", 0.0),
                count = s.optInt("count", 0),
            )
        }
        return out
    }
}
