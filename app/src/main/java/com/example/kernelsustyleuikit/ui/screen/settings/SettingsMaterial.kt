package com.example.kernelsustyleuikit.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.BuildConfig
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.local.CcAppIcon
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.cc.CcContentDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcIconPickerGrid
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.component.material.SegmentedColumn
import com.example.kernelsustyleuikit.ui.component.material.SegmentedDropdownItem
import com.example.kernelsustyleuikit.ui.component.material.SegmentedListItem
import com.example.kernelsustyleuikit.ui.component.material.SegmentedSwitchItem
import com.example.kernelsustyleuikit.ui.component.dialog.rememberLoadingDialog
import com.example.kernelsustyleuikit.ui.component.material.SnackBarHost

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.padding(bottom = bottomInnerPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 服务器 ----
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenServers,
                            headlineContent = { Text(stringResource(id = R.string.cc_current_server)) },
                            supportingContent = {
                                Text(uiState.serverName ?: stringResource(id = R.string.cc_no_server))
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Dns,
                                    stringResource(id = R.string.cc_current_server)
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenDiagnostics,
                            headlineContent = { Text(stringResource(id = R.string.cc_diagnostics)) },
                            supportingContent = { uiState.endpointLabel?.let { Text(it) } },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.NetworkCheck,
                                    stringResource(id = R.string.cc_diagnostics)
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        )
                    }
                )
            )

            // ---- 连接 ----
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Speed,
                            title = stringResource(id = R.string.cc_auto_endpoint),
                            summary = stringResource(id = R.string.cc_auto_endpoint_summary),
                            checked = uiState.autoSelectEndpoint,
                            onCheckedChange = actions.onSetAutoEndpoint
                        )
                    },
                    // ---- daemon 设置 ----
                    // 只放「改了不会连不上」的开关；no_init / allow_unencrypted 等
                    // 会影响 daemon 启动与连接协议，刻意不暴露。
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Sync,
                            title = stringResource(id = R.string.cc_daemon_hide_duplicate),
                            summary = stringResource(id = R.string.cc_daemon_hide_duplicate_summary),
                            checked = uiState.daemonSettings.hideDuplicateDevices,
                            onCheckedChange = { actions.onSetDaemonFlag("hide_duplicate_devices", it) }
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.Dashboard,
                            title = stringResource(id = R.string.cc_daemon_sensors_auto),
                            summary = stringResource(id = R.string.cc_daemon_sensors_auto_summary),
                            checked = uiState.daemonSettings.sensorsAutoDetect,
                            onCheckedChange = { actions.onSetDaemonFlag("sensors_auto_detect", it) }
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Speed,
                            title = stringResource(id = R.string.cc_daemon_drivetemp),
                            summary = stringResource(id = R.string.cc_daemon_drivetemp_summary),
                            checked = uiState.daemonSettings.drivetempSuspend,
                            onCheckedChange = { actions.onSetDaemonFlag("drivetemp_suspend", it) }
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Dns,
                            title = stringResource(id = R.string.cc_daemon_liquidctl),
                            summary = stringResource(id = R.string.cc_daemon_liquidctl_summary),
                            checked = uiState.daemonSettings.liquidctlIntegration,
                            onCheckedChange = { actions.onSetDaemonFlag("liquidctl_integration", it) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = { },
                            headlineContent = { Text(stringResource(id = R.string.cc_sse_status)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (uiState.sseConnected) R.string.cc_connected
                                        else R.string.cc_disconnected
                                    )
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Sync,
                                    stringResource(id = R.string.cc_sse_status)
                                )
                            },
                        )
                    }
                )
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = buildList {
                    add {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.Dashboard,
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onItemSelected = actions.onSetUiModeIndex
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onOpenTheme,
                            headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                            leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                    add {
                        // 桌面图标样式：清单来自 CcAppIcon.OPTIONS，追加图标无需改这里
                        SegmentedListItem(
                            onClick = { showIconPicker = true },
                            headlineContent = { Text(stringResource(id = R.string.cc_icon_style)) },
                            supportingContent = {
                                Text(
                                    CcAppIcon.OPTIONS
                                        .firstOrNull { it.id == uiState.iconId }
                                        ?.let { stringResource(it.labelRes) }
                                        ?: uiState.iconId
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Palette, stringResource(id = R.string.cc_icon_style))
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        )
                    }
                }
            )

            // ---- 数据 ----
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = { showClearDataConfirm = true },
                            headlineContent = { Text(stringResource(id = R.string.cc_clear_data)) },
                            supportingContent = {
                                Text(stringResource(id = R.string.cc_clear_data_summary))
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    stringResource(id = R.string.cc_clear_data)
                                )
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = { showLogoutConfirm = true },
                            headlineContent = { Text(stringResource(id = R.string.cc_logout)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Logout,
                                    stringResource(id = R.string.cc_logout)
                                )
                            },
                        )
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 关于 ----
            // 反馈入口、GitHub 链接等留待后续补充
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = {},
                            headlineContent = { Text(stringResource(id = R.string.cc_about_author)) },
                            supportingContent = {
                                Text(stringResource(id = R.string.cc_about_author_name))
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Person,
                                    stringResource(id = R.string.cc_about_author)
                                )
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = {},
                            headlineContent = { Text(stringResource(id = R.string.cc_version)) },
                            supportingContent = { Text(BuildConfig.VERSION_NAME) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Info,
                                    stringResource(id = R.string.cc_version)
                                )
                            },
                        )
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
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

    // 桌面图标样式选择：清单来自 CcAppIcon.OPTIONS，追加图标无需改这里
    CcContentDialog(
        show = showIconPicker,
        title = stringResource(R.string.cc_icon_style),
        onDismiss = { showIconPicker = false },
    ) {
        // 只显示图标本身，不配名称
        CcIconPickerGrid(
            options = CcAppIcon.OPTIONS,
            selectedId = uiState.iconId,
            onSelect = { id ->
                actions.onSetAppIcon(id)
                showIconPicker = false
            },
        )
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
