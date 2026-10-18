package com.takina.bilisearch.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.data.repo.ProfileSummary
import com.takina.bilisearch.ui.components.EmptyView
import com.takina.bilisearch.ui.components.ErrorView
import com.takina.bilisearch.ui.components.LoadingBox
import com.takina.bilisearch.ui.components.LoadingRow
import com.takina.bilisearch.ui.components.RowItem
import com.takina.bilisearch.ui.components.UserAvatar
import com.takina.bilisearch.ui.components.isDarkThemeActive
import com.takina.bilisearch.ui.components.setDarkTheme
import com.takina.bilisearch.ui.screens.profile.ProfileViewModel

@Composable
fun SearchScreen(
    onOpenMe: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenBangumi: (seasonId: Long, epId: Long) -> Unit = { _, _ -> },
    vm: SearchViewModel = viewModel(),
    profileVm: ProfileViewModel = viewModel()
) {
    val state = vm.state
    val profile = profileVm.state.profile

    // 进入首页（含从「我的」返回）时刷新头像等信息
    LaunchedEffect(Unit) { profileVm.refresh() }

    // 搜索结果相当于首页之上的一个层级：系统返回先回到搜索框主界面，而不是直接退出 App
    BackHandler(enabled = state.searched) { vm.backToLanding() }

    if (!state.searched) {
        SearchLanding(
            keyword = state.keyword,
            profile = profile,
            onKeywordChange = vm::onKeywordChange,
            onSearch = vm::submit,
            onOpenMe = onOpenMe,
            onOpenSettings = onOpenSettings
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::backToLanding) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            SearchField(
                value = state.keyword,
                onValueChange = vm::onKeywordChange,
                onSearch = vm::submit,
                modifier = Modifier.weight(1f)
            )
        }

        SearchTabs(current = state.tab, onSelect = vm::switchTab)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && state.rows.isEmpty() -> LoadingBox()

                state.error != null && state.rows.isEmpty() ->
                    ErrorView(state.error.orEmpty()) { vm.submit() }

                state.rows.isEmpty() -> EmptyView("没有找到相关内容")

                else -> SearchResultList(
                    vm = vm,
                    state = state,
                    onOpenUser = onOpenUser,
                    onOpenVideo = onOpenVideo,
                    onOpenBangumi = onOpenBangumi
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    vm: SearchViewModel,
    state: SearchUiState,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenBangumi: (seasonId: Long, epId: Long) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= state.rows.size - 3) vm.loadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
    ) {
        items(count = state.rows.size) { index ->
            RowItem(
                row = state.rows[index],
                onOpenFolder = {},
                onOpenUser = onOpenUser,
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

@Composable
private fun SearchLanding(
    keyword: String,
    profile: ProfileSummary,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val darkTheme = isDarkThemeActive()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 顶部一行：左上角头像 +（右上角）夜间模式开关、设置齿轮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左上角小头像：视觉尺寸 26dp，保留 44dp 的点击区域
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onOpenMe),
                contentAlignment = Alignment.CenterStart
            ) {
                UserAvatar(
                    face = profile.face,
                    size = 26.dp,
                    contentDescription = if (profile.isLogin) "我的主页" else "登录"
                )
            }

            Spacer(Modifier.weight(1f))

            // 夜间模式小按钮，放在齿轮左边
            LandingIconButton(
                icon = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = if (darkTheme) "切换到白天模式" else "切换到夜间模式",
                onClick = { setDarkTheme(!darkTheme) }
            )
            LandingIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "设置",
                onClick = onOpenSettings
            )
        }

        // 上方 38.2% / 下方 61.8%，搜索框落在黄金比例位置
        Spacer(Modifier.weight(0.382f))

        Text(
            text = "BiliSearch",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        SearchField(
            value = keyword,
            onValueChange = onKeywordChange,
            onSearch = onSearch
        )

        Spacer(Modifier.weight(0.618f))
    }
}

/** 与头像同尺寸（26dp 图标 / 44dp 点击区）的顶部图标按钮 */
@Composable
private fun LandingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            if (!placeholder.isNullOrBlank()) {
                Text(placeholder)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun SearchTabs(current: SearchTab, onSelect: (SearchTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SearchTab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(if (selected) 20.dp else 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
