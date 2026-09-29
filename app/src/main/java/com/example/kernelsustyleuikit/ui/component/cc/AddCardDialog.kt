package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R

/**
 * 「添加卡片」对话框：列出当前未显示的卡片，点一项即添加。
 *
 * 与冷却 / 监控页的区块添加共用同一形态（标题 + 列表）。
 */
@Composable
fun AddCardDialog(
    show: Boolean,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    CcContentDialog(
        show = show,
        title = stringResource(R.string.cc_add_card),
        onDismiss = onDismiss,
    ) {
        if (options.isEmpty()) {
            CcText(
                text = stringResource(R.string.cc_no_more_cards),
                style = CcTextStyle.Subtitle,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { (id, title) ->
                    CcText(
                        text = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(id) }
                            .padding(vertical = 10.dp),
                        style = CcTextStyle.Body,
                    )
                }
            }
        }
    }
}
