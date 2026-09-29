package com.example.kernelsustyleuikit.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 网页端的界面设置（`GET /settings/ui`）。
 *
 * 这个接口返回的是一整个大对象，包含仪表盘、菜单顺序、主题等大量内容。
 * 这里只抽出 APP 用得到的字段；**写回时必须把原始 JSON 整体回传**
 * （daemon 是整体替换语义，缺字段会被重置），所以解析结果里不保留 raw，
 * 由 Repository 单独缓存原文。
 */
data class CcUiSettings(
    /** 24 小时制；false 时用 12 小时制 */
    val time24: Boolean = false,
    /** 图表线宽档位 */
    val chartLineScale: Int = 2,
    /** 频率小数位 */
    val frequencyPrecision: Int = 1,
    val startupPage: String? = null,
    val eyeCandy: Boolean = true,
    /** 压力测试后端：built_in / stress_ng */
    val cpuStressBackend: String = STRESS_BUILT_IN,
    val gpuStressBackend: String = STRESS_BUILT_IN,
    val ramStressBackend: String = STRESS_BUILT_IN,
    val driveStressBackend: String = STRESS_BUILT_IN,
    /** 通道自定义颜色："deviceUid|channel" -> "#RRGGBB" */
    val channelColors: Map<String, String> = emptyMap(),
    /**
     * 主菜单顺序（取自 `menuOrder[].id`）。
     *
     * **设备排序的真正来源**：网页端拖拽主菜单得到的结果就存在这里。它是一个
     * 混合列表，既含设备 uid，也含 `dashboards` / `modes` / `profiles` / `alerts`
     * 这些固定菜单项，所以按 uid 查排名时未命中的项自然落到末尾。
     *
     * 注意不要误用 `devices` 数组 —— 那只是设备清单（且含已移除的历史项），
     * 它的顺序与菜单排序无关。
     */
    val menuOrder: List<String> = emptyList(),
    /**
     * 被设为「主页」的仪表盘 —— 网页端打开时直接显示的那个多通道图表。
     *
     * `homeDashboard` 字段本身只是个 uid，需要到 `dashboards[]` 里取对应项；
     * 未配置或指向已被删除的仪表盘时为 null，此时主页不显示这张卡片。
     */
    val homeDashboard: CcDashboard? = null,
) {
    /** 指定通道在网页端配置的颜色，未配置时为 null */
    fun colorFor(deviceUid: String, channel: String): String? =
        channelColors[channelKey(deviceUid, channel)]

    fun stressBackendFor(kind: String): String = when (kind) {
        "cpu" -> cpuStressBackend
        "gpu" -> gpuStressBackend
        "ram" -> ramStressBackend
        "drive" -> driveStressBackend
        else -> STRESS_BUILT_IN
    }

    companion object {
        const val STRESS_BUILT_IN = "built_in"
        const val STRESS_NG = "stress_ng"

        fun channelKey(deviceUid: String, channel: String) = "$deviceUid|$channel"

        /** 按 `homeDashboard` 的 uid 到 `dashboards[]` 里取对应那一项 */
        private fun parseHomeDashboard(json: JSONObject): CcDashboard? {
            val homeUid = json.optString("homeDashboard")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: return null
            val array = json.optJSONArray("dashboards") ?: return null

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("uid") == homeUid) {
                    return CcDashboard.parse(item.toString())
                }
            }
            return null
        }

        fun parse(raw: String): CcUiSettings = runCatching {
            val json = JSONObject(raw)
            CcUiSettings(
                time24 = json.optBoolean("time24", false),
                chartLineScale = json.optInt("chartLineScale", 2),
                frequencyPrecision = json.optInt("frequencyPrecision", 1),
                startupPage = json.optString("startupPage").takeIf { it.isNotBlank() && it != "null" },
                eyeCandy = json.optBoolean("eyeCandy", true),
                cpuStressBackend = json.optString("cpuStressBackend").ifBlank { STRESS_BUILT_IN },
                gpuStressBackend = json.optString("gpuStressBackend").ifBlank { STRESS_BUILT_IN },
                ramStressBackend = json.optString("ramStressBackend").ifBlank { STRESS_BUILT_IN },
                driveStressBackend = json.optString("driveStressBackend").ifBlank { STRESS_BUILT_IN },
                channelColors = parseChannelColors(json),
                menuOrder = json.optJSONArray("menuOrder")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
                    }
                }.orEmpty(),
                homeDashboard = parseHomeDashboard(json),
            )
        }.getOrDefault(CcUiSettings())

        /**
         * 从 `deviceSettings[].sensorAndChannelSettings[]` 里收集 `userColor`。
         *
         * 每个设备的 `names[]` 与该设备的 `sensorAndChannelSettings[]` **按下标一一对应**，
         * 所以用下标把通道名和颜色配起来。
         */
        private fun parseChannelColors(json: JSONObject): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            val deviceUids = json.optJSONArray("devices") ?: return result
            val deviceSettings = json.optJSONArray("deviceSettings") ?: return result

            for (i in 0 until minOf(deviceUids.length(), deviceSettings.length())) {
                val uid = deviceUids.optString(i).takeIf { it.isNotBlank() } ?: continue
                val entry = deviceSettings.optJSONObject(i) ?: continue
                val names: JSONArray = entry.optJSONArray("names") ?: continue
                val settings: JSONArray = entry.optJSONArray("sensorAndChannelSettings") ?: continue

                for (j in 0 until minOf(names.length(), settings.length())) {
                    val channel = names.optString(j).takeIf { it.isNotBlank() } ?: continue
                    val color = settings.optJSONObject(j)?.optString("userColor")
                    if (!color.isNullOrBlank() && color != "null") {
                        result[channelKey(uid, channel)] = color
                    }
                }
            }
            return result
        }
    }
}
