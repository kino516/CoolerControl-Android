package com.example.kernelsustyleuikit.data.model

import org.json.JSONObject

/**
 * 用户在网页端做过的重命名（`GET /settings/overrides`）。
 *
 * **这是名称的唯一权威来源。** 实测 `/devices` 与 `/status` **都不会**返回重命名后的
 * 通道名——它们给的是 `temp1` / `sensor1` / `fan2` 这类内部名，客户端必须自己合并：
 *
 * ```json
 * { "devices": {
 *     "<device_uid>": {
 *       "name": "IT8613控制器",          // 设备名覆盖
 *       "device_name": "…",              // 部分设备用这个键
 *       "channels": { "fan2": { "label": "CPU风扇" } }
 *     } } }
 * ```
 */
data class CcOverrides(
    /** deviceUid -> 设备显示名 */
    val deviceNames: Map<String, String>,
    /** "deviceUid|channel" -> 通道显示名 */
    val channelLabels: Map<String, String>,
) {
    fun deviceName(uid: String): String? = deviceNames[uid]

    fun channelLabel(deviceUid: String, channel: String): String? =
        channelLabels[key(deviceUid, channel)]

    /** 通道显示名：优先网页端重命名，否则退回内部名 */
    fun channelLabelOr(deviceUid: String, channel: String): String =
        channelLabel(deviceUid, channel) ?: channel

    /** 设备显示名：优先网页端重命名 */
    fun deviceNameOr(uid: String, fallback: String): String = deviceName(uid) ?: fallback

    companion object {
        val EMPTY = CcOverrides(emptyMap(), emptyMap())

        fun key(deviceUid: String, channel: String) = "$deviceUid|$channel"

        fun parse(raw: String): CcOverrides = runCatching {
            val devices = JSONObject(raw).optJSONObject("devices") ?: return@runCatching EMPTY
            val names = mutableMapOf<String, String>()
            val labels = mutableMapOf<String, String>()

            devices.keys().forEach { uid ->
                val entry = devices.optJSONObject(uid) ?: return@forEach
                // name 与 device_name 都出现过，谁非空用谁
                val name = sequenceOf(entry.optString("name"), entry.optString("device_name"))
                    .firstOrNull { it.isNotBlank() && it != "null" }
                name?.let { names[uid] = it }

                entry.optJSONObject("channels")?.let { channels ->
                    channels.keys().forEach { channel ->
                        val label = channels.optJSONObject(channel)?.optString("label")
                        if (!label.isNullOrBlank() && label != "null") {
                            labels[key(uid, channel)] = label
                        }
                    }
                }
            }
            CcOverrides(names, labels)
        }.getOrDefault(EMPTY)
    }
}
