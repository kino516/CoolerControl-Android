package com.example.kernelsustyleuikit.ui.screen.monitor

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.ui.component.cc.CcDraggableCardList
import com.example.kernelsustyleuikit.ui.screen.home.AddCardRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorPagerMaterial(
    state: MonitorUiState,
    editMode: Boolean,
    callbacks: MonitorCallbacks,
    bottomInnerPadding: Dp,
    onEditModeChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onAddSection: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monitor)) },
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
        CcDraggableCardList(
            items = state.sections,
            itemKey = { it },
            editMode = editMode,
            onOrderChange = onOrderChange,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
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
