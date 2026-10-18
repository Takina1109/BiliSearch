package com.takina.bilisearch.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/** B 站通用响应外壳 */
data class BiliResponse<T>(
    val code: Int = -1,
    val message: String? = null,
    val ttl: Int = 1,
    val data: T? = null
)

/** PGC（番剧/影视）接口的外壳：数据在 result 里，不是 data */
data class PgcResponse<T>(
    val code: Int = -1,
    val message: String? = null,
    val ttl: Int = 0,
    val result: T? = null
)

// ---------------- 搜索 ----------------

data class VideoItem(
    val aid: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val author: String? = null,
    val mid: Long = 0,
    val pic: String? = null,
    val description: String? = null,
    val pubdate: Long = 0,
    val duration: String? = null,
    val play: Long = 0,
    @SerializedName("video_review") val danmaku: Long = 0,
    val favorites: Long = 0,
    val tag: String? = null
)

data class OfficialVerify(
    val type: Int = 0,
    val desc: String? = null
)

data class UserItem(
    val mid: Long = 0,
    val uname: String? = null,
    val usign: String? = null,
    val fans: Long = 0,
    val videos: Long = 0,
    val upic: String? = null,
    val level: Int = 0,
    @SerializedName("is_upuser") val isUpUser: Int = 0,
    @SerializedName("official_verify") val officialVerify: OfficialVerify? = null
)

data class BangumiItem(
    @SerializedName("media_id") val mediaId: Long = 0,
    @SerializedName("season_id") val seasonId: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    @SerializedName("season_type_name") val seasonTypeName: String? = null,
    val styles: String? = null,
    val desc: String? = null,
    val areas: String? = null,
    val staff: String? = null,
    @SerializedName("index_show") val indexShow: String? = null,
    val badge: String? = null,
    val goto: String? = null
)

// ---------------- 账号 ----------------

data class WbiImg(
    @SerializedName("img_url") val imgUrl: String = "",
    @SerializedName("sub_url") val subUrl: String = ""
)

data class LevelInfo(
    @SerializedName("current_level") val currentLevel: Int = 0,
    @SerializedName("current_min") val currentMin: Long = 0,
    @SerializedName("current_exp") val currentExp: Long = 0,
    /** Lv6 时为字符串 "--"，其余为数字，这里保留原始 JSON */
    @SerializedName("next_exp") val nextExp: JsonElement? = null
) {
    /** 满级（Lv6）时返回 null */
    fun nextExpValue(): Long? {
        val element = nextExp ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asLong }.getOrNull()
    }
}

data class VipLabel(
    val path: String? = null,
    val text: String? = null,
    @SerializedName("label_theme") val labelTheme: String? = null,
    @SerializedName("text_color") val textColor: String? = null,
    @SerializedName("bg_style") val bgStyle: Int = 0,
    @SerializedName("bg_color") val bgColor: String? = null,
    @SerializedName("border_color") val borderColor: String? = null,
    @SerializedName("use_img_label") val useImgLabel: Boolean = false,
    @SerializedName("img_label_uri_hans") val imgLabelUriHans: String? = null,
    @SerializedName("img_label_uri_hant") val imgLabelUriHant: String? = null,
    @SerializedName("img_label_uri_hans_static") val imgLabelUriHansStatic: String? = null,
    @SerializedName("img_label_uri_hant_static") val imgLabelUriHantStatic: String? = null
)

data class VipInfo(
    val type: Int = 0,
    val status: Int = 0,
    @SerializedName("due_date") val dueDate: Long = 0,
    @SerializedName("vip_pay_type") val vipPayType: Int = 0,
    @SerializedName("theme_type") val themeType: Int = 0,
    val label: VipLabel? = null,
    @SerializedName("avatar_subscript") val avatarSubscript: Int = 0,
    @SerializedName("nickname_color") val nicknameColor: String? = null,
    val role: Int = 0,
    @SerializedName("avatar_subscript_url") val avatarSubscriptUrl: String? = null
)

data class NavInfo(
    @SerializedName("isLogin") val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String? = null,
    val face: String? = null,
    /** 拥有硬币数 */
    val money: Double = 0.0,
    @SerializedName("level_info") val levelInfo: LevelInfo? = null,
    @SerializedName("vipStatus") val vipStatus: Int = 0,
    @SerializedName("vipType") val vipType: Int = 0,
    @SerializedName("vipDueDate") val vipDueDate: Long = 0,
    @SerializedName("vip_nickname_color") val vipNicknameColor: String? = null,
    @SerializedName("vip_label") val vipLabel: VipLabel? = null,
    val vip: VipInfo? = null,
    @SerializedName("wbi_img") val wbiImg: WbiImg? = null
)

/** /x/space/wbi/acc/info 其他用户空间详细信息 */
data class SpaceInfo(
    val mid: Long = 0,
    val name: String? = null,
    val sex: String? = null,
    val face: String? = null,
    val sign: String? = null,
    val level: Int = 0,
    val coins: Double = 0.0,
    val vip: VipInfo? = null
)

/** /x/space/myinfo 登录用户空间详细信息 */
data class MyInfo(
    val mid: Long = 0,
    val name: String? = null,
    /** 男 / 女 / 保密 */
    val sex: String? = null,
    val face: String? = null,
    val sign: String? = null,
    val level: Int = 0,
    val coins: Double = 0.0,
    val following: Int = 0,
    val follower: Int = 0,
    val vip: VipInfo? = null,
    @SerializedName("level_exp") val levelExp: LevelInfo? = null
)

/** /x/web-interface/nav/stat */
data class NavStat(
    val following: Int = 0,
    val follower: Int = 0,
    @SerializedName("dynamic_count") val dynamicCount: Int = 0
)

/** /x/relation/stat */
data class RelationStat(
    val mid: Long = 0,
    val following: Int = 0,
    val follower: Int = 0
)

data class QrGenerate(
    val url: String = "",
    @SerializedName("qrcode_key") val qrcodeKey: String = ""
)

data class QrPoll(
    val url: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val timestamp: Long = 0,
    val code: Int = -1,
    val message: String? = null
)

// ---------------- 历史 / 稍后再看 ----------------

data class HistoryCursor(
    val max: Long = 0,
    @SerializedName("view_at") val viewAt: Long = 0,
    val business: String? = null
)

data class HistoryRef(
    val oid: Long = 0,
    val bvid: String? = null,
    val cid: Long = 0,
    val business: String? = null
)

data class HistoryItem(
    val title: String? = null,
    val cover: String? = null,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_mid") val authorMid: Long = 0,
    val duration: Long = 0,
    val videos: Int = 0,
    @SerializedName("view_at") val viewAt: Long = 0,
    val history: HistoryRef? = null
)

data class HistoryPage(
    val cursor: HistoryCursor? = null,
    val list: List<HistoryItem>? = null
)

data class Owner(
    val mid: Long = 0,
    val name: String? = null,
    val face: String? = null
)

data class WatchLaterItem(
    val aid: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val pic: String? = null,
    val owner: Owner? = null,
    val duration: Long = 0,
    val progress: Long = 0
)

data class WatchLaterData(
    val count: Int = 0,
    val list: List<WatchLaterItem>? = null
)

// ---------------- 收藏 ----------------

data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String? = null,
    @SerializedName("media_count") val mediaCount: Int = 0,
    /** 该稿件是否已在此收藏夹中（查询时传 rid/type 才有） */
    @SerializedName("fav_state") val favState: Int = 0
)

data class FavFolderData(
    val count: Int = 0,
    val list: List<FavFolder>? = null
)

data class FavCount(
    val play: Long = 0,
    val danmaku: Long = 0,
    val collect: Long = 0
)

data class FavMedia(
    val id: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val duration: Long = 0,
    val upper: Owner? = null,
    @SerializedName("cnt_info") val cntInfo: FavCount? = null
)

data class FavListData(
    val info: FavFolder? = null,
    val medias: List<FavMedia>? = null,
    @SerializedName("has_more") val hasMore: Boolean = false
)

// ---------------- 关注 / 粉丝 ----------------

data class RelationUser(
    val mid: Long = 0,
    val uname: String? = null,
    val face: String? = null,
    val sign: String? = null,
    @SerializedName("official_verify") val officialVerify: OfficialVerify? = null
)

data class RelationData(
    val total: Int = 0,
    val list: List<RelationUser>? = null
)

// ---------------- 番剧订阅 ----------------

data class NewEp(
    @SerializedName("index_show") val indexShow: String? = null
)

data class BangumiFollow(
    @SerializedName("season_id") val seasonId: Long = 0,
    @SerializedName("media_id") val mediaId: Long = 0,
    val title: String? = null,
    @SerializedName("season_title") val seasonTitle: String? = null,
    val cover: String? = null,
    @SerializedName("season_type_name") val seasonTypeName: String? = null,
    @SerializedName("new_ep") val newEp: NewEp? = null,
    val badge: String? = null,
    val url: String? = null,
    /** 接口返回的是文字，例如「看到第 3 话」，不能声明成 Int */
    val progress: String? = null,
    @SerializedName("is_finish") val isFinish: Int = 0
)

data class BangumiFollowData(
    val total: Int = 0,
    val list: List<BangumiFollow>? = null
)

// ---------------- 番剧（PGC）剧集 / 播放 ----------------

/** /pgc/view/web/season 的分集（正片或花絮） */
data class PgcEpisode(
    val aid: Long = 0,
    val bvid: String? = null,
    val cid: Long = 0,
    /** 分集 id，与 ep_id 相同 */
    val id: Long = 0,
    @SerializedName("ep_id") val epId: Long = 0,
    val cover: String? = null,
    /** 毫秒 */
    val duration: Long = 0,
    val title: String? = null,
    @SerializedName("long_title") val longTitle: String? = null,
    @SerializedName("show_title") val showTitle: String? = null,
    @SerializedName("section_type") val sectionType: Int = 0
) {
    fun episodeId(): Long = if (epId > 0) epId else id
}

/** 花絮 / PV 等附加内容 */
data class PgcSection(
    val id: Long = 0,
    val title: String? = null,
    val type: Int = 0,
    val episodes: List<PgcEpisode>? = null
)

data class PgcUpInfo(
    val mid: Long = 0,
    val uname: String? = null,
    val avatar: String? = null,
    val follower: Int = 0
)

data class PgcSeason(
    @SerializedName("season_id") val seasonId: Long = 0,
    @SerializedName("media_id") val mediaId: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    val evaluate: String? = null,
    val episodes: List<PgcEpisode>? = null,
    @SerializedName("section") val sections: List<PgcSection>? = null,
    @SerializedName("up_info") val upInfo: PgcUpInfo? = null
) {
    /** 正片 + 花絮全部分集 */
    fun allEpisodes(): List<PgcEpisode> =
        episodes.orEmpty() + sections.orEmpty().flatMap { it.episodes.orEmpty() }
}

// ---------------- 我的投稿 ----------------

data class SubmissionItem(
    val aid: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val pic: String? = null,
    val description: String? = null,
    val created: Long = 0,
    val length: String? = null,
    val play: Long = 0,
    @SerializedName("video_review") val danmaku: Long = 0
)

data class SubmissionList(
    val vlist: List<SubmissionItem>? = null
)

data class SubmissionPage(
    val count: Int = 0,
    val pn: Int = 0,
    val ps: Int = 0
)

data class SubmissionData(
    val list: SubmissionList? = null,
    val page: SubmissionPage? = null
)

// ---------------- 合集 ----------------

/** 合集（season）元信息 */
data class SeasonMeta(
    @SerializedName("season_id") val seasonId: Long = 0,
    val mid: Long = 0,
    val name: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val description: String? = null,
    val total: Int = 0,
    val ptime: Long = 0
)

/** 合集内的视频（合集列表接口与归档接口结构一致） */
data class SeasonArchive(
    val aid: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val pic: String? = null,
    val duration: Long = 0,
    val pubdate: Long = 0,
    @SerializedName("stat") val stat: SeasonArchiveStat? = null
)

data class SeasonArchiveStat(
    val view: Long = 0,
    val danmaku: Long = 0
)

data class SeasonItem(
    val meta: SeasonMeta? = null,
    val archives: List<SeasonArchive>? = null
)

data class PageInfo(
    @SerializedName("page_num") val pageNum: Int = 0,
    @SerializedName("page_size") val pageSize: Int = 0,
    val total: Int = 0
)

data class SeasonItems(
    val page: PageInfo? = null,
    @SerializedName("seasons_list") val seasonsList: List<SeasonItem>? = null,
    @SerializedName("series_list") val seriesList: List<SeasonItem>? = null
)

data class SeasonListData(
    @SerializedName("items_lists") val itemsLists: SeasonItems? = null
)

data class SeasonArchivesData(
    val archives: List<SeasonArchive>? = null,
    val page: PageInfo? = null
)

// ---------------- 黑名单 ----------------

/** /x/relation/blacks 的列表项比关注列表多一个 attribute 字段 */
data class BlackUser(
    val mid: Long = 0,
    val uname: String? = null,
    val face: String? = null,
    val sign: String? = null,
    val attribute: Int = 0
)

data class BlacklistData(
    val total: Int = 0,
    val list: List<BlackUser>? = null
)
