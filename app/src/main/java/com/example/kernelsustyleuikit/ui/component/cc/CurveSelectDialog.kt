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
import com.example.kernelsustyleuikit.data.model.CcProfile

/** 曲线选择对话框（风扇控制与冷却页共用） */
@Composable
fun CurveSelectDialog(
    show: Boolean,
    profiles: List<CcProfile>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    CcContentDialog(
        show = show,
        title = stringResource(R.string.cc_select_curve),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            profiles.forEach { profile ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(profile.uid) }
                        .padding(vertical = 8.dp)
                ) {
                    CcText(text = profile.name, style = CcTextStyle.Body)
                    val detail = buildString {
                        if (profile.speedProfile.isNotEmpty()) {
                            append(stringResource(R.string.cc_profile_points, profile.speedProfile.size))
                        }
                    }
                    if (detail.isNotEmpty()) {
                        CcText(text = detail, style = CcTextStyle.Caption)
                    }
                }
            }
        }
    }
}

/** 对话框内的文字按钮 */
@Composable
fun CcActionText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CcText(
        text = text,
        modifier = modifier.clickable(onClick = onClick),
        style = CcTextStyle.Body,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    )
}
