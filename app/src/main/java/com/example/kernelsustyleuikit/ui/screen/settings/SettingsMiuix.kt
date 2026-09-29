package com.example.kernelsustyleuikit.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.BuildConfig
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.cc.CcContentDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.data.local.CcAppIcon
import com.example.kernelsustyleuikit.ui.component.cc.CcIconPickerGrid
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.theme.LocalEnableBlur
import com.example.kernelsustyleuikit.ui.util.BlurredBar
import com.example.kernelsustyleuikit.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    var showUiModePicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showPollRatePicker by remember { mutableStateOf(false) }
    var showStartupDelayPicker by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    // ---- 服务器 ----
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_current_server),
                            summary = uiState.serverName ?: stringResource(id = R.string.cc_no_server),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dns,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_current_server),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenServers,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_diagnostics),
                            summary = uiState.endpointLabel,
                            startAction = {
                                Icon(
                                    Icons.Rounded.NetworkCheck,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_diagnostics),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenDiagnostics,
                        )
                    }

                    // ---- 连接 ----
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.cc_auto_endpoint),
                            summary = stringResource(id = R.string.cc_auto_endpoint_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Speed,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_auto_endpoint),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.autoSelectEndpoint,
                            onCheckedChange = actions.onSetAutoEndpoint,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_sse_status),
                            summary = stringResource(
                                if (uiState.sseConnected) R.string.cc_connected
                                else R.string.cc_disconnected
                            ),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Sync,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_sse_status),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { },
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        // 刻意不用 Miuix 的 OverlayDropdownPreference：实测其弹层在部分渲染
                        // 路径下点击无响应，会导致「切不回 Miuix」。改用自研选择对话框。
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = UiMode.fromValue(uiState.uiMode).name,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_ui_mode),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showUiModePicker = true },
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_theme),
                            summary = stringResource(id = R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_theme),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTheme
                        )
                        // 桌面图标样式：多选一。后期往 CcAppIcon.OPTIONS 里追加即可
                        val iconLabel = CcAppIcon.OPTIONS
                            .firstOrNull { it.id == uiState.iconId }
                            ?.let { stringResource(it.labelRes) }
                            ?: uiState.iconId
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_icon_style),
                            summary = iconLabel,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_icon_style),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showIconPicker = true },
                        )
                    }

                    // ---- daemon 设置 ----
                    // 只放「改了不会连不上」的开关；no_init / allow_unencrypted 等
                    // 会影响 daemon 启动与连接协议，刻意不暴露。
                    val daemon = uiState.daemonSettings
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        // 两个数值项用固定档位而非自由输入，省掉输入框组件
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_daemon_poll_rate),
                            summary = stringResource(
                                id = R.string.cc_daemon_poll_rate_summary,
                                daemon.pollRate,
                            ),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Sync,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_poll_rate),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showPollRatePicker = true },
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_daemon_startup_delay),
                            summary = stringResource(
                                id = R.string.cc_daemon_startup_delay_summary,
                                daemon.startupDelay,
                            ),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_startup_delay),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showStartupDelayPicker = true },
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.cc_daemon_hide_duplicate),
                            summary = stringResource(id = R.string.cc_daemon_hide_duplicate_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Sync,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_hide_duplicate),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = daemon.hideDuplicateDevices,
                            onCheckedChange = {
                                actions.onSetDaemonFlag("hide_duplicate_devices", it)
                            },
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.cc_daemon_sensors_auto),
                            summary = stringResource(id = R.string.cc_daemon_sensors_auto_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_sensors_auto),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = daemon.sensorsAutoDetect,
                            onCheckedChange = {
                                actions.onSetDaemonFlag("sensors_auto_detect", it)
                            },
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.cc_daemon_drivetemp),
                            summary = stringResource(id = R.string.cc_daemon_drivetemp_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Sync,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_drivetemp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = daemon.drivetempSuspend,
                            onCheckedChange = {
                                actions.onSetDaemonFlag("drivetemp_suspend", it)
                            },
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.cc_daemon_liquidctl),
                            summary = stringResource(id = R.string.cc_daemon_liquidctl_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_daemon_liquidctl),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = daemon.liquidctlIntegration,
                            onCheckedChange = {
                                actions.onSetDaemonFlag("liquidctl_integration", it)
                            },
                        )
                    }

                    // ---- 数据 ----
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_clear_data),
                            summary = stringResource(id = R.string.cc_clear_data_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.DeleteForever,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_clear_data),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showClearDataConfirm = true },
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_logout),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Logout,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_logout),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showLogoutConfirm = true },
                        )
                    }

                    // ---- 关于 ----
                    // 反馈入口、GitHub 链接等留待后续补充
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_about_author),
                            summary = stringResource(id = R.string.cc_about_author_name),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Person,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_about_author),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {},
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.cc_version),
                            summary = BuildConfig.VERSION_NAME,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Info,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.cc_version),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {},
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }

    CcContentDialog(
        show = showUiModePicker,
        title = stringResource(R.string.settings_ui_mode),
        onDismiss = { showUiModePicker = false },
    ) {
        UiMode.entries.forEachIndexed { index, mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        actions.onSetUiModeIndex(index)
                        showUiModePicker = false
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcText(
                    text = mode.name,
                    style = CcTextStyle.Body,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.uiMode == mode.value) {
                    CcText(
                        text = stringResource(R.string.cc_in_use),
                        style = CcTextStyle.Caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // 桌面图标样式选择：清单来自 CcAppIcon.OPTIONS，追加图标无需改这里
    CcContentDialog(
        show = showIconPicker,
        title = stringResource(R.string.cc_icon_style),
        onDismiss = { showIconPicker = false },
    ) {
        // 只显示图标本身，不配名称 —— 图标就是最直观的标签，
        // 配文字反而要在「看颜色」和「读名字」之间来回切换
        CcIconPickerGrid(
            options = CcAppIcon.OPTIONS,
            selectedId = uiState.iconId,
            onSelect = { id ->
                actions.onSetAppIcon(id)
                showIconPicker = false
            },
        )
    }

    CcContentDialog(
        show = showClearDataConfirm,
        title = stringResource(R.string.cc_clear_data),
        onDismiss = { showClearDataConfirm = false },
        confirmText = stringResource(R.string.cc_confirm),
        onConfirm = {
            actions.onClearData()
            showClearDataConfirm = false
        },
    ) {
        CcText(text = stringResource(R.string.cc_clear_data_confirm), style = CcTextStyle.Body)
    }

    CcContentDialog(
        show = showLogoutConfirm,
        title = stringResource(R.string.cc_logout),
        onDismiss = { showLogoutConfirm = false },
        confirmText = stringResource(R.string.cc_confirm),
        onConfirm = {
            actions.onLogout()
            showLogoutConfirm = false
        },
    ) {
        CcText(text = stringResource(R.string.cc_logout_confirm), style = CcTextStyle.Body)
    }

    CcContentDialog(
        show = showPollRatePicker,
        title = stringResource(R.string.cc_daemon_poll_rate),
        onDismiss = { showPollRatePicker = false },
    ) {
        POLL_RATE_OPTIONS.forEach { rate ->
            DaemonNumberRow(
                text = stringResource(R.string.cc_daemon_seconds, rate.toString()),
                selected = uiState.daemonSettings.pollRate == rate,
                onClick = {
                    actions.onSetDaemonNumber("poll_rate", rate)
                    showPollRatePicker = false
                },
            )
        }
    }

    CcContentDialog(
        show = showStartupDelayPicker,
        title = stringResource(R.string.cc_daemon_startup_delay),
        onDismiss = { showStartupDelayPicker = false },
    ) {
        STARTUP_DELAY_OPTIONS.forEach { delay ->
            DaemonNumberRow(
                text = stringResource(R.string.cc_daemon_seconds, delay.toString()),
                selected = uiState.daemonSettings.startupDelay == delay,
                onClick = {
                    actions.onSetDaemonNumber("startup_delay", delay.toDouble())
                    showStartupDelayPicker = false
                },
            )
        }
    }
}

/** 轮询速率档位（秒），对应网页端的「轮询速率」 */
private val POLL_RATE_OPTIONS = listOf(0.5, 1.0, 2.0, 5.0)

/** 启动延迟档位（秒） */
private val STARTUP_DELAY_OPTIONS = listOf(0, 5, 10, 30)

/** 数值选项对话框里的一行 */
@Composable
private fun DaemonNumberRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcText(
            text = text,
            style = CcTextStyle.Body,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            CcText(
                text = stringResource(R.string.cc_in_use),
                style = CcTextStyle.Caption,
            )
        }
    }
}
