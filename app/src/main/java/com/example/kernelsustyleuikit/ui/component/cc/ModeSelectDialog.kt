package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcMode

/** 主页模式卡点击后的模式单选：选中即切换 */
@Composable
fun ModeSelectDialog(
    show: Boolean,
    modes: List<CcMode>,
    activeUid: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    CcContentDialog(
        show = show,
        title = stringResource(R.string.cc_card_mode),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            modes.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode.uid) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcText(
                        text = mode.name,
                        style = CcTextStyle.Body,
                        modifier = Modifier.weight(1f),
                    )
                    if (mode.uid == activeUid) {
                        CcText(
                            text = stringResource(R.string.cc_mode_current),
                            style = CcTextStyle.Caption,
                            color = ccPrimaryColor(),
                        )
                    }
                }
            }
        }
    }
}
