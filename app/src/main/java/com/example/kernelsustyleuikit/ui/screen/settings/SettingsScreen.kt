package com.example.kernelsustyleuikit.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.navigation3.Navigator
import com.example.kernelsustyleuikit.ui.navigation3.Route
import com.example.kernelsustyleuikit.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenServers = { navigator.push(Route.Servers) },
        onSetAppIcon = viewModel::setAppIcon,
        onSetDaemonFlag = viewModel::setDaemonFlag,
        onSetDaemonNumber = viewModel::setDaemonNumber,
        onOpenDiagnostics = { navigator.push(Route.Diagnostics) },
        onSetAutoEndpoint = viewModel::setAutoEndpoint,
        onClearData = viewModel::clearData,
        onLogout = viewModel::logout,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}
