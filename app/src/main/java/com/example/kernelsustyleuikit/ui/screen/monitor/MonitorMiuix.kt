package com.example.kernelsustyleuikit.ui.screen.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.component.cc.CcDraggableCardList
import com.example.kernelsustyleuikit.ui.component.miuix.WarningCard
import com.example.kernelsustyleuikit.ui.screen.home.AddCardRow
import com.example.kernelsustyleuikit.ui.theme.LocalEnableBlur
import com.example.kernelsustyleuikit.ui.util.BlurredBar
import com.example.kernelsustyleuikit.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun MonitorPagerMiuix(
    state: MonitorUiState,
    editMode: Boolean,
    callbacks: MonitorCallbacks,
    bottomInnerPadding: Dp,
    onEditModeChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onAddSection: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.monitor),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { onEditModeChange(!editMode) }) {
                            Icon(
                                imageVector = if (editMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                                contentDescription = stringResource(
                                    if (editMode) R.string.cc_done else R.string.cc_edit
                                ),
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            CcDraggableCardList(
                items = state.sections,
                itemKey = { it },
                editMode = editMode,
                onOrderChange = onOrderChange,
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                bottomPadding = bottomInnerPadding,
                header = { MonitorHealthHeader(state) },
                footer = { if (editMode) AddCardRow(onAddSection) },
            ) { sectionId, isEdit ->
                MonitorSectionContent(
                    sectionId = sectionId,
                    state = state,
                    callbacks = callbacks,
                    editMode = isEdit,
                    onRemove = { onRemove(sectionId) },
                )
            }
        }
    }
}

/** 健康告警横幅：仅在存在异常时出现 */
@Composable
internal fun MonitorHealthHeader(state: MonitorUiState) {
    if (state.healthIssueCount > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WarningCard(stringResource(R.string.cc_health_issue, state.healthIssueCount))
        }
    }
}
