package com.example.kernelsustyleuikit.ui.screen.login

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.kernelsustyleuikit.data.remote.CcNetworkBinder
import com.example.kernelsustyleuikit.ui.component.cc.CcPillButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcEndpointField
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextField
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import com.example.kernelsustyleuikit.ui.viewmodel.LoginViewModel

/**
 * 登录页：内网地址（必填，需带端口）+ 外网地址（可选，端口可留空）+ 密码。
 *
 * 协议做成 `http` / `https` 切换按钮；粘贴整串网址时会自动识别协议并拆出端口。
 * 用户名固定 `CCAdmin`，界面上明示、不提供输入框。
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val viewModel = viewModel<LoginViewModel>()
    val lan by viewModel.lanAddress.collectAsStateWithLifecycle()
    val wan by viewModel.wanAddress.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.prefillFromSaved()
    }

    // 本地网络权限：Android 16 起访问内网必须持有。
    // 缺失时系统会静默丢弃内网流量（浏览器能打开、APP 却连接超时），
    // 所以必须给出显式入口，不能只让用户自己去猜。
    val context = LocalContext.current
    var hasLocalNetwork by remember {
        mutableStateOf(CcNetworkBinder.hasLocalNetworkPermission())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocalNetwork = granted }

    // 用户可能刚从系统设置授权回来，恢复时重新检查一次
    LifecycleResumeEffect(Unit) {
        hasLocalNetwork = CcNetworkBinder.hasLocalNetworkPermission()
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            CcText(text = "CoolerControl", style = CcTextStyle.LargeValue)
            CcText(text = stringResource(R.string.cc_login_subtitle), style = CcTextStyle.Subtitle)

            CcCard {
                // 说明内外网的关系：两个地址填任意一个即可登录
                CcText(
                    text = stringResource(R.string.cc_address_pair_hint),
                    style = CcTextStyle.Caption,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                // 内网地址：端口需带（不带会在点登录时提示）
                CcEndpointField(
                    label = stringResource(R.string.cc_lan_address),
                    address = lan,
                    onAddressChange = {
                        viewModel.lanAddress.value = it
                        viewModel.clearError()
                    },
                    hostPlaceholder = stringResource(R.string.cc_lan_address_hint),
                    portPlaceholder = stringResource(R.string.cc_port_hint),
                )

                // 外网地址：端口可留空（留空则按协议默认端口请求）
                CcEndpointField(
                    label = stringResource(R.string.cc_wan_address),
                    address = wan,
                    onAddressChange = {
                        viewModel.wanAddress.value = it
                        viewModel.clearError()
                    },
                    modifier = Modifier.padding(top = 14.dp),
                    hostPlaceholder = stringResource(R.string.cc_wan_address_hint),
                    portPlaceholder = stringResource(R.string.cc_port_hint),
                )

                CcText(
                    text = stringResource(R.string.cc_password),
                    style = CcTextStyle.Caption,
                    modifier = Modifier.padding(top = 14.dp),
                )
                CcTextField(
                    value = password,
                    onValueChange = {
                        viewModel.password.value = it
                        viewModel.clearError()
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                CcText(
                    text = stringResource(R.string.cc_username_fixed),
                    style = CcTextStyle.Caption,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (!hasLocalNetwork) {
                CcCard {
                    CcText(
                        text = stringResource(R.string.cc_permission_local_network),
                        style = CcTextStyle.Body,
                    )
                    CcText(
                        text = stringResource(R.string.cc_permission_local_network_desc),
                        style = CcTextStyle.Caption,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CcPillButton(
                            text = stringResource(R.string.cc_permission_grant),
                            onClick = {
                                permissionLauncher.launch(CcNetworkBinder.LOCAL_NETWORK_PERMISSION)
                            },
                        )
                        CcPillButton(
                            text = stringResource(R.string.cc_permission_open_settings),
                            onClick = {
                                // 权限被永久拒绝后系统不再弹窗，只能去设置页手动开
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            },
                            filled = false,
                        )
                    }
                }
            }

            error?.let { message ->
                CcText(
                    text = message,
                    style = CcTextStyle.Body,
                    color = CcTemperatureStyle.Red,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { viewModel.login(onLoggedIn) },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.cc_connecting),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(text = stringResource(R.string.cc_connect_login))
                }
            }

            CcText(
                text = stringResource(R.string.cc_version) + " 1.0.0",
                style = CcTextStyle.Caption,
            )
        }
    }
}
