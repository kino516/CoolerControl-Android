package com.example.kernelsustyleuikit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.local.CcAppIcon
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.repository.CcSseState
import com.example.kernelsustyleuikit.data.repository.SettingsRepository
import com.example.kernelsustyleuikit.data.repository.SettingsRepositoryImpl
import com.example.kernelsustyleuikit.data.session.CcSessionState
import com.example.kernelsustyleuikit.templateApp
import com.example.kernelsustyleuikit.ui.screen.settings.SettingsUiState
import com.example.kernelsustyleuikit.ui.theme.ColorMode
import org.json.JSONObject

class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val ccPrefs = CcGraph.prefs
    private val ccSession = CcGraph.session
    private val ccRepo = CcGraph.repository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeCoolerControl()
    }

    private fun observeCoolerControl() {
        viewModelScope.launch {
            ccSession.state.collect { state ->
                _uiState.update {
                    it.copy(
                        serverName = state.serverName,
                        endpointLabel = endpointLabel(state),
                        daemonVersion = state.daemonVersion,
                    )
                }
            }
        }
        viewModelScope.launch {
            ccRepo.sseState.collect { sse ->
                _uiState.update { it.copy(sseConnected = sse == CcSseState.Connected) }
            }
        }
    }

    private fun endpointLabel(state: CcSessionState): String? {
        val url = state.endpointUrl ?: return null
        val kind = when (state.endpointKind) {
            CcEndpointKind.Lan -> templateApp.getString(R.string.cc_endpoint_lan)
            CcEndpointKind.Wan -> templateApp.getString(R.string.cc_endpoint_wan)
            null -> null
        }
        val host = url.substringAfter("://").substringBefore('/')
        val latency = state.latencyMs?.let { "${it}ms" }
        return listOfNotNull(kind, host, latency).joinToString(" · ")
    }

    fun refresh() {
        viewModelScope.launch {
            // daemon 设置与界面设置都不在元数据刷新链路里，进设置页时主动拉一次。
            // 界面设置的写回依赖完整快照（PUT 是替换语义），快照为空会直接失败。
            ccRepo.refreshDaemonSettings()
            ccRepo.refreshUiSettings()
            val themeMode = repo.themeMode
            val miuixMonet = repo.miuixMonet
            val keyColor = repo.keyColor
            val enablePredictiveBack = repo.enablePredictiveBack
            val enableBlur = repo.enableBlur
            val enableFloatingBottomBar = repo.enableFloatingBottomBar
            val enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur
            val pageScale = repo.pageScale
            val iconId = CcAppIcon.currentId()
            val daemonSettings = ccRepo.daemonSettings.value
            val colorStyle = repo.colorStyle
            val colorSpec = repo.colorSpec
            val uiMode = repo.uiMode
            // 这个开关此前漏赋值：重进设置页时显示值与实际不符
            val autoSelectEndpoint = ccPrefs.autoSelectEndpoint

            _uiState.update {
                it.copy(
                    uiMode = uiMode,
                    themeMode = themeMode,
                    miuixMonet = miuixMonet,
                    keyColor = keyColor,
                    enablePredictiveBack = enablePredictiveBack,
                    enableBlur = enableBlur,
                    enableFloatingBottomBar = enableFloatingBottomBar,
                    enableFloatingBottomBarBlur = enableFloatingBottomBarBlur,
                    pageScale = pageScale,
                    iconId = iconId,
                    daemonSettings = daemonSettings,
                    colorStyle = colorStyle,
                    colorSpec = colorSpec,
                    autoSelectEndpoint = autoSelectEndpoint,
                )
            }
        }
    }

    /** 修改 daemon 设置里的数值项；同样是差量 PATCH */
    fun setDaemonNumber(key: String, value: Double) {
        viewModelScope.launch {
            ccRepo.updateDaemonSettings(JSONObject().put(key, value))
                .onSuccess {
                    _uiState.update { it.copy(daemonSettings = ccRepo.daemonSettings.value) }
                }
        }
    }

    /**
     * 选择桌面图标样式。
     *
     * 切换 alias 会让系统重启本进程（这是 launcher 刷新图标的前提），
     * 所以这里的 UI 状态更新只是尽量而为，界面状态以系统实际值为准。
     */
    fun setAppIcon(id: String) {
        CcAppIcon.select(id)
        _uiState.update { it.copy(iconId = id) }
    }

    /**
     * 修改 daemon 全局设置里的一个布尔开关。
     *
     * 键名直接沿用 `/settings` 的字段名，省掉一层枚举映射。
     */
    fun setDaemonFlag(key: String, value: Boolean) {
        viewModelScope.launch {
            // /settings 是差量 PATCH，只需发被改的这一个字段
            ccRepo.updateDaemonSettings(JSONObject().put(key, value))
                .onSuccess {
                    _uiState.update { it.copy(daemonSettings = ccRepo.daemonSettings.value) }
                }
        }
    }

    fun setUiMode(mode: String) {
        val oldMode = repo.uiMode
        val currentThemeMode = repo.themeMode

        val newThemeMode = when (oldMode) {
            "material" if mode == "miuix" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
                if (repo.miuixMonet && !colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toMonetMode()
                } else if (!repo.miuixMonet && colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toNonMonetMode()
                } else baseMode
            }

            "miuix" if mode == "material" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                if (colorMode.isMonet) {
                    colorMode.toNonMonetMode()
                } else currentThemeMode
            }

            else -> currentThemeMode
        }

        repo.uiMode = mode
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(uiMode = mode, themeMode = newThemeMode) }
    }

    fun setThemeMode(mode: Int) {
        val currentUiMode = repo.uiMode
        val effectiveMode = if (currentUiMode == "miuix" && _uiState.value.miuixMonet) {
            mode + 3
        } else {
            mode
        }
        repo.themeMode = effectiveMode
        _uiState.update { it.copy(themeMode = effectiveMode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }

    fun setMiuixMonet(enabled: Boolean) {
        val currentThemeMode = repo.themeMode
        val colorMode = ColorMode.fromValue(currentThemeMode)
        val newThemeMode = if (enabled) {
            if (!colorMode.isMonet) colorMode.toMonetMode() else currentThemeMode
        } else {
            if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
        }
        repo.miuixMonet = enabled
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(miuixMonet = enabled, themeMode = newThemeMode) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    fun setEnableBlur(enabled: Boolean) {
        repo.enableBlur = enabled
        _uiState.update { it.copy(enableBlur = enabled) }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repo.enableFloatingBottomBar = enabled
        _uiState.update { it.copy(enableFloatingBottomBar = enabled) }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repo.enableFloatingBottomBarBlur = enabled
        _uiState.update { it.copy(enableFloatingBottomBarBlur = enabled) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }

    // ---------- CoolerControl ----------

    fun setAutoEndpoint(enabled: Boolean) {
        ccPrefs.autoSelectEndpoint = enabled
        _uiState.update { it.copy(autoSelectEndpoint = enabled) }
        if (enabled) {
            viewModelScope.launch { ccSession.reselect() }
        }
    }

    /** 清除本地数据：服务器配置、凭据、布局设置全部删除 */
    fun clearData() {
        // 先断开实时流：会话马上要被清掉，留着已认证的长连接既无意义，
        // 也会让 SSE 循环因取不到 api 而退化成一秒一次的空转
        ccRepo.stopRealtime()
        ccPrefs.clearAll()
        ccSession.clear()
        ccRepo.clearHistory()
        _uiState.update {
            it.copy(serverName = null, endpointLabel = null, daemonVersion = null)
        }
    }

    /** 退出登录：仅清除会话与当前服务器选择，保留已保存的服务器列表 */
    fun logout() {
        // 同理：登出后不应继续持有与服务器的已认证长连接
        ccRepo.stopRealtime()
        ccPrefs.activeServerId = null
        ccSession.clear()
        _uiState.update {
            it.copy(serverName = null, endpointLabel = null, daemonVersion = null)
        }
    }

}
