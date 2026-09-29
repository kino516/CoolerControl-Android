package com.example.kernelsustyleuikit.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 电源模式集成状态（`GET /power-profiles`）。
 *
 * daemon 通过 D-Bus 读取系统电源模式，并按用户配置把它映射到某个散热模式。
 * **`available` 为空表示系统上没有电源模式 daemon**（例如多数台式机），
 * 此时客户端应把整个功能隐藏，而不是显示一个空卡片。
 *
 * 注意 API 只能**读**当前电源模式并**改映射**——切换电源模式本身由操作系统负责。
 */
data class CcPowerProfiles(
    /** 系统提供的电源模式名，如 `power-saver` / `balanced` / `performance` */
    val available: List<String>,
    /** 当前生效的电源模式，未观测到时为 null */
    val active: String?,
    /** 电源模式名 -> 散热模式 uid；未列出的表示不映射 */
    val modes: Map<String, String>,
) {
    val isSupported: Boolean get() = available.isNotEmpty()

    companion object {
        fun parse(raw: String): CcPowerProfiles? = runCatching {
            val json = JSONObject(raw)
            val available = json.optJSONArray("available")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }.orEmpty()

            val modes = buildMap {
                json.optJSONObject("modes")?.let { obj ->
                    obj.keys().forEach { key ->
                        obj.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                    }
                }
            }

            CcPowerProfiles(
                available = available,
                active = json.optString("active").takeIf { it.isNotBlank() && it != "null" },
                modes = modes,
            )
        }.getOrNull()
    }
}

/**
 * 压力测试的可选目标。
 *
 * - GPU：`id` 是 PCI 槽位之类的选择键，回传时用 `gpu_id`
 * - 磁盘：`id` 就是块设备路径，回传时用 `device_path`（必填）
 */
data class CcStressTarget(
    val id: String,
    val label: String,
    /** 补充信息（磁盘路径与容量、是否独显），由 UI 决定如何展示 */
    val detail: String?,
    val discrete: Boolean = false,
) {
    companion object {
        fun parseList(raw: String, isGpu: Boolean): List<CcStressTarget> = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj: JSONObject = array.optJSONObject(index) ?: return@mapNotNull null
                if (isGpu) {
                    val id = obj.optString("id")
                    if (id.isBlank()) return@mapNotNull null
                    CcStressTarget(
                        id = id,
                        label = obj.optString("name").takeIf { it.isNotBlank() } ?: id,
                        detail = id,
                        discrete = obj.optBoolean("discrete"),
                    )
                } else {
                    val path = obj.optString("device_path")
                    if (path.isBlank()) return@mapNotNull null
                    val sizeGb = obj.optLong("size_bytes") / 1024.0 / 1024.0 / 1024.0
                    CcStressTarget(
                        id = path,
                        label = obj.optString("model").takeIf { it.isNotBlank() } ?: path,
                        detail = "$path · ${"%.0f".format(sizeGb)} GB",
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
