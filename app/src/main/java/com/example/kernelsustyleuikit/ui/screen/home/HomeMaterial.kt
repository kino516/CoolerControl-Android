package com.example.kernelsustyleuikit.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.component.cc.CcDraggableCardList
import com.example.kernelsustyleuikit.ui.component.cc.CcRunningMan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    editMode: Boolean,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    onEditModeChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onAddCard: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            // 用普通 TopAppBar：标题与右侧操作按钮保持在同一水平线上
            TopAppBar(
                title = { Text(stringResource(R.string.home)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { onEditModeChange(!editMode) }) {
                        Icon(
                            imageVector = if (editMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                            contentDescription = stringResource(
                                if (editMode) R.string.cc_done else R.string.cc_edit
                            ),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            CcDraggableCardList(
                items = state.cards,
                itemKey = { it.id },
                editMode = editMode,
                onOrderChange = onOrderChange,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 16.dp),
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

            // 冷启动加载态：盖住列表，避免先闪一下空列表再突然冒出卡片
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CcRunningMan()
                }
            }
        }
    }
}
