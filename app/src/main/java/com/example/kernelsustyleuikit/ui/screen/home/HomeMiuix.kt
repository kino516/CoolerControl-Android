package com.example.kernelsustyleuikit.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.component.cc.CcDraggableCardList
import com.example.kernelsustyleuikit.ui.component.cc.CcRunningMan
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.component.cc.ccPrimaryColor
import com.example.kernelsustyleuikit.ui.component.miuix.WarningCard
import com.example.kernelsustyleuikit.ui.theme.LocalEnableBlur
import com.example.kernelsustyleuikit.ui.util.BlurredBar
import com.example.kernelsustyleuikit.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    editMode: Boolean,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    onEditModeChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onAddCard: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.home),
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
                items = state.cards,
                itemKey = { it.id },
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
                header = { HomeStatusHeader(state) },
                footer = { if (editMode) AddCardRow(onAddCard) },
            ) { card, isEdit ->
                HomeCardItem(
                    card = card,
                    deviceType = (card as? HomeCardUi.Temperature)?.deviceType.orEmpty(),
                    editMode = isEdit,
                    actions = actions,
                    onRemove = { onRemove(card.id) },
                )
            }

            // 冷启动加载态：盖在列表之上，避免先闪一下「没有设备」的空列表，
            // 等元数据回来再突然冒出十几张卡片
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CcRunningMan()
                }
            }
        }
    }
}

/** 顶部状态提示：连接异常时给出明确原因，不静默失败；无卡片时给出空态 */
@Composable
internal fun HomeStatusHeader(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.errorMessage?.let { message ->
            WarningCard(message)
        }
        if (!state.connected) {
            WarningCard(stringResource(R.string.cc_not_connected))
        }
        if (state.alertActive > 0) {
            WarningCard(stringResource(R.string.cc_alerts_active, state.alertActive))
        }
        if (state.cards.isEmpty()) {
            EmptyCardsHint()
        }
    }
}

/** 编辑模式下的「添加卡片」入口 */
@Composable
internal fun AddCardRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = ccPrimaryColor(),
        )
        Spacer(Modifier.width(6.dp))
        CcText(
            text = stringResource(R.string.cc_add_card),
            style = CcTextStyle.Body,
            color = ccPrimaryColor(),
        )
    }
}

/** 空态：还没有任何卡片 */
@Composable
internal fun EmptyCardsHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CcText(text = stringResource(R.string.cc_no_cards), style = CcTextStyle.Subtitle)
    }
}
