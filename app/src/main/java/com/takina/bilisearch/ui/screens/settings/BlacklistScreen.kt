package com.takina.bilisearch.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.data.repo.UserEntry
import com.takina.bilisearch.ui.components.EmptyView
import com.takina.bilisearch.ui.components.ErrorView
import com.takina.bilisearch.ui.components.LoadingBox
import com.takina.bilisearch.ui.components.LoadingRow
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.components.UserAvatar

/** 黑名单管理：查看被拉黑的用户并解除拉黑 */
@Composable
fun BlacklistScreen(
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    vm: BlacklistViewModel = viewModel()
) {
    val state = vm.state
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.users.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (state.users.isNotEmpty() && lastVisible >= state.users.size - 3) {
                    vm.load(append = true)
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "黑名单管理", onBack = onBack)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && state.users.isEmpty() -> LoadingBox()

                state.error != null && state.users.isEmpty() ->
                    ErrorView(state.error.orEmpty()) { vm.load() }

                state.users.isEmpty() -> EmptyView("黑名单里还没有人")

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
                ) {
                    items(count = state.users.size) { index ->
                        BlackUserRow(
                            entry = state.users[index],
                            onClick = { onOpenUser(state.users[index].mid) },
                            onUnblock = { vm.unblock(state.users[index].mid) }
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

@Composable
private fun BlackUserRow(entry: UserEntry, onClick: () -> Unit, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(face = entry.avatar, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.sign.isNotBlank()) {
                Text(
                    text = entry.sign,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onUnblock) {
            Text("移除", color = MaterialTheme.colorScheme.primary)
        }
    }
}
