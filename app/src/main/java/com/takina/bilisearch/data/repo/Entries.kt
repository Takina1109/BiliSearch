package com.takina.bilisearch.data.repo

import com.takina.bilisearch.data.model.BangumiFollow
import com.takina.bilisearch.data.model.BangumiItem
import com.takina.bilisearch.data.model.BlackUser
import com.takina.bilisearch.data.model.FavFolder
import com.takina.bilisearch.data.model.FavMedia
import com.takina.bilisearch.data.model.HistoryItem
import com.takina.bilisearch.data.model.RelationUser
import com.takina.bilisearch.data.model.ReplyItem
import com.takina.bilisearch.data.model.SeasonArchive
import com.takina.bilisearch.data.model.SeasonItem
import com.takina.bilisearch.data.model.SubmissionItem
import com.takina.bilisearch.data.model.UserItem
import com.takina.bilisearch.data.model.VideoItem
import com.takina.bilisearch.data.model.WatchLaterItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 视频类条目（搜索、历史、收藏、订阅等统一用它渲染） */
data class VideoEntry(
    val id: String,
    val title: String,
    val cover: String?,
    val subtitle: String = "",
    val meta: String = "",
    val target: String,
    /** > 0 表示番剧，可在 App 内打开播放 */
    val seasonId: Long = 0,
    /** > 0 表示具体某一集 */
    val epId: Long = 0
)

/** 用户条目（搜索结果、关注、粉丝） */
data class UserEntry(
    val mid: Long,
    val name: String,
    val avatar: String?,
    val sign: String = "",
    val meta: String = ""
)

/** 收藏夹条目 */
data class FolderEntry(
    val id: Long,
    val title: String,
    val count: Int
)

/** 合集条目 */
data class CollectionEntry(
    val id: Long,
    val title: String,
    val cover: String?,
    val subtitle: String = "",
    val count: Int = 0
)

/** UP 主概要：粉丝数、投稿数 */
data class UpInfo(
    val mid: Long = 0,
    val followers: Int = 0,
    val videos: Int = 0
)

/** 收藏抽屉里的收藏夹（带是否已收藏） */
data class FavoriteFolderEntry(
    val id: Long,
    val title: String,
    val count: Int,
    val selected: Boolean
)

/** 评论条目 */
data class CommentEntry(
    val rpid: Long,
    val mid: Long,
    val name: String,
    val avatar: String?,
    val message: String,
    val like: Long,
    val timeText: String,
    val replyCount: Int = 0,
    /** 我是否已点赞 */
    val liked: Boolean = false,
    /** 根评论 rpid；0 表示本身就是一层评论 */
    val root: Long = 0
)

/** 分页结果 */
data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 0,
    val total: Long = 0,
    val hasMore: Boolean = false,
    val cursorMax: Long = 0,
    val cursorViewAt: Long = 0
)

/** 当前登录账号的概要信息（头像区 / 我的页面共用） */
data class ProfileSummary(
    val isLogin: Boolean = false,
    val mid: Long = 0,
    val name: String = "",
    val face: String? = null,
    val level: Int = 0,
    val expCurrent: Long = 0,
    val expMin: Long = 0,
    /** null 表示满级 */
    val expNext: Long? = null,
    val coins: Double = 0.0,
    val isVip: Boolean = false,
    val vipLabelText: String = "",
    val vipLabelImage: String? = null,
    val nicknameColor: String? = null,
    val following: Int = 0,
    val followers: Int = 0,
    /** 个人签名 */
    val sign: String = ""
) {
    /** 经验条进度：0f ~ 1f */
    val expProgress: Float
        get() {
            val next = expNext ?: return 1f
            val span = next - expMin
            if (span <= 0) return 1f
            return ((expCurrent - expMin).toFloat() / span).coerceIn(0f, 1f)
        }
}

// ---------------- 工具 ----------------

internal fun String?.cleanText(): String = this.orEmpty()
    .replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

internal fun String?.toHttpsOrNull(): String? {
    val value = this?.trim().orEmpty()
    return when {
        value.isEmpty() -> null
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        value.startsWith("https://") -> value
        else -> "https://$value"
    }
}

internal fun Long.countText(): String = when {
    this >= 100_000_000 -> String.format(Locale.US, "%.1f亿", this / 100_000_000.0)
    this >= 10_000 -> String.format(Locale.US, "%.1f万", this / 10_000.0)
    else -> toString()
}

internal fun Long.durationText(): String {
    val total = if (this < 0) 0 else this
    val hour = total / 3600
    val minute = (total % 3600) / 60
    val second = total % 60
    return if (hour > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hour, minute, second)
    } else {
        String.format(Locale.US, "%02d:%02d", minute, second)
    }
}

internal fun Long.dateText(): String {
    if (this <= 0) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this * 1000))
}

internal fun Long.timeAgo(): String {
    if (this <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - this
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60} 分钟前"
        diff < 86_400 -> "${diff / 3600} 小时前"
        diff < 86_400 * 30 -> "${diff / 86_400} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this * 1000))
    }
}

// ---------------- 映射 ----------------

internal fun VideoItem.toEntry(): VideoEntry {
    val bv = bvid
    return VideoEntry(
        id = bv ?: "av$aid",
        title = title.cleanText(),
        cover = pic.toHttpsOrNull(),
        subtitle = author.cleanText(),
        meta = buildString {
            append(play.countText())
            append(" 播放")
            if (danmaku > 0) {
                append(" · ")
                append(danmaku.countText())
                append(" 弹幕")
            }
            duration?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        },
        target = "https://www.bilibili.com/video/${bv ?: "av$aid"}"
    )
}

internal fun UserItem.toEntry(): UserEntry = UserEntry(
    mid = mid,
    name = uname.cleanText(),
    avatar = upic.toHttpsOrNull(),
    sign = usign.cleanText(),
    meta = "粉丝 ${fans.countText()} · 视频 ${videos.countText()}"
)

internal fun BangumiItem.toEntry(): VideoEntry {
    val subtitle = listOfNotNull(
        seasonTypeName?.takeIf { it.isNotBlank() },
        areas?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    val target = if (seasonId > 0) {
        "https://www.bilibili.com/bangumi/play/ss$seasonId"
    } else {
        "https://www.bilibili.com/bangumi/media/md$mediaId"
    }

    return VideoEntry(
        id = "md$mediaId",
        title = title.cleanText(),
        cover = cover.toHttpsOrNull(),
        subtitle = subtitle,
        meta = indexShow.cleanText(),
        target = target,
        seasonId = seasonId
    )
}

internal fun HistoryItem.toEntry(): VideoEntry {
    val bv = history?.bvid
    val oid = history?.oid ?: 0L
    return VideoEntry(
        id = bv ?: "av$oid",
        title = title.cleanText(),
        cover = cover.toHttpsOrNull(),
        subtitle = authorName.cleanText(),
        meta = viewAt.timeAgo(),
        target = "https://www.bilibili.com/video/${bv ?: "av$oid"}"
    )
}

internal fun WatchLaterItem.toEntry(): VideoEntry {
    val bv = bvid
    return VideoEntry(
        id = bv ?: "av$aid",
        title = title.cleanText(),
        cover = pic.toHttpsOrNull(),
        subtitle = owner?.name.cleanText(),
        meta = if (duration > 0) duration.durationText() else "",
        target = "https://www.bilibili.com/video/${bv ?: "av$aid"}"
    )
}

internal fun FavMedia.toEntry(): VideoEntry {
    val bv = bvid
    return VideoEntry(
        id = bv ?: "av$id",
        title = title.cleanText(),
        cover = cover.toHttpsOrNull(),
        subtitle = upper?.name.cleanText(),
        meta = "${(cntInfo?.play ?: 0).countText()} 播放",
        target = "https://www.bilibili.com/video/${bv ?: "av$id"}"
    )
}

internal fun RelationUser.toEntry(): UserEntry = UserEntry(
    mid = mid,
    name = uname.cleanText(),
    avatar = face.toHttpsOrNull(),
    sign = sign.cleanText(),
    meta = officialVerify?.desc.cleanText()
)

internal fun BangumiFollow.toEntry(): VideoEntry = VideoEntry(
    id = "ss$seasonId",
    title = title.cleanText(),
    cover = cover.toHttpsOrNull(),
    subtitle = seasonTypeName.cleanText(),
    meta = newEp?.indexShow.cleanText().takeIf { it.isNotBlank() }
        ?: progress.cleanText().takeIf { it.isNotBlank() }
        ?: badge.cleanText(),
    target = url?.takeIf { it.isNotBlank() }
        ?: "https://www.bilibili.com/bangumi/play/ss$seasonId",
    seasonId = seasonId
)

internal fun FavFolder.toEntry(): FolderEntry = FolderEntry(
    id = id,
    title = title.cleanText(),
    count = mediaCount
)

internal fun SubmissionItem.toEntry(): VideoEntry {
    val bv = bvid
    return VideoEntry(
        id = bv ?: "av$aid",
        title = title.cleanText(),
        cover = pic.toHttpsOrNull(),
        subtitle = created.dateText(),
        meta = "${play.countText()} 播放",
        target = "https://www.bilibili.com/video/${bv ?: "av$aid"}"
    )
}

internal fun SeasonItem.toEntry(): CollectionEntry? {
    val info = meta ?: return null
    val name = info.name.cleanText().ifBlank { info.title.cleanText() }
    val count = if (info.total > 0) info.total else archives.orEmpty().size
    return CollectionEntry(
        id = info.seasonId,
        title = name,
        cover = info.cover.toHttpsOrNull(),
        subtitle = info.description.cleanText(),
        count = count
    )
}

internal fun SeasonArchive.toEntry(): VideoEntry {
    val bv = bvid
    return VideoEntry(
        id = bv ?: "av$aid",
        title = title.cleanText(),
        cover = pic.toHttpsOrNull(),
        subtitle = "",
        meta = buildString {
            val view = stat?.view ?: 0
            if (view > 0) {
                append(view.countText())
                append(" 播放")
            }
            if (duration > 0) {
                if (isNotEmpty()) append(" · ")
                append(duration.durationText())
            }
        },
        target = "https://www.bilibili.com/video/${bv ?: "av$aid"}"
    )
}

internal fun BlackUser.toEntry(): UserEntry = UserEntry(
    mid = mid,
    name = uname.cleanText(),
    avatar = face.toHttpsOrNull(),
    sign = sign.cleanText(),
    meta = ""
)

internal fun ReplyItem.toEntry(): CommentEntry = CommentEntry(
    rpid = rpid,
    mid = member?.mid ?: mid,
    name = member?.uname.cleanText(),
    avatar = member?.avatar.toHttpsOrNull(),
    message = content?.message.orEmpty(),
    like = like,
    timeText = ctime.timeAgo(),
    replyCount = rcount,
    liked = action == 1,
    root = root
)
