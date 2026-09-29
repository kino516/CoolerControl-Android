package com.example.kernelsustyleuikit.ui.screen.cooling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcMode
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import com.example.kernelsustyleuikit.data.model.CcStressTarget
import com.example.kernelsustyleuikit.ui.component.cc.CcActionText
import com.example.kernelsustyleuikit.ui.component.cc.CcChoiceChip
import com.example.kernelsustyleuikit.ui.component.cc.CcPillButton
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcCardHeader
import com.example.kernelsustyleuikit.ui.component.cc.CcSegment
import com.example.kernelsustyleuikit.ui.component.cc.CcSegmentedControl
import com.example.kernelsustyleuikit.ui.component.cc.CcSlider
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.component.cc.CurveThumbnail
import com.example.kernelsustyleuikit.ui.component.cc.ccOnSurfaceColor
import com.example.kernelsustyleuikit.ui.component.cc.ccPrimaryColor
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import kotlin.math.roundToInt

/** 冷却页回调集合 */
@Immutable
data class CoolingCallbacks(
    val onSwitchMode: (String) -> Unit = {},
    val onEditCurveVisibility: () -> Unit = {},
    val onEditFanVisibility: () -> Unit = {},
    val onOpenFanCurvePicker: (CoolingFanUi) -> Unit = {},
    val onApplyManual: (deviceUid: String, channel: String, speed: Int) -> Unit = { _, _, _ -> },
    val onStartStress: (String) -> Unit = {},
    val onStopStress: (String) -> Unit = {},
    val onStopAllStress: () -> Unit = {},
    val onSelectStressTarget: (kind: String, id: String) -> Unit = { _, _ -> },
    /** 选中压力测试类型（不启动） */
    val onSelectStressKind: (String) -> Unit = {},
)

/** 按区块 id 渲染冷却页内容 */
@Composable
fun CoolingSectionContent(
    sectionId: String,
    state: CoolingUiState,
    callbacks: CoolingCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    when (sectionId) {
        CoolingSection.MODES -> ModesSection(state, callbacks, editMode, onRemove)
        CoolingSection.PROFILES -> ProfilesSection(state, callbacks, editMode, onRemove)
        CoolingSection.FANS -> FansSection(state, callbacks, editMode, onRemove)
        CoolingSection.STRESS -> StressSection(state, callbacks, editMode, onRemove)
        CoolingSection.POWER -> PowerSection(state, editMode, onRemove)
    }
}

// ---------- 模式（一级界面直接切换）----------

@Composable
private fun ModesSection(
    state: CoolingUiState,
    callbacks: CoolingCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        CcCardHeader(
            title = stringResource(R.string.cc_section_modes),
            subtitle = stringResource(R.string.cc_modes_count, state.modes.size),
        )
        // 模式是互斥单选，用分段控件比一排独立芯片整洁。
        // 等分整行宽度：模式通常就两三个，固定宽度会让按钮显得很小、难点
        CcSegmentedControl(
            options = state.modes.map { CcSegment(it.uid, it.name) },
            selectedId = state.activeModeUid,
            onSelect = callbacks.onSwitchMode,
            fillEqually = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

// ---------- 风扇曲线 ----------

@Composable
private fun ProfilesSection(
    state: CoolingUiState,
    callbacks: CoolingCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_profiles),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            CcActionText(
                text = stringResource(R.string.cc_edit),
                onClick = callbacks.onEditCurveVisibility,
            )
        }

        state.visibleProfiles.forEach { profile ->
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        CcText(text = profile.name, style = CcTextStyle.Body)
                        state.tempSourceLabel(profile)?.let { source ->
                            CcText(
                                text = stringResource(R.string.cc_profile_source, source),
                                style = CcTextStyle.Caption,
                            )
                        }
                    }
                    if (profile.tempMin != null && profile.tempMax != null) {
                        CcText(
                            text = stringResource(
                                R.string.cc_profile_range,
                                profile.tempMin.roundToInt().toString(),
                                profile.tempMax.roundToInt().toString(),
                            ),
                            style = CcTextStyle.Caption,
                        )
                    }
                }
                // 缩略图在文字下方、通栏显示
                CurveThumbnail(
                    points = profile.speedProfile,
                    color = ccPrimaryColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

// ---------- 风扇控制 ----------

@Composable
private fun FansSection(
    state: CoolingUiState,
    callbacks: CoolingCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_fans),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            CcActionText(
                text = stringResource(R.string.cc_edit),
                onClick = callbacks.onEditFanVisibility,
            )
        }

        state.visibleFans.forEachIndexed { index, fan ->
            // 每台风扇各自包一个浅色圆角容器。
            // 原先用细分割线，和卡片内部标题下的分割线长得太像，
            // 分不清「这是两台风扇」还是「这是新的一段」。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 10.dp else 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ccOnSurfaceColor().copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                FanRow(fan = fan, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun FanRow(
    fan: CoolingFanUi,
    callbacks: CoolingCallbacks,
) {
    val minDuty = (fan.speedOptions?.minDuty ?: 0).toFloat()
    val maxDuty = (fan.speedOptions?.maxDuty ?: 100).toFloat()
    val adjustable = fan.speedOptions?.fixedEnabled == true

    // 待应用值：拖动只改这里，点「应用手动」才下发
    var pending by remember(fan.deviceUid, fan.channel) {
        mutableFloatStateOf((fan.duty ?: minDuty.toDouble()).toFloat().coerceIn(minDuty, maxDuty))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                CcText(text = fan.channelLabel, style = CcTextStyle.Body)
                CcText(text = fan.deviceName, style = CcTextStyle.Caption)
            }
            Column(horizontalAlignment = Alignment.End) {
                CcText(
                    text = fan.rpm?.let { "$it RPM" } ?: "--",
                    style = CcTextStyle.Body,
                )
                CcText(
                    text = stringResource(
                        R.string.cc_current_duty,
                        (fan.duty ?: 0.0).roundToInt(),
                    ),
                    style = CcTextStyle.Caption,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcSlider(
                value = pending,
                onValueChange = { pending = it },
                modifier = Modifier.weight(1f),
                enabled = adjustable,
                valueRange = minDuty..maxDuty,
            )
            CcText(
                text = stringResource(R.string.cc_manual_value, pending.roundToInt()),
                style = CcTextStyle.Caption,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 手动调速：左侧显示待应用值，右侧实心按钮才真正下发
        if (adjustable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcText(
                    text = stringResource(R.string.cc_manual_value, pending.roundToInt()),
                    style = CcTextStyle.Body,
                    modifier = Modifier.weight(1f),
                )
                CcPillButton(
                    text = stringResource(R.string.cc_apply_manual),
                    onClick = {
                        callbacks.onApplyManual(fan.deviceUid, fan.channel, pending.roundToInt())
                    },
                    filled = true,
                )
            }
        } else {
            CcText(
                text = stringResource(
                    if (fan.speedOptions == null) R.string.cc_not_adjustable else R.string.cc_fixed_disabled
                ),
                style = CcTextStyle.Caption,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = ccOnSurfaceColor().copy(alpha = 0.12f),
        )

        // 控制配置：右侧胶囊按钮显示当前绑定的曲线名，点击即可换曲线
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcText(
                text = stringResource(R.string.cc_control_config),
                style = CcTextStyle.Body,
                modifier = Modifier.weight(1f),
            )
            CcPillButton(
                text = fan.profileName ?: stringResource(R.string.cc_curve_none),
                onClick = { callbacks.onOpenFanCurvePicker(fan) },
            )
        }
    }
}

// ---------- 电源模式 ----------

/**
 * 电源模式集成。
 *
 * daemon 通过 D-Bus 读系统电源模式，并按映射自动套用散热模式。
 * **API 不提供「切换电源模式」** —— 那是操作系统的职责，这里只呈现
 * 当前状态与映射关系；`available` 为空（多数台式机）时整张卡片不渲染。
 */
@Composable
private fun PowerSection(
    state: CoolingUiState,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    val power = state.powerProfiles ?: return
    if (!power.isSupported) return

    CcCard(editMode = editMode, onRemove = onRemove) {
        CcCardHeader(
            title = stringResource(R.string.cc_section_power),
            subtitle = power.active?.let { stringResource(R.string.cc_power_active, it) },
        )
        CcText(
            text = stringResource(R.string.cc_power_hint),
            style = CcTextStyle.Caption,
            modifier = Modifier.padding(top = 4.dp),
        )

        power.available.forEach { profile ->
            val modeUid = power.modes[profile]
            val modeName = modeUid?.let { uid -> state.modes.firstOrNull { it.uid == uid }?.name }
            val isActive = profile == power.active

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcText(
                    text = profile,
                    style = CcTextStyle.Body,
                    color = if (isActive) ccPrimaryColor() else null,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                CcText(
                    text = modeName ?: stringResource(R.string.cc_power_unmapped),
                    style = CcTextStyle.Caption,
                    color = if (modeName != null) ccPrimaryColor() else null,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------- 压力测试 ----------

@Composable
private fun StressSection(
    state: CoolingUiState,
    callbacks: CoolingCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        CcText(text = stringResource(R.string.cc_section_stress), style = CcTextStyle.Title)
        CcText(
            text = stringResource(R.string.cc_stress_warning),
            style = CcTextStyle.Caption,
            color = CcTemperatureStyle.Amber,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 1. 选类型：只选中，不启动。压力测试会真把 CPU / 磁盘跑满，误触代价不小
        CcSegmentedControl(
            options = StressKind.entries.map { CcSegment(it.id, stringResource(it.labelRes)) },
            selectedId = state.selectedStressKind,
            onSelect = callbacks.onSelectStressKind,
            minSegmentWidth = 68.dp,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
        )

        // 2. 选目标：只显示当前选中类型对应的目标。
        // 原先不论选什么都把 GPU / 硬盘一起列出来，选了处理器还能挑硬盘，
        // 会让人以为两者能同时跑
        val selectedKind = state.selectedStressKind
        if (selectedKind == "gpu" && state.stressGpus.size > 1) {
            StressTargetGroup(
                label = stringResource(R.string.cc_stress_gpu),
                targets = state.stressGpus,
                selectedId = state.stressGpuId,
                onSelect = { callbacks.onSelectStressTarget("gpu", it) },
            )
        }
        if (selectedKind == "drive" && state.stressDrives.size > 1) {
            StressTargetGroup(
                label = stringResource(R.string.cc_stress_drive),
                targets = state.stressDrives,
                selectedId = state.stressDriveId,
                onSelect = { callbacks.onSelectStressTarget("drive", it) },
            )
        }

        // 3. 开始 / 停止：主操作做成整行大按钮
        CcPillButton(
            text = stringResource(R.string.cc_stress_start),
            onClick = {
                state.selectedStressKind?.let(callbacks.onStartStress)
            },
            filled = true,
            enabled = state.selectedStressKind != null,
            large = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )

        if (state.runningStress.isNotEmpty()) {
            CcPillButton(
                text = stringResource(R.string.cc_stress_stop_all),
                onClick = callbacks.onStopAllStress,
                filled = true,
                tone = CcTemperatureStyle.Red,
                large = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        if (state.runningStress.isNotEmpty()) {
            // 先把名称解析好：joinToString / map 的 lambda 都不是 Composable 上下文，
            // 在里面调用 stringResource 会编译失败，for 循环则可以
            val runningNames = mutableListOf<String>()
            for (kind in StressKind.entries) {
                if (kind.id in state.runningStress) {
                    runningNames += stringResource(kind.labelRes)
                }
            }
            CcText(
                text = stringResource(
                    R.string.cc_stress_running_now,
                    runningNames.joinToString("、"),
                ),
                style = CcTextStyle.Caption,
                color = CcTemperatureStyle.Red,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 目标选择：竖向列出，避免横向滚动误触翻页 */
@Composable
private fun StressTargetGroup(
    label: String,
    targets: List<CcStressTarget>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        CcText(
            text = stringResource(R.string.cc_stress_target, label),
            style = CcTextStyle.Caption,
        )
        targets.forEach { target ->
            CcChoiceChip(
                text = target.label,
                selected = target.id == selectedId,
                onClick = { onSelect(target.id) },
                badge = target.detail,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

private enum class StressKind(val id: String, val labelRes: Int) {
    Cpu("cpu", R.string.cc_stress_cpu),
    Gpu("gpu", R.string.cc_stress_gpu),
    Ram("ram", R.string.cc_stress_ram),
    Drive("drive", R.string.cc_stress_drive),
}
