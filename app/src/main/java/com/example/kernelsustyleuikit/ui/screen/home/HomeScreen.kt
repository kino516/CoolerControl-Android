package com.example.kernelsustyleuikit.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kernelsustyleuikit.ui.LocalMainPagerState
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.cc.AddCardDialog
import com.example.kernelsustyleuikit.ui.component.cc.AlertListDialog
import com.example.kernelsustyleuikit.ui.component.cc.FanControlDialog
import com.example.kernelsustyleuikit.ui.component.cc.ModeSelectDialog
import com.example.kernelsustyleuikit.ui.component.cc.TemperatureDetailDialog
import com.example.kernelsustyleuikit.ui.navigation3.Navigator
import com.example.kernelsustyleuikit.ui.navigation3.Route
import com.example.kernelsustyleuikit.ui.util.CcFormat
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import com.example.kernelsustyleuikit.ui.viewmodel.HomeViewModel

/** 主界面第 3 页（索引 2）是监控页 */
private const val MONITOR_PAGE_INDEX = 2

/**
 * 主页（卡片仪表盘）入口。
 *
 * 两套风格共用同一份状态与回调，仅渲染层不同；
 * 卡片点击后的快捷对话框在这里统一挂载（PRD FR-2.2）。
 */
@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val mainPagerState = LocalMainPagerState.current

    var selectedTemperature by remember { mutableStateOf<HomeCardUi.Temperature?>(null) }
    var selectedFan by remember { mutableStateOf<HomeCardUi.Fan?>(null) }
    var showAddCard by remember { mutableStateOf(false) }
    var showAlertList by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

    // 进入本页时刷新一次
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) viewModel.refresh()
    }
    // 切到其它页面时自动退出编辑模式
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) viewModel.setEditMode(false)
    }
    // 编辑模式期间禁止左右滑动切页（PRD FR-6.2）
    LaunchedEffect(editMode) {
        mainPagerState.pageInEditMode = editMode
    }

    val actions = HomeActions(
        onTemperatureClick = { selectedTemperature = it },
        onFanClick = { selectedFan = it },
        onAlertClick = { showAlertList = true },
        onModeClick = { showModePicker = true },
        onSwitchMode = viewModel::switchMode,
        onOverviewClick = { navigator.push(Route.Diagnostics) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            editMode = editMode,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onEditModeChange = viewModel::setEditMode,
            onOrderChange = viewModel::setCardOrder,
            onRemove = viewModel::removeCard,
            onAddCard = { showAddCard = true },
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            editMode = editMode,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onEditModeChange = viewModel::setEditMode,
            onOrderChange = viewModel::setCardOrder,
            onRemove = viewModel::removeCard,
            onAddCard = { showAddCard = true },
        )
    }

    // ---------- 温度详情 ----------
    selectedTemperature?.let { card ->
        val current = viewModel.latestTemp(card.deviceUid, card.channel) ?: card.value
        TemperatureDetailDialog(
            show = true,
            title = card.title,
            subtitle = card.subtitle,
            value = current,
            stats = viewModel.statsFor(card.deviceUid, card.channel),
            trend = viewModel.trendFor(card.deviceUid, card.channel),
            trendColor = CcTemperatureStyle.colorFor(
                current,
                card.deviceType,
                card.subtitle.orEmpty(),
            ),
            onDismiss = { selectedTemperature = null },
            onViewInMonitor = {
                selectedTemperature = null
                mainPagerState.animateToPage(MONITOR_PAGE_INDEX)
            },
        )
    }

    // ---------- 风扇控制 ----------
    selectedFan?.let { card ->
        FanControlDialog(
            show = true,
            title = card.title,
            subtitle = card.subtitle,
            rpm = viewModel.latestRpm(card.deviceUid, card.channel) ?: card.rpm,
            duty = viewModel.latestDuty(card.deviceUid, card.channel) ?: card.duty,
            speedOptions = viewModel.speedOptionsFor(card.deviceUid, card.channel),
            currentProfileName = viewModel.boundProfileName(card.deviceUid, card.channel),
            profiles = viewModel.profiles(),
            onDismiss = { selectedFan = null },
            onApplyManual = { speed ->
                viewModel.applyManual(card.deviceUid, card.channel, speed)
                selectedFan = null
            },
            onSelectProfile = { profileUid ->
                viewModel.selectProfile(card.deviceUid, card.channel, profileUid)
                selectedFan = null
            },
            onRestoreCurve = {
                viewModel.restoreCurve(card.deviceUid, card.channel)
                selectedFan = null
            },
        )
    }

    // ---------- 报警列表 ----------
    AlertListDialog(
        show = showAlertList,
        alerts = viewModel.alerts(),
        onDismiss = { showAlertList = false },
        onViewAll = {
            showAlertList = false
            mainPagerState.animateToPage(MONITOR_PAGE_INDEX)
        },
    )

    // ---------- 模式切换 ----------
    ModeSelectDialog(
        show = showModePicker,
        modes = viewModel.modes(),
        activeUid = viewModel.activeModeUid(),
        onDismiss = { showModePicker = false },
        onSelect = { modeUid ->
            viewModel.switchMode(modeUid)
            showModePicker = false
        },
    )

    // ---------- 添加卡片 ----------
    AddCardDialog(
        show = showAddCard,
        options = viewModel.availableCards(),
        onDismiss = { showAddCard = false },
        onAdd = { id ->
            viewModel.addCard(id)
            showAddCard = false
        },
    )
}
