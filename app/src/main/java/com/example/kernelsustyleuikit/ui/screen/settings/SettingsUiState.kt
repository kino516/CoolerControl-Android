package com.example.kernelsustyleuikit.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.example.kernelsustyleuikit.data.local.CcAppIcon
import com.example.kernelsustyleuikit.data.model.CcDaemonSettings
import com.example.kernelsustyleuikit.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
    /** 当前桌面图标样式 id（见 CcAppIcon.OPTIONS） */
    val iconId: String = CcAppIcon.DEFAULT_ID,
    /** daemon 全局设置 */
    val daemonSettings: CcDaemonSettings = CcDaemonSettings(),

    // ---- CoolerControl ----
    /** 当前服务器名 */
    val serverName: String? = null,
    /** 形如「内网 · 10.0.0.53 · 8ms」 */
    val endpointLabel: String? = null,
    val daemonVersion: String? = null,
    val autoSelectEndpoint: Boolean = true,
    val sseConnected: Boolean = false,
)

@Immutable
data class SettingsScreenActions(
    val onOpenTheme: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    /** 选择桌面图标样式 */
    val onSetAppIcon: (String) -> Unit = {},
    /** 修改 daemon 设置里的某个布尔开关（键名与 /settings 一致） */
    val onSetDaemonFlag: (String, Boolean) -> Unit = { _, _ -> },
    /** 修改 daemon 设置里的某个数值项，如 poll_rate / startup_delay */
    val onSetDaemonNumber: (String, Double) -> Unit = { _, _ -> },
    // ---- CoolerControl ----
    val onOpenServers: () -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onSetAutoEndpoint: (Boolean) -> Unit = {},
    val onClearData: () -> Unit = {},
    val onLogout: () -> Unit = {},
)
