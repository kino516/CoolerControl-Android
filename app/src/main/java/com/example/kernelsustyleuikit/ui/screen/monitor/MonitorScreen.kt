package com.example.kernelsustyleuikit.ui.screen.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.ui.LocalMainPagerState
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.cc.AddCardDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcContentDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcMultiSelectDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcSelectOption
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextField
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.navigation3.Navigator
import com.example.kernelsustyleuikit.ui.viewmodel.MonitorViewModel
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 监控页入口：时间温度表、报警规则、最近报警记录、历史温度统计。
 */
@Composable
fun MonitorPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<MonitorViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()

    var showAddSection by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var editAlertTarget by remember { mutableStateOf<CcAlert?>(null) }
    var silenceAlertTarget by remember { mutableStateOf<CcAlert?>(null) }
    // 重命名目标：设备用 uid，通道用 uid + 通道内部名
    var renameDeviceTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameChannelTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }

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

    val callbacks = MonitorCallbacks(
        onRangeChange = viewModel::setRangeMinutes,
        onToggleSeries = viewModel::toggleSeriesVisibility,
        onSelectChannels = { showChannelPicker = true },
        onEditAlert = { editAlertTarget = it },
        onSilenceAlert = { silenceAlertTarget = it },
        onToggleAlertEnabled = viewModel::setAlertEnabled,
        onRenameDevice = { uid, current -> renameDeviceTarget = uid to current },
        onRenameChannel = { uid, channel, current ->
            renameChannelTarget = Triple(uid, channel, current)
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> MonitorPagerMiuix(
            state = uiState,
            editMode = editMode,
            callbacks = callbacks,
            bottomInnerPadding = bottomInnerPadding,
            onEditModeChange = viewModel::setEditMode,
            onOrderChange = viewModel::setSectionOrder,
            onRemove = viewModel::removeSection,
            onAddSection = { showAddSection = true },
        )

        UiMode.Material -> MonitorPagerMaterial(
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

    // ---------- 监控通道选择（按设备分组） ----------
    CcMultiSelectDialog(
        show = showChannelPicker,
        title = stringResource(R.string.cc_select_channels),
        options = viewModel.allChannels().map { channel ->
            CcSelectOption(
                id = channel.key,
                label = channel.channelLabel,
                selected = channel.selected,
                subtitle = channel.deviceName,
            )
        },
        onDismiss = { showChannelPicker = false },
        onToggle = viewModel::toggleChannel,
    )

    // ---------- 报警规则编辑 ----------
    editAlertTarget?.let { alert ->
        AlertEditDialog(
            alert = alert,
            onDismiss = { editAlertTarget = null },
            onConfirm = { name, min, max ->
                viewModel.renameAlert(alert.uid, name)
                viewModel.updateAlertLimits(alert.uid, min, max)
                editAlertTarget = null
            },
        )
    }

    // ---------- 静默 ----------
    silenceAlertTarget?.let { alert ->
        SilenceDialog(
            onDismiss = { silenceAlertTarget = null },
            onSelect = { duration ->
                viewModel.silenceAlert(alert.uid, duration)
                silenceAlertTarget = null
            },
        )
    }

    // ---------- 设备重命名 ----------
    renameDeviceTarget?.let { (uid, current) ->
        RenameDialog(
            title = stringResource(R.string.cc_rename_device),
            initial = current,
            onDismiss = { renameDeviceTarget = null },
            onConfirm = { value ->
                viewModel.renameDevice(uid, value.ifBlank { null })
                renameDeviceTarget = null
            },
        )
    }

    // ---------- 通道重命名 ----------
    renameChannelTarget?.let { (uid, channel, current) ->
        RenameDialog(
            title = stringResource(R.string.cc_rename_channel),
            initial = current,
            onDismiss = { renameChannelTarget = null },
            onConfirm = { value ->
                viewModel.renameChannel(uid, channel, value.ifBlank { null })
                renameChannelTarget = null
            },
        )
    }
}

/**
 * 通用重命名输入框。
 *
 * 清空并保存即恢复默认名（daemon 的 overrides 传 null 会清除自定义名）。
 */
@Composable
private fun RenameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }

    CcContentDialog(
        show = true,
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cc_save),
        onConfirm = { onConfirm(value.trim()) },
    ) {
        CcTextField(value = value, onValueChange = { value = it })
        CcText(
            text = stringResource(R.string.cc_rename_reset_hint),
            style = CcTextStyle.Caption,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 报警规则编辑：名称 / 下限 / 上限 */
@Composable
private fun AlertEditDialog(
    alert: CcAlert,
    onDismiss: () -> Unit,
    onConfirm: (name: String, min: Double, max: Double) -> Unit,
) {
    var name by remember(alert.uid) { mutableStateOf(alert.name) }
    var minText by remember(alert.uid) { mutableStateOf(alert.min.roundToInt().toString()) }
    var maxText by remember(alert.uid) { mutableStateOf(alert.max.roundToInt().toString()) }

    CcContentDialog(
        show = true,
        title = alert.name,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cc_save),
        onConfirm = {
            onConfirm(
                name,
                minText.toDoubleOrNull() ?: alert.min,
                maxText.toDoubleOrNull() ?: alert.max,
            )
        },
    ) {
        CcText(text = stringResource(R.string.cc_server_name), style = CcTextStyle.Caption)
        CcTextField(value = name, onValueChange = { name = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CcText(text = stringResource(R.string.cc_alert_min), style = CcTextStyle.Caption)
                CcTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                CcText(text = stringResource(R.string.cc_alert_max), style = CcTextStyle.Caption)
                CcTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

/** 静默时长选择 */
@Composable
private fun SilenceDialog(
    onDismiss: () -> Unit,
    onSelect: (Duration?) -> Unit,
) {
    val options = listOf(
        stringResource(R.string.cc_silence_15m) to Duration.ofMinutes(15),
        stringResource(R.string.cc_silence_1h) to Duration.ofHours(1),
        stringResource(R.string.cc_silence_4h) to Duration.ofHours(4),
        stringResource(R.string.cc_silence_1d) to Duration.ofDays(1),
    )

    CcContentDialog(
        show = true,
        title = stringResource(R.string.cc_alert_silence),
        onDismiss = onDismiss,
    ) {
        options.forEach { (label, duration) ->
            CcText(
                text = label,
                style = CcTextStyle.Body,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(duration) }
                    .padding(vertical = 10.dp),
            )
        }
        CcText(
            text = stringResource(R.string.cc_silence_cancel),
            style = CcTextStyle.Body,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(null) }
                .padding(vertical = 10.dp),
        )
    }
}
