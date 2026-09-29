package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.data.model.CcAddress
import com.example.kernelsustyleuikit.data.model.CcScheme

/**
 * 三段式地址输入：`[http│https] [主机] [:端口]`。
 *
 * 登录页与「添加服务器」对话框共用，保证两处规则完全一致。
 *
 * - 协议：手动点选，或在主机框里输入/粘贴整串网址时自动切换；
 * - 主机：粘贴 `http://10.0.0.53:11987` 会自动拆成协议 + 主机 + 端口；
 * - 端口：单独输入框，只接受数字；留空时按协议默认端口（80 / 443）请求。
 */
@Composable
fun CcEndpointField(
    label: String,
    address: CcAddress,
    onAddressChange: (CcAddress) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    hostPlaceholder: String? = null,
    portPlaceholder: String? = null,
    showPreview: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CcText(text = label, style = CcTextStyle.Caption)
            if (!hint.isNullOrBlank()) {
                CcText(
                    text = hint,
                    style = CcTextStyle.Caption,
                    color = ccSecondaryTextColor(),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcSchemeToggle(
                scheme = address.scheme,
                onSchemeChange = { onAddressChange(address.copy(scheme = it)) },
            )
            CcTextField(
                value = address.host,
                onValueChange = { input ->
                    val parsed = CcAddress.parseHostInput(input, address.host, address.scheme)
                    onAddressChange(parsed ?: address.copy(host = input))
                },
                modifier = Modifier.weight(1f),
                placeholder = hostPlaceholder,
            )
            CcTextField(
                value = address.port,
                onValueChange = { input ->
                    onAddressChange(address.copy(port = input.filter { it.isDigit() }.take(5)))
                },
                modifier = Modifier.width(72.dp),
                placeholder = portPlaceholder,
                prefix = ":",
                fillMaxWidth = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        if (showPreview) {
            address.toUrl()?.let { url ->
                CcText(
                    text = "→ $url",
                    style = CcTextStyle.Caption,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** `http` / `https` 两段式切换按钮 */
@Composable
fun CcSchemeToggle(
    scheme: CcScheme,
    onSchemeChange: (CcScheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(ccOnSurfaceColor().copy(alpha = 0.06f))
            .border(1.dp, ccOnSurfaceColor().copy(alpha = 0.15f), shape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcScheme.values().forEach { item ->
            val selected = item == scheme
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) ccPrimaryColor() else Color.Transparent)
                    .clickable { onSchemeChange(item) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                CcText(
                    text = item.value,
                    style = CcTextStyle.Body,
                    color = if (selected) Color.White else ccSecondaryTextColor(),
                )
            }
        }
    }
}
