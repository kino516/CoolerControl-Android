package com.example.kernelsustyleuikit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.model.CcAddress
import com.example.kernelsustyleuikit.data.model.CcAddressError
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.model.CcScheme
import com.example.kernelsustyleuikit.data.model.CcServer
import com.example.kernelsustyleuikit.templateApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录页 ViewModel。
 *
 * 地址拆成「协议 + 主机 + 端口」三段：内网地址必填且必须带端口，
 * 外网地址可选、端口也可留空（留空则按协议默认端口 80 / 443 请求）。
 * 两个地址填任意一个即可登录。
 *
 * 用户名固定 `CCAdmin`（daemon 不支持自定义用户名），界面上只做说明、不提供输入框。
 */
class LoginViewModel : ViewModel() {

    private val session = CcGraph.session
    private val prefs = CcGraph.prefs

    /** 内网地址（默认 http，端口必填） */
    val lanAddress = MutableStateFlow(CcAddress(scheme = CcScheme.Http))

    /** 外网地址（默认 https，端口可留空） */
    val wanAddress = MutableStateFlow(CcAddress(scheme = CcScheme.Https))

    val password = MutableStateFlow("")

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 已有配置时预填两个地址，方便改密码重登 */
    fun prefillFromSaved() {
        val saved = prefs.activeServer() ?: return
        if (!lanAddress.value.isBlank || !wanAddress.value.isBlank) return

        var lan = saved.endpoints.firstOrNull { it.kind == CcEndpointKind.Lan }
        var wan = saved.endpoints.firstOrNull { it.kind == CcEndpointKind.Wan }
        if (lan == null && wan == null) {
            // 只有一个端点时，按它自己的类型落回对应输入框
            saved.endpoints.firstOrNull()?.let { endpoint ->
                if (endpoint.kind == CcEndpointKind.Wan) wan = endpoint else lan = endpoint
            }
        }
        lan?.let {
            lanAddress.value = CcAddress.parse(it.url, CcScheme.defaultFor(CcEndpointKind.Lan))
        }
        wan?.let {
            wanAddress.value = CcAddress.parse(it.url, CcScheme.defaultFor(CcEndpointKind.Wan))
        }
    }

    fun login(onSuccess: () -> Unit) {
        when (CcAddress.validate(lanAddress.value, wanAddress.value)) {
            CcAddressError.Empty -> {
                _error.value = templateApp.getString(R.string.cc_address_required)
                return
            }

            CcAddressError.LanPortRequired -> {
                _error.value = templateApp.getString(R.string.cc_lan_port_required)
                return
            }

            CcAddressError.PortInvalid -> {
                _error.value = templateApp.getString(R.string.cc_port_invalid)
                return
            }

            null -> Unit
        }

        if (password.value.isEmpty()) {
            _error.value = templateApp.getString(R.string.cc_password_required)
            return
        }

        val endpoints = CcAddress.endpoints(lanAddress.value, wanAddress.value)
        if (endpoints.isEmpty()) {
            _error.value = templateApp.getString(R.string.cc_address_invalid)
            return
        }

        val existing = prefs.activeServer()
        val server = CcServer(
            id = existing?.id ?: CcPrefs.newServerId(),
            name = existing?.name
                ?: endpoints.first().url.substringAfter("://").substringBefore('/'),
            endpoints = endpoints,
            passwordEnc = "",
        )

        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = session.login(server, password.value)
            _loading.value = false
            result
                .onSuccess { onSuccess() }
                .onFailure { error -> _error.value = error.message }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
