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
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R

@Immutable
data class CcSelectOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val subtitle: String? = null,
)

/**
 * 多选对话框：曲线显隐、风扇显隐、监控通道选择共用。
 *
 * 每勾选一次立即回调，方便调用方即时持久化。
 */
@Composable
fun CcMultiSelectDialog(
    show: Boolean,
    title: String,
    options: List<CcSelectOption>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
) {
    CcContentDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(option.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = option.selected,
                        onCheckedChange = { onToggle(option.id) },
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        CcText(text = option.label, style = CcTextStyle.Body)
                        option.subtitle?.let {
                            CcText(text = it, style = CcTextStyle.Caption)
                        }
                    }
                }
            }
        }
    }
}

/** 单行文本输入对话框（重命名等） */
@Composable
fun CcInputDialog(
    show: Boolean,
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }

    CcContentDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cc_save),
        onConfirm = { onConfirm(value) },
    ) {
        CcText(text = label, style = CcTextStyle.Caption)
        CcTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
