package com.example.kernelsustyleuikit.data.model

/** 散热模式（`GET /modes`） */
data class CcMode(
    val uid: String,
    val name: String,
)

/** 温度源引用：`temp_name` 是内部名，不是展示用的 label */
data class CcTempSource(
    val deviceUid: String,
    val tempName: String,
)

/**
 * 风扇曲线（`GET /profiles`）。
 *
 * daemon 用 `p_type` 作为判别器：
 * - `Default`  默认（无附加字段）
 * - `Fixed`    固定转速（`speedFixed`）
 * - `Graph`    图形曲线（`speedProfile` 控制点 + `tempMin/tempMax` + `tempSource`）
 * - `Mix`      多条曲线混合
 * - `Overlay`  在成员曲线上叠加偏移
 *
 * 内置占位曲线 `Unmanaged` 的 uid 为 `"0"`，必须过滤掉。
 */
data class CcProfile(
    val uid: String,
    val name: String,
    val functionUid: String,
    val pType: String,
    val speedFixed: Int? = null,
    val speedProfile: List<CcCurvePoint> = emptyList(),
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val tempSource: CcTempSource? = null,
) {
    val isGraph: Boolean get() = pType.equals("Graph", ignoreCase = true)

    companion object {
        /** 内置占位曲线的 uid */
        const val UNMANAGED_UID = "0"
    }
}

/** 曲线控制点：温度 -> 占空比 */
data class CcCurvePoint(
    val temp: Double,
    val duty: Int,
)

/** 曲线绑定的函数（步进 / 迟滞参数），`GET /functions` */
data class CcFunction(
    val uid: String,
    val name: String,
    /** Identity / Standard */
    val fType: String,
    val dutyMinimum: Int,
    val dutyMaximum: Int,
)

/**
 * 通道级已应用设置（`GET /devices/{uid}/settings` 的 `settings[]`）。
 *
 * 同一个通道只会有一种设置生效，判别字段为 `profile_uid` / `speed_fixed` /
 * `reset_to_default` 之一。
 */
data class CcChannelSetting(
    val channelName: String,
    val profileUid: String? = null,
    val speedFixed: Int? = null,
    val resetToDefault: Boolean = false,
)
