package com.example.kernelsustyleuikit.ui.screen.cooling

import androidx.compose.runtime.Immutable
import com.example.kernelsustyleuikit.data.model.CcMode
import com.example.kernelsustyleuikit.data.model.CcPowerProfiles
import com.example.kernelsustyleuikit.data.model.CcProfile
import com.example.kernelsustyleuikit.data.model.CcSpeedOptions
import com.example.kernelsustyleuikit.data.model.CcStressTarget

/** 冷却页区块 id */
object CoolingSection {
    const val MODES = "modes"
    const val PROFILES = "profiles"
    const val FANS = "fans"
    const val STRESS = "stress"
    const val POWER = "power"

    /** 设备重命名已移到监控页；电源模式在系统不支持时自动隐藏 */
    val all = listOf(MODES, PROFILES, FANS, STRESS, POWER)
}

@Immutable
data class CoolingUiState(
    val modes: List<CcMode> = emptyList(),
    val activeModeUid: String? = null,
    val profiles: List<CcProfile> = emptyList(),
    val hiddenCurves: Set<String> = emptySet(),
    val fans: List<CoolingFanUi> = emptyList(),
    val hiddenFans: Set<String> = emptySet(),
    val sections: List<String> = CoolingSection.all,
    val editMode: Boolean = false,
    /** 正在运行的压力测试类型（cpu / gpu / ram / drive） */
    val runningStress: Set<String> = emptySet(),
    /** 可选的压力测试目标 */
    val stressGpus: List<CcStressTarget> = emptyList(),
    val stressDrives: List<CcStressTarget> = emptyList(),
    /** 已选定的目标；未选表示对所有目标施压 */
    val stressGpuId: String? = null,
    val stressDriveId: String? = null,
    /** 当前选中的压力测试类型；点类型只选中，按「开始测试」才真正下发 */
    val selectedStressKind: String? = null,
    /** 电源模式集成状态；null 或 available 为空表示系统不支持，卡片应隐藏 */
    val powerProfiles: CcPowerProfiles? = null,
    /** deviceUid -> 设备名，用于把曲线的温度源显示成人话 */
    val deviceNames: Map<String, String> = emptyMap(),
    val channelLabels: Map<String, String> = emptyMap(),
) {
    val visibleProfiles: List<CcProfile>
        get() = profiles.filter { it.uid !in hiddenCurves }

    val visibleFans: List<CoolingFanUi>
        get() = fans.filterNot { "${it.deviceUid}|${it.channel}" in hiddenFans }

    val activeMode: CcMode?
        get() = modes.firstOrNull { it.uid == activeModeUid }

    /** 曲线温度源显示名：设备名 · 通道名 */
    fun tempSourceLabel(profile: CcProfile): String? {
        val source = profile.tempSource ?: return null
        val device = deviceNames[source.deviceUid] ?: source.deviceUid
        val channel = channelLabels["${source.deviceUid}|${source.tempName}"] ?: source.tempName
        return "$device · $channel"
    }
}

/** 一个可调速风扇通道 */
@Immutable
data class CoolingFanUi(
    val deviceUid: String,
    val channel: String,
    val deviceName: String,
    val channelLabel: String,
    val rpm: Int?,
    val duty: Double?,
    val speedOptions: CcSpeedOptions?,
    val profileName: String?,
)

/** 设备重命名区块已迁移到监控页（见 MonitorUiState.RenameDeviceUi） */
