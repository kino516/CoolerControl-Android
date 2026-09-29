package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 跨风格的内容对话框：Miuix 走 `WindowDialog`，Material 走 `AlertDialog`。
 *
 * 用于温度详情、风扇控制、通道选择等需要自定义内容的弹窗。
 */
@Composable
fun CcContentDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return

    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                DialogButtons(
                    confirmText = confirmText,
                    confirmEnabled = confirmEnabled,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
            }
        }

        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            },
            confirmButton = {
                if (confirmText != null && onConfirm != null) {
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(confirmText)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cc_close))
                }
            },
        )
    }
}

@Composable
private fun DialogButtons(
    confirmText: String?,
    confirmEnabled: Boolean,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            text = stringResource(R.string.cc_close),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        if (confirmText != null && onConfirm != null) {
            Spacer(Modifier.width(16.dp))
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/** 对话框内的小节标题 */
@Composable
fun CcDialogLabel(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(
            text = text,
            fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
