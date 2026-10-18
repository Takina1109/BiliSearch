package com.takina.bilisearch.data.model

import com.google.gson.annotations.SerializedName

// ---------------- 视频详情 /x/web-interface/view ----------------

data class VideoPage(
    val cid: Long = 0,
    val page: Int = 0,
    val part: String? = null,
    val duration: Long = 0
)

data class VideoOwner(
    val mid: Long = 0,
    val name: String? = null,
    val face: String? = null
)

data class VideoStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0
)

data class VideoDimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0
)

data class UgcEpisode(
    val aid: Long = 0,
    val bvid: String? = null,
    val cid: Long = 0,
    val title: String? = null,
    val pic: String? = null,
    val duration: Long = 0,
    /** 番剧分集的 ep_id（UGC 合集为 0） */
    @SerializedName("ep_id") val epId: Long = 0
)

data class UgcSection(
    val id: Long = 0,
    val title: String? = null,
    val episodes: List<UgcEpisode>? = null
)

/** 视频所属合集（ugc_season） */
data class UgcSeason(
    val id: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    @SerializedName("ep_count") val epCount: Int = 0,
    val sections: List<UgcSection>? = null
)

data class VideoDetail(
    val bvid: String? = null,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String? = null,
    val pic: String? = null,
    val desc: String? = null,
    val pubdate: Long = 0,
    val duration: Long = 0,
    val owner: VideoOwner? = null,
    val stat: VideoStat? = null,
    val pages: List<VideoPage>? = null,
    @SerializedName("ugc_season") val ugcSeason: UgcSeason? = null,
    val dimension: VideoDimension? = null
)

// ---------------- 播放地址 /x/player/wbi/playurl ----------------

data class DashTrack(
    val id: Int = 0,
    @SerializedName("baseUrl") val baseUrl: String? = null,
    @SerializedName("base_url") val baseUrlAlt: String? = null,
    @SerializedName("backupUrl") val backupUrls: List<String>? = null,
    @SerializedName("backup_url") val backupUrlsAlt: List<String>? = null,
    val bandwidth: Long = 0,
    val codecid: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val codecs: String? = null,
    @SerializedName("frameRate") val frameRate: String? = null
) {
    fun url(): String? = (baseUrl ?: baseUrlAlt)?.takeIf { it.isNotBlank() }
}

data class DashData(
    val duration: Long = 0,
    val video: List<DashTrack>? = null,
    val audio: List<DashTrack>? = null
)

data class SupportFormat(
    val quality: Int = 0,
    val format: String? = null,
    @SerializedName("new_description") val newDescription: String? = null,
    @SerializedName("display_desc") val displayDesc: String? = null
)

data class DurlSegment(
    val order: Int = 0,
    val url: String? = null,
    @SerializedName("backup_url") val backupUrls: List<String>? = null
)

data class PlayData(
    val quality: Int = 0,
    val dash: DashData? = null,
    @SerializedName("support_formats") val supportFormats: List<SupportFormat>? = null,
    val durl: List<DurlSegment>? = null
)

/** 弹幕 /x/v1/dm/list.so（XML） */
data class DanmakuItem(
    val timeMs: Long,
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val text: String
)

// ---------------- 评论 /x/v2/reply ----------------

data class ReplyMember(
    val mid: Long = 0,
    val uname: String? = null,
    val avatar: String? = null,
    @SerializedName("level_info") val levelInfo: LevelInfo? = null
)

data class ReplyContent(
    val message: String? = null
)

data class ReplyItem(
    val rpid: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val member: ReplyMember? = null,
    val content: ReplyContent? = null,
    val like: Long = 0,
    val ctime: Long = 0,
    val rcount: Int = 0,
    /** 根评论 rpid，0 表示本身就是根评论 */
    val root: Long = 0,
    /** 父评论 rpid（回复楼中楼时用） */
    val parent: Long = 0,
    /** 1 = 我已点赞 */
    val action: Int = 0
)

data class ReplyPage(
    val count: Int = 0,
    val page: Int = 0,
    val size: Int = 0
)

data class ReplyData(
    val replies: List<ReplyItem>? = null,
    val page: ReplyPage? = null
)

// ---------------- UP 主统计 /x/space/upstat + 投稿数 ----------------

data class UpStatArchive(
    val view: Long = 0
)

data class UpStat(
    val archive: UpStatArchive? = null,
    val likes: Long = 0
)

data class UpStatData(
    val mid: Long = 0,
    val archive: UpStatArchive? = null,
    val likes: Long = 0
)

/** /x/space/navnum 投稿数 */
data class NavNumData(
    val video: Int = 0,
    val bangumi: Int = 0,
    val article: Int = 0,
    val album: Int = 0
)

/** /x/web-interface/archive/relation 当前用户对视频的互动状态 */
data class ArchiveRelation(
    val attention: Boolean = false,
    val favorite: Boolean = false,
    val like: Boolean = false,
    val coin: Int = 0,
    val dislike: Int = 0
)
