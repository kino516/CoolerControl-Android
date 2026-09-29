package com.example.kernelsustyleuikit.ui.screen.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.local.CcCrypto
import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.model.CcAddress
import com.example.kernelsustyleuikit.data.model.CcAddressError
import com.example.kernelsustyleuikit.data.model.CcServer
import com.example.kernelsustyleuikit.ui.component.cc.AddServerDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcActionText
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcCardHeader
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.navigation3.LocalNavigator
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import kotlinx.coroutines.launch

/**
 * 服务器列表（PRD FR-1.3）：显示已保存的服务器，可添加、切换与删除。
 *
 * 每个服务器独立保存凭据与会话，切换时用已保存密码静默重登。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen() {
    val navigator = LocalNavigator.current
    val prefs = CcGraph.prefs
    val session = CcGraph.session
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf(prefs.servers) }
    var activeId by remember { mutableStateOf(prefs.activeServerId) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    // onSubmit 不是 composable 上下文，字符串先在这里取好
    val errEmpty = stringResource(R.string.cc_address_required)
    val errLanPort = stringResource(R.string.cc_lan_port_required)
    val errPort = stringResource(R.string.cc_port_invalid)
    val errPassword = stringResource(R.string.cc_password_required)
    val errAddress = stringResource(R.string.cc_address_invalid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cc_server_list)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cc_server_add),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (servers.isEmpty()) {
                CcCard {
                    CcText(
                        text = stringResource(R.string.cc_no_server),
                        style = CcTextStyle.Subtitle,
                    )
                }
            }

            servers.forEach { server ->
                val isActive = server.id == activeId
                CcCard {
                    CcCardHeader(
                        title = server.name,
                        subtitle = server.endpoints.joinToString("  ·  ") { it.url },
                        value = if (isActive) stringResource(R.string.cc_in_use) else null,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (!isActive) {
                            CcActionText(
                                text = stringResource(R.string.cc_switch),
                                onClick = {
                                    val password = CcCrypto.decrypt(server.passwordEnc)
                                    if (password == null) {
                                        message = "凭据不可用，请重新登录"
                                        return@CcActionText
                                    }
                                    scope.launch {
                                        session.login(server, password)
                                            .onSuccess {
                                                prefs.activeServerId = server.id
                                                activeId = server.id
                                                navigator.pop()
                                            }
                                            .onFailure { message = it.message }
                                    }
                                },
                            )
                        }
                        CcActionText(
                            text = stringResource(R.string.cc_server_delete),
                            onClick = {
                                prefs.removeServer(server.id)
                                servers = prefs.servers
                                activeId = prefs.activeServerId
                            },
                        )
                    }
                }
            }

            message?.let { text ->
                CcText(
                    text = text,
                    style = CcTextStyle.Body,
                    color = CcTemperatureStyle.Red,
                )
            }
        }
    }

    AddServerDialog(
        show = showAdd,
        onDismiss = { showAdd = false },
        onSubmit = { lan, wan, password ->
            when (CcAddress.validate(lan, wan)) {
                CcAddressError.Empty -> {
                    message = errEmpty
                    return@AddServerDialog
                }

                CcAddressError.LanPortRequired -> {
                    message = errLanPort
                    return@AddServerDialog
                }

                CcAddressError.PortInvalid -> {
                    message = errPort
                    return@AddServerDialog
                }

                null -> Unit
            }
            if (password.isEmpty()) {
                message = errPassword
                return@AddServerDialog
            }

            val endpoints = CcAddress.endpoints(lan, wan)
            val primary = endpoints.firstOrNull()?.url
            if (primary == null) {
                message = errAddress
                return@AddServerDialog
            }

            val server = CcServer(
                id = CcPrefs.newServerId(),
                name = primary.substringAfter("://").substringBefore('/'),
                endpoints = endpoints,
                passwordEnc = "",
            )
            scope.launch {
                session.login(server, password)
                    .onSuccess {
                        prefs.activeServerId = server.id
                        servers = prefs.servers
                        activeId = server.id
                        showAdd = false
                    }
                    .onFailure { message = it.message }
            }
        },
    )
}
