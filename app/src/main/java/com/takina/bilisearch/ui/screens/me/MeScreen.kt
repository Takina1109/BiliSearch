package com.takina.bilisearch.ui.screens.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.ui.components.ProfileEntryRow
import com.takina.bilisearch.ui.components.ProfileHeaderCard
import com.takina.bilisearch.ui.components.ProfileSectionTitle
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.components.UserAvatar
import com.takina.bilisearch.ui.components.shareText
import com.takina.bilisearch.ui.components.spaceUrl
import com.takina.bilisearch.ui.navigation.ListKind
import com.takina.bilisearch.ui.screens.profile.ProfileViewModel

@Composable
fun MeScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenList: (ListKind, Long, String) -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val state = vm.state
    val profile = state.profile

    // 从登录页 / 编辑资料页返回时会重新进入组合，这里顺便刷新资料
    LaunchedEffect(Unit) { vm.refresh() }

    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "我的",
            onBack = onBack,
            actions = {
                if (profile.isLogin) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("分享我的主页") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    context.shareText(
                                        spaceUrl(profile.mid),
                                        profile.name.ifBlank { "我的主页" }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                when {
                    profile.isLogin -> ProfileHeaderCard(
                        profile = profile,
                        showExp = true,
                        onAvatarClick = onEditProfile,
                        onFollowing = { onOpenList(ListKind.Following, -1L, "关注") },
                        onFollowers = { onOpenList(ListKind.Followers, -1L, "粉丝") }
                    )
                    // 正在加载且还没有缓存资料时，不要闪出「未登录」
                    state.loading -> LoadingHeader()
                    else -> LoginHeader(onLogin = onLogin)
                }
            }

            if (profile.isLogin && profile.sign.isNotBlank()) {
                item {
                    Text(
                        text = profile.sign,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp)
                    )
                }
            }

            if (state.error != null) {
                item {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("我的内容") }
            item {
                ProfileEntryRow(Icons.Filled.History, "观看记录") {
                    onOpenList(ListKind.History, -1L, "观看记录")
                }
            }
            item {
                ProfileEntryRow(Icons.Filled.VideoLibrary, "我的投稿") {
                    onOpenList(ListKind.Submissions, -1L, "我的投稿")
                }
            }
            item {
                ProfileEntryRow(Icons.Filled.Star, "我的收藏") {
                    onOpenList(ListKind.FavoriteFolders, -1L, "我的收藏")
                }
            }
            item {
                ProfileEntryRow(Icons.Filled.CollectionsBookmark, "合集") {
                    onOpenList(ListKind.Collections, -1L, "我的合集")
                }
            }
            item {
                ProfileEntryRow(Icons.Filled.WatchLater, "稍后再看") {
                    onOpenList(ListKind.WatchLater, -1L, "稍后再看")
                }
            }
            item {
                ProfileEntryRow(Icons.Filled.LiveTv, "我的追番") {
                    onOpenList(ListKind.Bangumis, -1L, "我的追番")
                }
            }
        }
    }
}

@Composable
private fun LoadingHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(face = null, size = 60.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "加载中…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "正在获取账号信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoginHeader(onLogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(face = null, size = 60.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "未登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "登录后可查看关注、收藏、历史与订阅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onLogin) { Text("登录") }
    }
}
