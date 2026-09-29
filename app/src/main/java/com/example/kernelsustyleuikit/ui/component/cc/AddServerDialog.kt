package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcAddress
import com.example.kernelsustyleuikit.data.model.CcScheme

/**
 * 添加服务器对话框（PRD FR-1.3）。
 *
 * 与登录页共用 [CcEndpointField]，字段与规则完全一致：
 * 内网地址（必填，需带端口）+ 外网地址（可选，端口可留空）+ 密码。
 * 用户名固定 `CCAdmin`，不提供输入框。
 */
@Composable
fun AddServerDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (lan: CcAddress, wan: CcAddress, password: String) -> Unit,
) {
    var lan by remember(show) { mutableStateOf(CcAddress(scheme = CcScheme.Http)) }
    var wan by remember(show) { mutableStateOf(CcAddress(scheme = CcScheme.Https)) }
    var password by remember(show) { mutableStateOf("") }

    CcContentDialog(
        show = show,
        title = stringResource(R.string.cc_server_add),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cc_connect_login),
        onConfirm = { onSubmit(lan, wan, password) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CcEndpointField(
                label = stringResource(R.string.cc_lan_address),
                address = lan,
                onAddressChange = { lan = it },
                hostPlaceholder = stringResource(R.string.cc_lan_address_hint),
                portPlaceholder = stringResource(R.string.cc_port_hint),
            )

            CcEndpointField(
                label = stringResource(R.string.cc_wan_address),
                address = wan,
                onAddressChange = { wan = it },
                modifier = Modifier.padding(top = 8.dp),
                hostPlaceholder = stringResource(R.string.cc_wan_address_hint),
                portPlaceholder = stringResource(R.string.cc_port_hint),
            )

            CcText(
                text = stringResource(R.string.cc_password),
                style = CcTextStyle.Caption,
                modifier = Modifier.padding(top = 8.dp),
            )
            CcTextField(
                value = password,
                onValueChange = { password = it },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            CcText(
                text = stringResource(R.string.cc_username_fixed),
                style = CcTextStyle.Caption,
            )
        }
    }
}
