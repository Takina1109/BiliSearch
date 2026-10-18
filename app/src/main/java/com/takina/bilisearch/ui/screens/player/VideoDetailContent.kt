package com.takina.bilisearch.ui.screens.player

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.takina.bilisearch.data.model.UgcSeason
import com.takina.bilisearch.data.model.VideoDetail
import com.takina.bilisearch.data.repo.CommentEntry
import com.takina.bilisearch.data.repo.UpInfo
import com.takina.bilisearch.data.repo.countText
import com.takina.bilisearch.data.repo.dateText
import com.takina.bilisearch.ui.components.LoadingRow
import com.takina.bilisearch.ui.components.UserAvatar

/** 视频下方的概要 + 互动 + 合集 + 评论 */
@Composable
fun VideoDetailContent(
    state: PlayerUiState,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenEpisode: (Long) -> Unit = {},
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onSortChange: (Int) -> Unit,
    onLoadMoreComments: () -> Unit,
    onLikeComment: (CommentEntry) -> Unit,
    onReplyComment: (CommentEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = state.detail
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.comments.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (state.comments.isNotEmpty() && lastVisible >= state.comments.size - 3) {
                    onLoadMoreComments()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        if (detail != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = detail.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.isBangumi) buildString {
                            val count = detail.ugcSeason?.epCount ?: 0
                            if (count > 0) append("共 $count 集")
                            detail.owner?.name?.takeIf { it.isNotBlank() }?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        } else buildString {
                            append("${(detail.stat?.view ?: 0).countText()} 播放")
                            append(" · ${(detail.stat?.danmaku ?: 0).countText()} 弹幕")
                            append(" · ${detail.pubdate.dateText()}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                if (state.isBangumi) {
                    // 番剧的点赞/投币/收藏走的是另一套接口，这里只保留分享
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ActionItem(Icons.Filled.Share, "分享", onClick = onShare)
                    }
                } else {
                    ActionRow(
                        likeCount = detail.stat?.like ?: 0,
                        coinCount = detail.stat?.coin ?: 0,
                        favCount = detail.stat?.favorite ?: 0,
                        liked = state.liked,
                        coined = state.coined,
                        favorited = state.favorited,
                        watchLater = state.watchLater,
                        onLike = onLike,
                        onCoin = onCoin,
                        onFavorite = onFavorite,
                        onWatchLater = onWatchLater,
                        onShare = onShare
                    )
                }
            }

            item {
                UpRow(
                    mid = detail.owner?.mid ?: 0,
                    name = detail.owner?.name.orEmpty(),
                    avatar = detail.owner?.face,
                    up = state.up,
                    following = state.following,
                    onFollow = onFollow,
                    onOpenUser = {
                        // 番剧的 UP 主信息可能缺失，mid=0 会让个人主页直接 500
                        val upMid = detail.owner?.mid ?: 0
                        if (upMid > 0) onOpenUser(upMid)
                    }
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            if (!detail.desc.isNullOrBlank()) {
                item {
                    Text(
                        text = detail.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            val season = detail.ugcSeason
            if (season != null) {
                item {
                    CollectionSection(
                        season = season,
                        currentBvid = detail.bvid.orEmpty(),
                        onOpenVideo = onOpenVideo,
                        onOpenEpisode = onOpenEpisode
                    )
                }
            }
        }

        item {
            CommentHeader(
                total = if (state.commentsTotal > 0) state.commentsTotal
                else (detail?.stat?.reply ?: 0).toInt(),
                sort = state.commentSort,
                onSortChange = onSortChange
            )
        }

        items(count = state.comments.size) { index ->
            val comment = state.comments[index]
            CommentRow(
                comment = comment,
                onOpenUser = onOpenUser,
                onLike = { onLikeComment(comment) },
                onReply = { onReplyComment(comment) }
            )
        }

        if (state.commentsLoading) {
            item { LoadingRow() }
        }

        if (!state.commentsLoading && state.comments.isEmpty()) {
            item {
                Text(
                    text = "还没有评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    likeCount: Long,
    coinCount: Long,
    favCount: Long,
    liked: Boolean,
    coined: Boolean,
    favorited: Boolean,
    watchLater: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionItem(
            Icons.Filled.ThumbUp,
            (if (liked) likeCount + 1 else likeCount).countText(),
            active = liked,
            onClick = onLike
        )
        ActionItem(
            Icons.Filled.MonetizationOn,
            if (coined) "已投币" else coinCount.countText(),
            active = coined,
            onClick = onCoin
        )
        ActionItem(
            Icons.Filled.Star,
            favCount.countText(),
            active = favorited,
            onClick = onFavorite
        )
        ActionItem(
            Icons.Filled.WatchLater,
            if (watchLater) "已加入" else "稍后看",
            active = watchLater,
            onClick = onWatchLater
        )
        ActionItem(Icons.Filled.Share, "分享", onClick = onShare)
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun UpRow(
    mid: Long,
    name: String,
    avatar: String?,
    up: UpInfo?,
    following: Boolean,
    onFollow: () -> Unit,
    onOpenUser: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenUser),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(face = avatar, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "UID: $mid · 粉丝 ${(up?.followers ?: 0).toLong().countText()} · 视频 ${(up?.videos ?: 0).toLong().countText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Button(onClick = onFollow) {
            Text(if (following) "已关注" else "关注")
        }
    }
}

@Composable
private fun CollectionSection(
    season: UgcSeason,
    currentBvid: String,
    onOpenVideo: (String) -> Unit,
    onOpenEpisode: (Long) -> Unit = {}
) {
    val episodes = season.sections.orEmpty().flatMap { it.episodes.orEmpty() }
    val listState = rememberLazyListState()
    // 当前集在合集里的位置；打开第 18 集时列表直接定位到第 18 集，不用手动拖
    val currentIndex = remember(episodes, currentBvid) {
        episodes.indexOfFirst { it.bvid == currentBvid }
    }
    LaunchedEffect(currentIndex, episodes.size) {
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "合集 · ${season.title.orEmpty()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "共 ${episodes.size} 个视频",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        // 和 B 站一样左右滑动选集，一集一个小方框
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(episodes) { index, episode ->
                val isCurrent = episode.bvid == currentBvid
                val background = if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(background)
                        .clickable(enabled = !isCurrent) {
                            if (episode.epId > 0) {
                                onOpenEpisode(episode.epId)
                            } else {
                                episode.bvid?.let(onOpenVideo)
                            }
                        }
                        .padding(8.dp)
                ) {
                    Text(
                        text = "P${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = episode.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTime(episode.duration * 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentHeader(total: Int, sort: Int, onSortChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "评论 ${total.toLong().countText()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        SortTab("最热", sort == SORT_HOT) { onSortChange(SORT_HOT) }
        Spacer(Modifier.width(12.dp))
        SortTab("最新", sort == SORT_NEWEST) { onSortChange(SORT_NEWEST) }
    }
}

@Composable
private fun SortTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun CommentRow(
    comment: CommentEntry,
    onOpenUser: (Long) -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit
) {
    val openUser = { if (comment.mid > 0) onOpenUser(comment.mid) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 点整条评论就是回复这条（B 站同款交互）
            .clickable(onClick = onReply)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(face = comment.avatar, size = 34.dp, onClick = openUser)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = comment.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = openUser)
            )
            Spacer(Modifier.height(2.dp))
            Text(text = comment.message, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(comment.timeText)
                        if (comment.replyCount > 0) {
                            append(" · ")
                            append(comment.replyCount)
                            append(" 回复")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                CommentLikeButton(
                    liked = comment.liked,
                    count = comment.like,
                    onClick = onLike
                )
            }
        }
    }
}

/** 评论小拇指点赞按钮 */
@Composable
private fun CommentLikeButton(liked: Boolean, count: Long, onClick: () -> Unit) {
    val tint = if (liked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (liked) "取消点赞" else "点赞",
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.countText(),
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

/** 视频封面（暂未直接使用，留作后续相关推荐） */
@Composable
internal fun CoverThumb(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun VideoDetail.playUrlString(bvid: String): String =
    "https://www.bilibili.com/video/${this.bvid ?: bvid}"
