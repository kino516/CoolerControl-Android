package com.example.kernelsustyleuikit.data.model

/**
 * CoolerControl 设备（对应 `GET /devices` 的 `devices[]`）。
 *
 * 通道与温度传感器的信息都挂在 `info` 下面：
 * - `info.channels`：可调速通道（风扇），带 `speed_options`
 * - `info.temps`：温度传感器，**只有这里带 `label`**
 *
 * 两处的 `label` 都是 daemon 合并过网页端重命名的结果。
 * 注意 `/status` 只返回通道的内部名（`sensor1` / `fan2`），
 * 所以展示名称必须回到本模型来查，否则界面会露出英文内部名。
 */
data class CcDevice(
    val uid: String,
    val name: String,
    /** 设备类型：CPU / GPU / Liquidctl / Hwmon / CustomSensors / ServicePlugin */
    val type: String,
    val typeIndex: Int,
    /** 可调速通道内部名 -> 通道信息 */
    val channels: Map<String, CcChannelInfo>,
    /** 温度传感器内部名 -> 显示名（`info.temps[].label`） */
    val temps: Map<String, String> = emptyMap(),

    // ---- 驱动与硬件信息（`info.driver_info` / `info.model`），仅用于设备详情展示 ----

    /** 驱动来源：Kernel / CoolerControl */
    val driverType: String? = null,
    /** 具体驱动名，如 it87 / sd / spd5118 / CustomSensors */
    val driverName: String? = null,
    /** 驱动版本（通常是内核版本） */
    val driverVersion: String? = null,
    /** sysfs 路径，用于定位硬件 */
    val driverLocations: List<String> = emptyList(),
    /** 硬件型号，如 Micron MTFDKBA512TFH */
    val model: String? = null,
    val tempMin: Int? = null,
    val tempMax: Int? = null,
) {
    /**
     * 通道显示名：依次尝试通道 label -> 温度 label -> 内部名。
     *
     * 风扇走第一个分支，温度传感器走第二个。
     */
    fun channelDisplayName(channelName: String): String =
        channels[channelName]?.label?.takeIf { it.isNotBlank() }
            ?: temps[channelName]?.takeIf { it.isNotBlank() }
            ?: channelName
}

data class CcChannelInfo(
    val name: String,
    val label: String?,
    /** 非空表示该通道可调速（风扇） */
    val speedOptions: CcSpeedOptions?,
)

data class CcSpeedOptions(
    val fixedEnabled: Boolean,
    val minDuty: Int,
    val maxDuty: Int,
)

/** 一次采样快照（`GET /status` 与 SSE `status` 事件共用） */
data class CcStatus(
    val timestamp: String,
    val channels: List<CcChannelStatus>,
    val temps: List<CcTempStatus>,
)

/** 通道指标：风扇转速 / 占空比 / 功耗 / 频率 */
data class CcChannelStatus(
    val name: String,
    val duty: Double? = null,
    val rpm: Int? = null,
    val watts: Double? = null,
    val freq: Int? = null,
    val pwmMode: Int? = null,
) {
    /** 判定为风扇通道的唯一依据：有转速读数 */
    val isFan: Boolean get() = rpm != null
}

data class CcTempStatus(
    val name: String,
    val temp: Double,
)

/** `GET /status` 的 `devices[]` 项 */
data class CcDeviceStatus(
    val uid: String,
    /** 最新一次快照（`status_history` 的末项） */
    val status: CcStatus?,
)
