package com.takina.bilisearch.ui.screens.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.ui.components.EmptyView
import com.takina.bilisearch.ui.components.ErrorView
import com.takina.bilisearch.ui.components.LoadingBox
import com.takina.bilisearch.ui.components.LoadingRow
import com.takina.bilisearch.ui.components.RowItem
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.navigation.ListKind

@Composable
fun ContentListScreen(
    kind: ListKind,
    folderId: Long,
    userMid: Long,
    title: String,
    onBack: () -> Unit,
    onOpenFolder: (Long, String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenCollection: (Long, String) -> Unit = { _, _ -> },
    onOpenVideo: (String) -> Unit = {},
    onOpenBangumi: (seasonId: Long, epId: Long) -> Unit = { _, _ -> }
) {
    val vm: ContentListViewModel = viewModel(
        factory = ContentListViewModelFactory(kind, folderId, userMid)
    )
    val state = vm.state
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (state.rows.isNotEmpty() && lastVisible >= state.rows.size - 3) {
                    vm.load(append = true)
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = title, onBack = onBack)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && state.rows.isEmpty() -> LoadingBox()

                state.error != null && state.rows.isEmpty() ->
                    ErrorView(state.error.orEmpty()) { vm.load() }

                state.rows.isEmpty() -> EmptyView()

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(count = state.rows.size) { index ->
                        RowItem(
                            row = state.rows[index],
                            onOpenFolder = { folder -> onOpenFolder(folder.id, folder.title) },
                            onOpenUser = onOpenUser,
                            onOpenCollection = { collection ->
                                onOpenCollection(collection.id, collection.title)
                            },
                            onOpenVideo = onOpenVideo,
                            onOpenBangumi = onOpenBangumi
                        )
                    }
                    if (state.loadingMore) {
                        item { LoadingRow() }
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
