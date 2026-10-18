package com.takina.bilisearch.ui.screens.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.local.AppLog
import com.takina.bilisearch.data.model.DanmakuItem
import com.takina.bilisearch.data.model.DashTrack
import com.takina.bilisearch.data.model.PlayData
import com.takina.bilisearch.data.model.UgcEpisode
import com.takina.bilisearch.data.model.UgcSeason
import com.takina.bilisearch.data.model.UgcSection
import com.takina.bilisearch.data.model.VideoDetail
import com.takina.bilisearch.data.model.VideoOwner
import com.takina.bilisearch.data.net.BiliException
import com.takina.bilisearch.data.repo.CommentEntry
import com.takina.bilisearch.data.repo.FavoriteFolderEntry
import com.takina.bilisearch.data.repo.UpInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 清晰度选项 */
data class QualityOption(val qn: Int, val label: String)

/** 一个可尝试的媒体地址（视频 + 可选音频） */
data class MediaCandidate(val videoUrl: String, val audioUrl: String?)

/** 播放源：按可用性排序的候选列表 */
data class PlaySource(
    val candidates: List<MediaCandidate>,
    val qn: Int,
    val label: String
) {
    fun candidateAt(index: Int): MediaCandidate? = candidates.getOrNull(index)
}

/**
 * 实测（多轮 curl）：
 * - `*.mcdn.bilivideo.cn` 直连 200
 * - `*.edge.mountaintoys.cn`（P2P CDN）直连 403
 * - 海外 upos 镜像（mirrorcoso / mirrorhwo）也常 403
 * B 站会在多个 CDN 之间轮换，重新请求 playurl 有机会拿到可用地址。
 */
internal fun hostScoreOf(url: String?): Int {
    if (url.isNullOrBlank()) return 0
    val host = runCatching { java.net.URI(url).host.orEmpty() }
        .getOrDefault("").lowercase()
    return when {
        host.isEmpty() -> 0
        host.contains("mountaintoys") -> 0
        // B 站官方代理：由服务端去取 CDN，本地网络屏蔽 CDN 时依然能播
        host.contains("proxy-tf-all-ws") -> 3
        host.contains("mcdn") -> 3
        host.contains("mirrorcoso") || host.contains("mirrorhwo") -> 1
        host.contains("bilivideo") -> 2
        else -> 1
    }
}

private fun hostOf(url: String?): String =
    url?.let { runCatching { java.net.URI(it).host }.getOrNull() }.orEmpty()

private fun candidateScore(candidate: MediaCandidate): Int = minOf(
    hostScoreOf(candidate.videoUrl),
    hostScoreOf(candidate.audioUrl ?: candidate.videoUrl)
)

private fun DashTrack.backupUrls(): List<String> =
    (backupUrls ?: backupUrlsAlt).orEmpty().filter { it.isNotBlank() }

/** 清晰度 id 对应的中文名（接口没给 `support_formats` 时用） */
private fun qnLabelOf(qn: Int): String = when (qn) {
    127 -> "8K"
    126 -> "杜比视界"
    125 -> "真彩 HDR"
    120 -> "超清 4K"
    116 -> "1080P60"
    112 -> "1080P+"
    80 -> "1080P"
    74 -> "720P60"
    64 -> "720P"
    32 -> "480P"
    16 -> "360P"
    6 -> "240P"
    else -> "${qn}P"
}

/**
 * 番剧会额外下发杜比视界 / HDR / 8K / 超分轨，它们的文件名形如：
 * - `{cid}_sr2-1-100035.m4s` 超分
 * - `{cid}_hr1-1-30125.m4s` 真彩 HDR（id=125）
 * 实测这些轨道几乎只挂在 upos 上，在部分网络下恒定 403，而且设备也未必解得了，直接排掉。
 */
private fun DashTrack.isSpecialTrack(): Boolean {
    // 125 真彩 HDR / 126 杜比视界 / 127 8K
    if (id >= 100_000 || id in 125..127) return true
    val name = url()?.lowercase() ?: return false
    return name.contains("_sr") || name.contains("_hr") || name.contains("dolby")
}

/**
 * B 站官方代理：客户端只需要连上 `proxy-tf-all-ws.bilivideo.com`，
 * 真正的 CDN 由服务端去取。实测对 upos / mcdn 都能返回 206，
 * 所以当本地网络屏蔽了 CDN 时，这是唯一能播的通路。
 */
private fun proxyUrl(url: String): String =
    "https://proxy-tf-all-ws.bilivideo.com/?url=" +
        runCatching { java.net.URLEncoder.encode(url, "UTF-8") }.getOrDefault(url)

/**
 * 一个视频轨道候选。
 * `rank` 越大越优先：先比 CDN 可用性（直连 mcdn / 代理 > upos > 镜像），
 * 再比清晰度，再比直连还是走代理，最后才是编码格式。
 */
private data class VideoCandidate(
    val url: String,
    val qn: Int,
    val codecid: Int,
    val host: Int,
    /** 是否走 B 站官方代理 */
    val proxied: Boolean = false
) {
    val rank: Int
        get() = host * 100_000 + qn.coerceIn(0, 999) * 100 +
            (if (proxied) 1 else 2) * 10 +
            when (codecid) {
                12 -> 3   // HEVC：mcdn 上最常见，兼容性也好
                7 -> 2    // AVC：最保险，但实测基本只挂在 upos 上
                13 -> 1   // AV1：体积最小，个别老设备解不了
                else -> 0
            }
}

data class PlayerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: VideoDetail? = null,
    /** 番剧没有点赞/投币/收藏等互动，置 true 时隐藏互动栏 */
    val isBangumi: Boolean = false,
    /** 番剧当前正在播放的分集 epId */
    val epId: Long = 0,
    val up: UpInfo? = null,
    val qualities: List<QualityOption> = emptyList(),
    val currentQn: Int = 0,
    val source: PlaySource? = null,
    val danmaku: List<DanmakuItem> = emptyList(),
    val comments: List<CommentEntry> = emptyList(),
    val commentSort: Int = SORT_HOT,
    val commentsLoading: Boolean = false,
    val commentsHasMore: Boolean = false,
    val commentsTotal: Int = 0,
    /** 评论正在发送 */
    val commentPosting: Boolean = false,
    val liked: Boolean = false,
    val coined: Boolean = false,
    val favorited: Boolean = false,
    val following: Boolean = false,
    val watchLater: Boolean = false,
    val favoriteFolders: List<FavoriteFolderEntry> = emptyList(),
    val isLogin: Boolean = false,
    val message: String? = null
) {
    val aid: Long get() = detail?.aid ?: 0
}

const val SORT_NEWEST = 0
const val SORT_HOT = 1

class PlayerViewModel(
    private val bvid: String,
    /** > 0 表示当前在看某一集番剧 */
    private val epId: Long = 0,
    /** 只给了番剧 seasonId 时，先取第一集 */
    private val seasonId: Long = 0
) : ViewModel() {

    var state by mutableStateOf(PlayerUiState())
        private set

    private val repo = AppGraph.repo
    private var commentPage = 0
    private val isBangumiRef = epId > 0 || seasonId > 0
    /** 解析后的当前分集 epId（只给 seasonId 时由服务端决定） */
    private var currentEpId = epId
    /** 当前分集的 aid，番剧播放地址靠它走普通视频接口 */
    private var bangumiAid = 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (isBangumiRef) loadBangumi() else loadUgc()
        }
    }

    /** 番剧播放走普通视频接口（按分集 aid），见 `BiliRepository.playUrlByAid` */
    private suspend fun fetchPlay(
        bv: String,
        cid: Long,
        qn: Int
    ): PlayData = if (isBangumiRef) {
        if (bangumiAid > 0) {
            repo.playUrlByAid(bangumiAid, cid, qn)
        } else {
            // 极端情况下分集没有 aid，退回 PGC 接口
            repo.bangumiPlayUrl(currentEpId, cid, qn)
        }
    } else {
        repo.playUrl(bv, cid, qn)
    }

    private suspend fun loadBangumi() {
        state = state.copy(loading = true, error = null, isLogin = repo.isLoggedIn())
        try {
            val season = repo.bangumiSeason(epId = epId, seasonId = seasonId)
            val episodes = season.allEpisodes()
            val current = episodes.firstOrNull { it.episodeId() == epId }
                ?: episodes.firstOrNull()
                ?: throw BiliException(-404, "找不到这一集")
            currentEpId = current.episodeId()
            bangumiAid = current.aid

            val title = listOfNotNull(
                season.title?.takeIf { it.isNotBlank() },
                current.longTitle?.takeIf { it.isNotBlank() }
                    ?: current.showTitle?.takeIf { it.isNotBlank() },
            ).joinToString(" ")

            val detail = VideoDetail(
                bvid = current.bvid,
                aid = current.aid,
                cid = current.cid,
                title = title,
                pic = current.cover ?: season.cover,
                desc = season.evaluate,
                owner = VideoOwner(
                    mid = season.upInfo?.mid ?: 0,
                    name = season.upInfo?.uname,
                    face = season.upInfo?.avatar
                ),
                // 把分集列表做成「合集」，选集就能用现成的横向卡片
                ugcSeason = UgcSeason(
                    id = season.seasonId,
                    title = season.title,
                    cover = season.cover,
                    epCount = episodes.size,
                    sections = listOf(
                        UgcSection(
                            id = season.seasonId,
                            title = season.title,
                            episodes = episodes.map {
                                UgcEpisode(
                                    aid = it.aid,
                                    bvid = it.bvid,
                                    cid = it.cid,
                                    title = it.longTitle ?: it.showTitle ?: it.title,
                                    pic = it.cover,
                                    duration = it.duration / 1000,
                                    epId = it.episodeId()
                                )
                            }
                        )
                    )
                )
            )

            state = state.copy(
                detail = detail,
                isBangumi = true,
                epId = currentEpId,
                isLogin = repo.isLoggedIn()
            )

            viewModelScope.launch {
                val ownerMid = detail.owner?.mid ?: 0
                if (ownerMid > 0) {
                    val up = runCatching { repo.upInfo(ownerMid) }.getOrNull()
                    if (up != null) state = state.copy(up = up)
                }
            }
            viewModelScope.launch {
                val items = runCatching { repo.danmaku(current.cid, current.bvid) }
                    .getOrDefault(emptyList())
                state = state.copy(danmaku = items)
            }

            loadPlay(detail, detail.cid, DEFAULT_QN)
            loadComments(reset = true)
            state = state.copy(loading = false)
        } catch (e: Exception) {
            state = state.copy(loading = false, error = e.message ?: "加载失败")
        }
    }

    private suspend fun loadUgc() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null, isLogin = repo.isLoggedIn())
            try {
                val detail = repo.videoDetail(bvid)
                state = state.copy(detail = detail, isLogin = repo.isLoggedIn())
                // UP 主概要（粉丝 / 投稿数）
                viewModelScope.launch {
                    val up = runCatching { repo.upInfo(detail.owner?.mid ?: 0) }.getOrNull()
                    if (up != null) state = state.copy(up = up)
                }
                // 弹幕
                viewModelScope.launch {
                    val items = runCatching {
                        repo.danmaku(detail.cid, detail.bvid)
                    }.getOrDefault(emptyList())
                    state = state.copy(danmaku = items)
                }
                // 已点赞 / 已投币 / 已收藏 / 已关注 / 收藏夹状态
                viewModelScope.launch {
                    val relation = repo.archiveRelation(detail.aid, detail.owner?.mid)
                    val folders = runCatching {
                        repo.favoriteFoldersWithState(detail.aid)
                    }.getOrDefault(emptyList())
                    state = state.copy(
                        liked = relation?.like ?: state.liked,
                        coined = (relation?.coin ?: 0) > 0 || state.coined,
                        favorited = relation?.favorite == true || folders.any { it.selected },
                        following = relation?.attention ?: state.following,
                        favoriteFolders = folders
                    )
                }
                viewModelScope.launch {
                    val inLater = repo.isInWatchLater(detail.aid)
                    state = state.copy(watchLater = inLater)
                }
                // 播放地址
                loadPlay(detail, detail.cid, DEFAULT_QN)
                // 评论
                loadComments(reset = true)
                state = state.copy(loading = false)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun selectQuality(qn: Int) {
        val detail = state.detail ?: return
        if (qn == state.currentQn) return
        viewModelScope.launch {
            runCatching { loadPlay(detail, detail.cid, qn) }
                .onFailure { state = state.copy(message = it.message ?: "切换清晰度失败") }
        }
    }

    /** 上报观看进度，让视频出现在「观看记录」里（番剧走自己的接口，这里不报） */
    fun reportProgress(seconds: Long) {
        val detail = state.detail ?: return
        if (isBangumiRef) return
        if (seconds <= 0) return
        viewModelScope.launch {
            runCatching { repo.reportHistory(detail.aid, detail.cid, seconds) }
                .onFailure { AppLog.d("history", "上报失败: ${it.message}") }
        }
    }

    private var dashTried = false

    /** H5 通道全部失败时，改用 DASH（部分网络下反而只有 DASH 能通） */
    fun useProgressiveFallback() {
        val detail = state.detail ?: return
        if (dashTried) return
        dashTried = true
        AppLog.d("play", "H5 通道失败，切换到 DASH 兜底")
        viewModelScope.launch {
            runCatching {
                loadPlayDash(detail.bvid ?: bvid, detail.cid, state.currentQn, true)
            }.onFailure {
                AppLog.e("play", "DASH 兜底也失败", it)
                state = state.copy(message = it.message ?: "播放失败")
            }
        }
    }

    /**
     * 拉取播放地址。
     *
     * **优先走 H5 通道（`platform=html5`）**：实测在部分网络下，`platform=pc` 默认签出来的
     * upos 地址恒定 403（换 UA / Referer / Range / Cookie / 任何 upos 主机都无效），
     * 而**同一个主机**上 `platform=html5` 签出来的地址直接 206。代价是只能拿到 durl
     * 整段 mp4（音视频已合并）且最高 720P，但至少能播。
     *
     * html5 拿不到时再退回 DASH（某些网络下 DASH 反而是通的）。
     */
    private suspend fun loadPlay(
        detail: VideoDetail,
        cid: Long,
        requestedQn: Int,
        allowDowngrade: Boolean = true
    ) {
        val bv = detail.bvid ?: bvid

        // ---------- 1. 先试 DASH ----------
        // DASH 能拿到完整清晰度（最高 4K），而且只要抽到 `*.mcdn.bilivideo.cn` 就能播。
        val dashScore = loadPlayDash(bv, cid, requestedQn, allowDowngrade)
        // 番剧的 html5 通道恒为 -404，抽不到 mcdn 也只能用 DASH
        if (isBangumiRef) return
        // 已经拿到 mcdn，且不是只能给 720P 的兜底档，就直接用
        if (dashScore >= 3) return

        // ---------- 2. H5 通道兜底 ----------
        // html5 只认 64(720P) / 16(360P)；请求更高的 qn 服务端会自己降到能给的档
        val h5Qn = if (requestedQn >= 64) 80 else requestedQn
        val h5 = runCatching { repo.playUrlHtml5(bv, cid, h5Qn) }
            .onFailure { AppLog.e("play", "html5 playurl 失败 qn=$h5Qn", it) }
            .getOrNull()

        val h5Urls = h5?.durl.orEmpty().flatMap { segment ->
            listOfNotNull(segment.url) + segment.backupUrls.orEmpty()
        }.filter { it.isNotBlank() }

        if (h5Urls.isNotEmpty()) {
            val formats = h5?.supportFormats.orEmpty().map {
                QualityOption(it.quality, it.newDescription ?: it.format ?: "${it.quality}")
            }
            val label = formats.firstOrNull { it.qn == h5?.quality }?.label ?: "${h5?.quality}P"
            val merged = h5Urls.distinct().map { MediaCandidate(it, null) }
            AppLog.d(
                "play",
                "html5 兑底命中 qn=${h5?.quality} 地址=${merged.size} 个 host=${hostOf(merged.first().videoUrl)}"
            )
            // html5 通道最高就是 720P，这属于通道上限而非降级，不必每次都提示
            state = state.copy(
                qualities = formats.ifEmpty { state.qualities },
                currentQn = h5?.quality?.takeIf { it > 0 } ?: requestedQn,
                source = PlaySource(
                    candidates = merged,
                    qn = h5?.quality?.takeIf { it > 0 } ?: requestedQn,
                    label = label
                )
            )
        }
    }

    /** DASH 通道（`fnval=4048`）：按 CDN 可用性给候选地址打分排序，返回最佳分值 */
    private suspend fun loadPlayDash(
        bv: String,
        cid: Long,
        requestedQn: Int,
        allowDowngrade: Boolean
    ): Int {
        var formats: List<QualityOption> = emptyList()
        var firstServedQn = 0
        val videoCandidates = mutableListOf<VideoCandidate>()
        val audioCandidates = mutableListOf<Pair<String, Int>>()
        /** 这一集实际下发的清晰度（HDR / 杜比 / 超分已排除） */
        val offeredQn = mutableSetOf<Int>()
        var lastError: Exception? = null

        fun addVideo(url: String, qn: Int, codecid: Int) {
            if (url.isBlank()) return
            videoCandidates += VideoCandidate(url, qn, codecid, hostScoreOf(url))
            // 直连不通时靠官方代理，服务端去取 CDN，不受本地网络屏蔽影响
            videoCandidates += VideoCandidate(proxyUrl(url), qn, codecid, 3, proxied = true)
        }

        fun addAudio(url: String) {
            if (url.isBlank()) return
            audioCandidates += url to hostScoreOf(url)
            audioCandidates += proxyUrl(url) to 3
        }

        fun collect(play: PlayData) {
            if (formats.isEmpty()) {
                formats = play.supportFormats.orEmpty().map {
                    QualityOption(it.quality, it.newDescription ?: it.format ?: "${it.quality}")
                }
                firstServedQn = play.quality
            }

            val videos = play.dash?.video.orEmpty()
            val normal = videos.filterNot { it.isSpecialTrack() }
            val pool = normal.ifEmpty { videos }

            // 关键：把这一份 playurl 里**所有**普通轨道的地址都收进候选。
            // 之前只取「同清晰度的第一条轨道」，结果永远命中超分轨（只挂在 upos 上），
            // 而其它轨道其实会被分配到 mcdn —— 这才是拿不到可播视频地址的真正原因。
            pool.forEach { track ->
                val qn = if (track.id in 1..999) track.id else play.quality
                offeredQn += qn
                (listOfNotNull(track.url()) + track.backupUrls()).forEach { url ->
                    addVideo(url, qn, track.codecid)
                }
            }
            // 某些接口直接给 durl（整段 mp4，音视频已合并）
            play.durl.orEmpty().forEach { segment ->
                offeredQn += play.quality
                (listOfNotNull(segment.url) + segment.backupUrls.orEmpty()).forEach { url ->
                    addVideo(url, play.quality, 7)
                }
            }

            // 音频同理：30250 杜比 / 30251 Hi-Res 不一定能解，优先普通 AAC
            val audios = play.dash?.audio.orEmpty()
            val aacAudios = audios.filter { it.id == 30216 || it.id == 30232 || it.id == 30280 }
            (aacAudios.ifEmpty { audios }).forEach { track ->
                (listOfNotNull(track.url()) + track.backupUrls()).forEach { addAudio(it) }
            }

            val top = videoCandidates.maxByOrNull { it.rank }
            AppLog.d(
                "play",
                "采样 qn=${play.quality} 轨道=${normal.size}/${videos.size} 候选=${videoCandidates.size} " +
                    "bestVideo=${hostOf(top?.url)}(${top?.host}${if (top?.proxied == true) "-代理" else ""}) " +
                    "qn=${top?.qn} cid=${top?.codecid} " +
                    "bestAudio=${hostOf(audioCandidates.maxByOrNull { it.second }?.first)}"
            )
        }

        // 第一次按请求清晰度取，顺带拿到清晰度列表
        val first = try {
            fetchPlay(bv, cid, requestedQn)
        } catch (e: Exception) {
            lastError = e
            AppLog.e("play", "playurl qn=$requestedQn 失败", e)
            null
        }
        first?.let { collect(it) }

        val hasVideo = { videoCandidates.any { it.host >= 3 } }
        val hasAudio = { audioCandidates.any { it.second >= 3 } }

        if (allowDowngrade && !(hasVideo() && hasAudio())) {
            // CDN 分配是随机的，同一个节点也会时好时坏，所以多抽几次；
            // 高清晰度轨更容易分到 upos，低清晰度反而常给 mcdn，所以顺带抽两档低的
            val batches = buildList {
                add(List(3) { requestedQn })
                add(listOf(requestedQn, requestedQn, 80))
                if (requestedQn > 80) add(listOf(80, 64, 64))
            }
            for (batch in batches) {
                val results = coroutineScope {
                    batch.map { qn ->
                        async {
                            runCatching { fetchPlay(bv, cid, qn) }
                                .onFailure { AppLog.e("play", "playurl qn=$qn 失败", it) }
                                .getOrNull()
                        }
                    }.awaitAll()
                }
                results.filterNotNull().forEach { collect(it) }
                if (hasVideo() && (hasAudio() || audioCandidates.isEmpty())) break
            }
        }

        // 视频与音频可以来自不同的几次请求：交叉组合，取可用性最高的配对。
        // 先确定这部/这一集真实可选的清晰度（每部番都不一样），只在这个范围内排候选。
        val offeredDesc = offeredQn.sortedDescending()
        // 菜单只列出本集实际有的清晰度，不再把所有番都显示成同一套
        formats = formats.filter { it.qn in offeredQn }.sortedByDescending { it.qn }
            .ifEmpty { offeredDesc.map { QualityOption(it, qnLabelOf(it)) } }

        val targetQn = when {
            offeredDesc.isEmpty() -> firstServedQn
            // 用户明确选了清晰度就按他的来，不再自作主张跳到其他档
            requestedQn > 0 && offeredDesc.contains(requestedQn) -> requestedQn
            requestedQn > 0 -> offeredDesc.filter { it <= requestedQn }.maxOrNull()
                ?: offeredDesc.first()
            else -> offeredDesc.first()
        }

        val pool = videoCandidates.filter { it.qn == targetQn }.ifEmpty { videoCandidates }
        val ranked = pool
            .distinctBy { it.url }
            .sortedByDescending { it.rank }
        // 代理候选排在最前，但也要留几个直连的：万一代理不可用还有退路
        val bestVideos = (ranked.filter { it.proxied }.take(4) + ranked.filterNot { it.proxied }.take(3))
            .sortedByDescending { it.rank }
            .take(6)
        val bestAudios = audioCandidates
            .distinctBy { it.first }
            .sortedByDescending { it.second }
            .take(4)

        val combos = mutableListOf<MediaCandidate>()
        if (bestAudios.isEmpty()) {
            bestVideos.forEach { combos += MediaCandidate(it.url, null) }
        } else {
            // 轮转配对：主机尽量错开，同时避免全组合导致候选过多、重试太慢
            bestVideos.forEachIndexed { index, video ->
                val audio = bestAudios[index % bestAudios.size]
                combos += MediaCandidate(video.url, audio.first)
            }
        }

        val merged = combos.sortedByDescending { candidateScore(it) }

        // 视频和音频都能拿到 mcdn 才算真好（3 分），只有一边好就要继续降级
        val bestVideoHost = videoCandidates.maxOfOrNull { it.host } ?: 0
        val bestAudioHost = audioCandidates.maxOfOrNull { it.second } ?: bestVideoHost
        val bestResponseScore = minOf(bestVideoHost, bestAudioHost)

        if (merged.isEmpty()) {
            val message = lastError?.message ?: "没有拿到可播放的地址"
            AppLog.e("play", "获取播放地址失败: $message")
            state = state.copy(message = message)
            return bestResponseScore
        }

        // 实际能播的清晰度以排在最前面的候选为准
        val topQn = bestVideos.firstOrNull()?.qn?.takeIf { it > 0 } ?: targetQn
        val topLabel = formats.firstOrNull { it.qn == topQn }?.label
            ?: bestVideos.firstOrNull()?.qn?.let { "${it}P" }
            ?: ""

        AppLog.d(
            "play",
            "最终候选 ${merged.size} 个，最佳 score=${candidateScore(merged.first())} qn=$topQn " +
                "video=${hostOf(merged.first().videoUrl)} " +
                "代理=${merged.first().videoUrl.contains("proxy-tf-all-ws")} " +
                "audio=${hostOf(merged.first().audioUrl)}"
        )

        val downgraded = firstServedQn > 0 && topQn > 0 && topQn < firstServedQn
        state = state.copy(
            qualities = formats.ifEmpty { state.qualities },
            currentQn = if (topQn > 0) topQn else firstServedQn,
            source = PlaySource(
                candidates = merged,
                qn = if (topQn > 0) topQn else firstServedQn,
                label = topLabel
            ),
            message = if (downgraded) {
                "当前网络下自动使用 $topLabel"
            } else {
                state.message
            }
        )
        return bestResponseScore
    }

    private var sourceReloads = 0

    /** 播放失败后重新请求播放地址（换一个 CDN），返回是否已发起重试 */
    fun reloadPlaySource(): Boolean {
        val detail = state.detail ?: return false
        // 番剧的 CDN 更容易抽到 403，多给它几轮
        val maxReloads = if (isBangumiRef) 4 else 2
        if (sourceReloads >= maxReloads) return false
        sourceReloads++
        AppLog.d("play", "重新获取播放地址 第 $sourceReloads 次")
        viewModelScope.launch {
            runCatching {
                loadPlay(detail, detail.cid, state.currentQn, allowDowngrade = true)
            }.onFailure {
                AppLog.e("play", "重新获取播放地址失败", it)
                state = state.copy(message = it.message ?: "重新获取播放地址失败")
            }
        }
        return true
    }

    // ---------------- 评论 ----------------

    fun switchSort(sort: Int) {
        if (sort == state.commentSort) return
        state = state.copy(commentSort = sort, comments = emptyList())
        loadComments(reset = true)
    }

    private fun replaceComment(index: Int, block: (CommentEntry) -> CommentEntry) {
        if (index !in state.comments.indices) return
        val list = state.comments.toMutableList()
        list[index] = block(list[index])
        state = state.copy(comments = list)
    }

    /** 评论点赞 / 取消点赞：先本地生效，接口失败再回滚 */
    fun toggleCommentLike(comment: CommentEntry) {
        val aid = state.aid
        if (aid == 0L) return
        val index = state.comments.indexOfFirst { it.rpid == comment.rpid }
        if (index < 0) return
        val like = !comment.liked
        replaceComment(index) {
            it.copy(liked = like, like = (it.like + if (like) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            runCatching { repo.likeComment(aid, comment.rpid, like) }
                .onFailure {
                    replaceComment(index) { it.copy(liked = comment.liked, like = comment.like) }
                    state = state.copy(message = it.message ?: "点赞失败")
                }
        }
    }

    /** 发表评论；replyTo 不为空时是回复某条评论 */
    fun postComment(text: String, replyTo: CommentEntry? = null) {
        val content = text.trim()
        val aid = state.aid
        if (aid == 0L) return
        if (content.isEmpty()) {
            state = state.copy(message = "说点什么吧")
            return
        }
        if (state.commentPosting) return
        state = state.copy(commentPosting = true)
        viewModelScope.launch {
            runCatching {
                repo.addComment(
                    aid = aid,
                    message = content,
                    // 回复楼中楼时 root 仍是根评论，parent 才是被回复的那条
                    root = replyTo?.let { if (it.root > 0) it.root else it.rpid } ?: 0L,
                    parent = replyTo?.rpid ?: 0L
                )
            }.onSuccess {
                state = state.copy(commentPosting = false, message = "评论已发布")
                // 切到「最新」重新拉一次，保证刚发的评论能看到
                state = state.copy(commentSort = SORT_NEWEST, comments = emptyList())
                commentPage = 0
                loadComments(reset = true)
            }.onFailure {
                state = state.copy(commentPosting = false, message = it.message ?: "评论失败")
            }
        }
    }

    fun loadComments(reset: Boolean = false) {
        val aid = state.aid
        if (aid == 0L || state.commentsLoading) return
        if (!reset && !state.commentsHasMore) return
        val page = if (reset) 1 else commentPage + 1
        state = state.copy(commentsLoading = true)
        viewModelScope.launch {
            runCatching { repo.comments(aid, page, state.commentSort) }
                .onSuccess { result ->
                    commentPage = page
                    state = state.copy(
                        comments = if (reset) result.items else state.comments + result.items,
                        commentsHasMore = result.hasMore,
                        commentsTotal = result.total.toInt(),
                        commentsLoading = false
                    )
                }
                .onFailure {
                    state = state.copy(
                        commentsLoading = false,
                        message = it.message ?: "评论加载失败"
                    )
                }
        }
    }

    // ---------------- 互动 ----------------

    fun toggleLike() {
        val aid = state.aid
        if (aid == 0L) return
        val target = !state.liked
        viewModelScope.launch {
            runCatching { repo.likeVideo(aid, target, state.detail?.bvid) }
                .onSuccess { state = state.copy(liked = target) }
                .onFailure { error ->
                    val code = (error as? BiliException)?.code
                    if (code == 65006 || error.message?.contains("重复点赞") == true) {
                        state = state.copy(liked = true, message = "已经点过赞了")
                    } else {
                        state = state.copy(message = error.message ?: "点赞失败")
                    }
                }
        }
    }

    /** 投币，count = 1 或 2 */
    fun addCoin(count: Int) {
        val aid = state.aid
        if (aid == 0L) return
        if (state.coined) {
            state = state.copy(message = "已经投过币了")
            return
        }
        viewModelScope.launch {
            runCatching { repo.coinVideo(aid, count, state.detail?.bvid) }
                .onSuccess {
                    state = state.copy(coined = true, message = "已投 $count 枚硬币")
                }
                .onFailure { state = state.copy(message = it.message ?: "投币失败") }
        }
    }

    /** 拉取收藏夹列表（带是否已收藏该稿件） */
    fun loadFavoriteFolders() {
        val aid = state.aid
        if (aid == 0L) return
        viewModelScope.launch {
            runCatching { repo.favoriteFoldersWithState(aid) }
                .onSuccess { state = state.copy(favoriteFolders = it) }
                .onFailure {
                    state = state.copy(message = it.message ?: "收藏夹加载失败")
                }
        }
    }

    /** 应用收藏夹勾选结果 */
    fun applyFavorite(folderIds: Set<Long>) {
        val aid = state.aid
        if (aid == 0L) return
        val original = state.favoriteFolders.filter { it.selected }.map { it.id }.toSet()
        val add = folderIds - original
        val del = original - folderIds
        if (add.isEmpty() && del.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.favoriteVideo(aid, add, del, state.detail?.bvid) }
                .onSuccess {
                    state = state.copy(
                        favoriteFolders = state.favoriteFolders.map {
                            it.copy(selected = it.id in folderIds)
                        },
                        favorited = folderIds.isNotEmpty(),
                        message = if (folderIds.isEmpty()) "已取消收藏" else "已收藏"
                    )
                }
                .onFailure { state = state.copy(message = it.message ?: "收藏失败") }
        }
    }

    fun addWatchLater() {
        val aid = state.aid
        if (aid == 0L) return
        val adding = !state.watchLater
        viewModelScope.launch {
            runCatching {
                if (adding) repo.addWatchLater(aid) else repo.removeWatchLater(aid)
            }.onSuccess {
                state = state.copy(
                    watchLater = adding,
                    message = if (adding) "已添加到稍后再看" else "已从稍后再看移除"
                )
            }.onFailure {
                state = state.copy(message = it.message ?: "操作失败")
            }
        }
    }

    fun toggleFollow() {
        val mid = state.detail?.owner?.mid ?: return
        viewModelScope.launch {
            runCatching { repo.followUser(mid, !state.following) }
                .onSuccess {
                    state = state.copy(
                        following = !state.following,
                        message = if (state.following) "已关注" else "已取消关注"
                    )
                }
                .onFailure { state = state.copy(message = it.message ?: "关注失败") }
        }
    }

    fun consumeMessage() {
        if (state.message != null) state = state.copy(message = null)
    }

    /** 播放顺序用：合集里的下一个视频 */
    fun nextBvid(): String? {
        if (isBangumiRef) return null
        val detail = state.detail ?: return null
        val episodes = detail.ugcSeason?.sections.orEmpty()
            .flatMap { it.episodes.orEmpty() }
        if (episodes.isEmpty()) return null
        val index = episodes.indexOfFirst { it.bvid == (detail.bvid ?: bvid) }
        if (index < 0 || index + 1 >= episodes.size) return null
        return episodes[index + 1].bvid
    }

    /** 播放顺序用：番剧的下一集 epId */
    fun nextEpisodeId(): Long? {
        if (!isBangumiRef) return null
        val episodes = state.detail?.ugcSeason?.sections.orEmpty()
            .flatMap { it.episodes.orEmpty() }
        if (episodes.isEmpty()) return null
        val index = episodes.indexOfFirst { it.epId == currentEpId }
        if (index < 0 || index + 1 >= episodes.size) return null
        return episodes[index + 1].epId
    }
    class Factory(
        private val bvid: String,
        private val epId: Long = 0L,
        private val seasonId: Long = 0L
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(bvid, epId, seasonId) as T
        }
    }

    private companion object {
        /** 请求最高档，再由 support_formats 决定实际可用清晰度 */
        const val DEFAULT_QN = 120
    }
}
