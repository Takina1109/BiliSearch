package com.takina.bilisearch.ui.screens.user

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.ui.components.AvatarViewerDialog
import com.takina.bilisearch.ui.components.ErrorView
import com.takina.bilisearch.ui.components.LoadingBox
import com.takina.bilisearch.ui.components.ProfileEntryRow
import com.takina.bilisearch.ui.components.ProfileHeaderCard
import com.takina.bilisearch.ui.components.ProfileSectionTitle
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.components.shareText
import com.takina.bilisearch.ui.components.spaceUrl

/**
 * 其他用户个人主页（App 内展示，不跳浏览器）。
 */
@Composable
fun UserProfileScreen(
    mid: Long,
    onBack: () -> Unit,
    onOpenSubmissions: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenBangumis: () -> Unit = {},
    vm: UserProfileViewModel = viewModel(factory = UserProfileViewModelFactory(mid))
) {
    val state = vm.state
    val profile = state.profile
    var showAvatar by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.consumeMessage()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = profile.name.ifBlank { "个人主页" },
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("分享") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                context.shareText(
                                    spaceUrl(profile.mid.takeIf { it > 0 } ?: mid),
                                    profile.name.ifBlank { "个人主页" }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (state.blocked) "移出黑名单" else "加入黑名单")
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Block, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                vm.toggleBlock()
                            }
                        )
                    }
                }
            }
        )

        when {
            state.loading && profile.mid == 0L -> LoadingBox(Modifier.weight(1f))

            state.error != null && profile.mid == 0L -> Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ErrorView(state.error.orEmpty()) { vm.load() }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    ProfileHeaderCard(
                        profile = profile,
                        showExp = false,
                        onAvatarClick = { showAvatar = true },
                        onFollowing = onOpenFollowing,
                        onFollowers = onOpenFollowers
                    )
                }

                if (profile.sign.isNotBlank()) {
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

                item { HorizontalDivider() }
                item { ProfileSectionTitle("我的内容") }
                item {
                    ProfileEntryRow(Icons.Filled.VideoLibrary, "TA的投稿", onOpenSubmissions)
                }
                item {
                    ProfileEntryRow(Icons.Filled.Star, "TA的收藏", onOpenFavorites)
                }
                item {
                    ProfileEntryRow(Icons.Filled.CollectionsBookmark, "TA的合集", onOpenCollections)
                }
                item {
                    ProfileEntryRow(Icons.Filled.LiveTv, "TA的追番", onOpenBangumis)
                }
            }
        }
    }

    if (showAvatar) {
        AvatarViewerDialog(
            faceUrl = profile.face,
            name = profile.name,
            onDismiss = { showAvatar = false }
        )
    }
}
