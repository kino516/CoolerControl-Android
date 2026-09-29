package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import com.example.kernelsustyleuikit.ui.component.material.TonalCard
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle

/**
 * 跨风格卡片容器：**每个功能区块 = 一张卡片**（PRD FR-6.4）。
 *
 * 标题、副标题、数值、图表全部在卡片内部；编辑模式下卡片**内部底部**出现
 * 右对齐的红色移除行（与主页卡片一致）。
 */
@Composable
fun CcCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    editMode: Boolean = false,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier.then(clickModifier)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
                if (editMode && onRemove != null) CcCardRemoveRow(onRemove)
            }
        }

        UiMode.Material -> TonalCard(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .then(clickModifier)
            ) {
                content()
                if (editMode && onRemove != null) CcCardRemoveRow(onRemove)
            }
        }
    }
}

/** 卡片内底部的移除行（右对齐红色 × 号） */
@Composable
fun CcCardRemoveRow(onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = CcTemperatureStyle.Red,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            CcText(
                text = stringResource(R.string.cc_remove),
                style = CcTextStyle.Caption,
                color = CcTemperatureStyle.Red,
            )
        }
    }
}

/** 卡片标题行：标题 + 副标题（左）与数值（右） */
@Composable
fun CcCardHeader(
    title: String,
    subtitle: String?,
    value: String? = null,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CcText(text = title, style = CcTextStyle.Title)
            if (!subtitle.isNullOrBlank()) {
                CcText(text = subtitle, style = CcTextStyle.Subtitle)
            }
        }
        if (value != null) {
            CcText(
                text = value,
                style = CcTextStyle.Value,
                color = valueColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
