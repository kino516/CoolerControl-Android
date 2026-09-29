package com.example.kernelsustyleuikit.ui.screen.cooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.LocalMainPagerState
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.cc.AddCardDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcInputDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcMultiSelectDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcSelectOption
import com.example.kernelsustyleuikit.ui.component.cc.CurveSelectDialog
import com.example.kernelsustyleuikit.ui.navigation3.Navigator
import com.example.kernelsustyleuikit.ui.viewmodel.CoolingViewModel

/**
 * 冷却页入口：模式切换、风扇曲线、风扇控制、设备重命名、压力测试。
 *
 * 区块顺序与显隐均可定制并持久化。
 */
@Composable
fun CoolingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<CoolingViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()

    var showAddSection by remember { mutableStateOf(false) }
    var showCurveVisibility by remember { mutableStateOf(false) }
    var showFanVisibility by remember { mutableStateOf(false) }
    var curvePickerFan by remember { mutableStateOf<CoolingFanUi?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) viewModel.refresh()
    }
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) viewModel.setEditMode(false)
    }
    // 编辑模式期间禁止左右滑动切页（PRD FR-6.2）
    val mainPagerState = LocalMainPagerState.current
    LaunchedEffect(editMode) {
        mainPagerState.pageInEditMode = editMode
    }

    val callbacks = CoolingCallbacks(
        onSwitchMode = viewModel::switchMode,
        onEditCurveVisibility = { showCurveVisibility = true },
        onEditFanVisibility = { showFanVisibility = true },
        onOpenFanCurvePicker = { curvePickerFan = it },
        onApplyManual = viewModel::applyManual,
        onStartStress = viewModel::startStress,
        onSelectStressKind = viewModel::selectStressKind,
        onSelectStressTarget = { kind, id ->
            if (kind == "gpu") viewModel.selectStressGpu(id) else viewModel.selectStressDrive(id)
        },
        onStopStress = viewModel::stopStress,
        onStopAllStress = viewModel::stopAllStress,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> CoolingPagerMiuix(
            state = uiState,
            editMode = editMode,
            callbacks = callbacks,
            bottomInnerPadding = bottomInnerPadding,
            onEditModeChange = viewModel::setEditMode,
            onOrderChange = viewModel::setSectionOrder,
            onRemove = viewModel::removeSection,
            onAddSection = { showAddSection = true },
        )

        UiMode.Material -> CoolingPagerMaterial(
            state = uiState,
            editMode = editMode,
            callbacks = callbacks,
            bottomInnerPadding = bottomInnerPadding,
            onEditModeChange = viewModel::setEditMode,
            onOrderChange = viewModel::setSectionOrder,
            onRemove = viewModel::removeSection,
            onAddSection = { showAddSection = true },
        )
    }

    // ---------- 添加区块 ----------
    AddCardDialog(
        show = showAddSection,
        options = viewModel.availableSections(),
        onDismiss = { showAddSection = false },
        onAdd = { id ->
            viewModel.addSection(id)
            showAddSection = false
        },
    )

    // ---------- 曲线显隐 ----------
    CcMultiSelectDialog(
        show = showCurveVisibility,
        title = stringResource(R.string.cc_section_profiles),
        options = uiState.profiles.map { profile ->
            CcSelectOption(
                id = profile.uid,
                label = profile.name,
                selected = profile.uid !in uiState.hiddenCurves,
                subtitle = uiState.tempSourceLabel(profile),
            )
        },
        onDismiss = { showCurveVisibility = false },
        onToggle = viewModel::toggleCurveVisibility,
    )

    // ---------- 风扇显隐 ----------
    CcMultiSelectDialog(
        show = showFanVisibility,
        title = stringResource(R.string.cc_section_fans),
        options = uiState.fans.map { fan ->
            val key = "${fan.deviceUid}|${fan.channel}"
            CcSelectOption(
                id = key,
                label = fan.channelLabel,
                selected = key !in uiState.hiddenFans,
                subtitle = fan.deviceName,
            )
        },
        onDismiss = { showFanVisibility = false },
        onToggle = { key ->
            val current = uiState.hiddenFans
            viewModel.setHiddenFans(if (key in current) current - key else current + key)
        },
    )

    // ---------- 给风扇挑曲线 ----------
    curvePickerFan?.let { fan ->
        CurveSelectDialog(
            show = true,
            profiles = uiState.profiles,
            onDismiss = { curvePickerFan = null },
            onSelect = { profileUid ->
                viewModel.selectProfile(fan.deviceUid, fan.channel, profileUid)
                curvePickerFan = null
            },
        )
    }

}
