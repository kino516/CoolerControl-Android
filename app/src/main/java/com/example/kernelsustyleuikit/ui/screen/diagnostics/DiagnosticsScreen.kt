package com.example.kernelsustyleuikit.ui.screen.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.ui.component.cc.CcActionText
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcCardHeader
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.navigation3.LocalNavigator
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import kotlinx.coroutines.launch

/**
 * 连接诊断：逐端点显示可达性与响应延迟，用于排查「为什么连不上」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen() {
    val navigator = LocalNavigator.current
    val session = CcGraph.session
    val sessionState by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cc_diagnostics)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { session.reselect() } }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.cc_diagnostics_redetect),
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
            sessionState.probeResults.forEach { result ->
                val isCurrent = result.endpoint.url == sessionState.endpointUrl
                CcCard {
                    CcCardHeader(
                        title = result.endpoint.url,
                        subtitle = if (result.reachable) {
                            "${result.latencyMs} ms"
                        } else {
                            stringResource(R.string.cc_unreachable)
                        },
                        value = when (result.endpoint.kind) {
                            CcEndpointKind.Lan -> stringResource(R.string.cc_endpoint_lan)
                            CcEndpointKind.Wan -> stringResource(R.string.cc_endpoint_wan)
                        },
                        valueColor = if (result.reachable) {
                            CcTemperatureStyle.Green
                        } else {
                            CcTemperatureStyle.Red
                        },
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (isCurrent) {
                            CcText(
                                text = stringResource(R.string.cc_in_use),
                                style = CcTextStyle.Caption,
                                color = CcTemperatureStyle.Green,
                            )
                        } else if (result.reachable) {
                            CcActionText(
                                text = stringResource(R.string.cc_force_use),
                                onClick = {
                                    scope.launch { session.forceEndpoint(result.endpoint) }
                                },
                            )
                        }
                    }
                }
            }

            if (sessionState.probeResults.isEmpty()) {
                CcCard {
                    CcText(
                        text = stringResource(R.string.cc_loading),
                        style = CcTextStyle.Subtitle,
                    )
                }
            }

            sessionState.daemonVersion?.let { version ->
                CcCard {
                    CcText(
                        text = stringResource(R.string.cc_daemon_version, version),
                        style = CcTextStyle.Body,
                    )
                }
            }

            sessionState.errorMessage?.let { error ->
                CcCard {
                    CcText(
                        text = error,
                        style = CcTextStyle.Body,
                        color = CcTemperatureStyle.Red,
                    )
                }
            }
        }
    }
}
