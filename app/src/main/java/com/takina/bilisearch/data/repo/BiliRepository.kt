package com.takina.bilisearch.data.repo

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.takina.bilisearch.data.local.AppLog
import com.takina.bilisearch.data.local.Prefs
import com.takina.bilisearch.data.model.ArchiveRelation
import com.takina.bilisearch.data.model.BangumiFollowData
import com.takina.bilisearch.data.model.BangumiItem
import com.takina.bilisearch.data.model.BiliResponse
import com.takina.bilisearch.data.model.BlacklistData
import com.takina.bilisearch.data.model.DanmakuItem
import com.takina.bilisearch.data.model.FavFolderData
import com.takina.bilisearch.data.model.FavListData
import com.takina.bilisearch.data.model.HistoryPage
import com.takina.bilisearch.data.model.MyInfo
import com.takina.bilisearch.data.model.NavInfo
import com.takina.bilisearch.data.model.NavNumData
import com.takina.bilisearch.data.model.NavStat
import com.takina.bilisearch.data.model.PlayData
import com.takina.bilisearch.data.model.PgcResponse
import com.takina.bilisearch.data.model.PgcSeason
import com.takina.bilisearch.data.model.QrGenerate
import com.takina.bilisearch.data.model.QrPoll
import com.takina.bilisearch.data.model.RelationData
import com.takina.bilisearch.data.model.RelationStat
import com.takina.bilisearch.data.model.ReplyData
import com.takina.bilisearch.data.model.SeasonArchivesData
import com.takina.bilisearch.data.model.SeasonListData
import com.takina.bilisearch.data.model.SpaceInfo
import com.takina.bilisearch.data.model.SubmissionData
import com.takina.bilisearch.data.model.UserItem
import com.takina.bilisearch.data.model.VideoDetail
import com.takina.bilisearch.data.model.VideoItem
import com.takina.bilisearch.data.model.WatchLaterData
import com.takina.bilisearch.data.net.BiliApi
import com.takina.bilisearch.data.net.BiliException
import com.takina.bilisearch.data.net.BiliHttp
import com.takina.bilisearch.data.net.WbiSigner
import com.takina.bilisearch.data.net.optIntOr
import com.takina.bilisearch.data.net.optLongOr
import com.takina.bilisearch.data.net.optStringOr

class BiliRepository(private val prefs: Prefs) {

    private val gson = Gson()

    // ---------------- 登录态 ----------------

    fun loadSavedCookies(): Map<String, String> = runCatching {
        val raw = prefs.cookiesJson() ?: return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson<Map<String, String>>(raw, type) ?: emptyMap()
    }.getOrDefault(emptyMap())

    fun persistSession() {
        prefs.saveCookies(gson.toJson(BiliHttp.cookieJar.snapshot()))
    }

    fun isLoggedIn(): Boolean = !BiliHttp.cookieJar.get("SESSDATA").isNullOrBlank()

    fun logout() {
        BiliHttp.cookieJar.clear()
        BiliApi.resetGuest()
        prefs.clearCookies()
    }

    private fun myMid(): Long = BiliHttp.cookieJar.get("DedeUserID")?.toLongOrNull() ?: 0L

    private fun requireLogin() {
        if (!isLoggedIn()) throw BiliException(-101, "请先登录")
    }

    // ---------------- 扫码登录 ----------------

    suspend fun qrGenerate(): QrGenerate {
        val json = BiliHttp.rawGet("${BiliApi.PASSPORT}/x/passport-login/web/qrcode/generate")
        ensureOk(json)
        return gson.fromJson(dataOf(json), QrGenerate::class.java)
    }

    suspend fun qrPoll(qrKey: String): QrPoll {
        val json = BiliHttp.rawGet(
            "${BiliApi.PASSPORT}/x/passport-login/web/qrcode/poll?qrcode_key=$qrKey"
        )
        ensureOk(json)
        val poll = gson.fromJson(dataOf(json), QrPoll::class.java)
        if (poll.code == 0) captureCookiesFromUrl(poll.url)
        return poll
    }

    /** 部分情况下 Set-Cookie 不生效，从成功跳转 URL 中兜底提取登录 Cookie */
    private fun captureCookiesFromUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        listOf(
            "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5",
            "sid", "buvid3", "buvid4"
        ).forEach { name ->
            uri.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let {
                BiliHttp.cookieJar.put(name, it)
            }
        }
    }

    // ---------------- 账号信息 ----------------

    suspend fun nav(): NavInfo {
        val url = WbiSigner.sign("${BiliApi.API}/x/web-interface/nav", emptyMap())
        val json = BiliHttp.rawGet(url)
        val data = json.get("data")?.takeIf { it.isJsonObject } ?: return NavInfo()
        return gson.fromJson(data, NavInfo::class.java) ?: NavInfo()
    }

    /**
     * 当前账号概要：头像、等级经验、硬币、大会员、关注/粉丝数。
     * 未登录时返回 isLogin = false 的空对象。
     */
    suspend fun profile(): ProfileSummary {
        if (!isLoggedIn()) return ProfileSummary()

        // myinfo 一次返回昵称/签名/经验/硬币/关注粉丝，优先使用
        val info = runCatching { myInfo() }.getOrNull()
        if (info != null) {
            val vip = info.vip
            val exp = info.levelExp
            return ProfileSummary(
                isLogin = true,
                mid = info.mid,
                name = info.name.cleanText(),
                face = info.face.toHttpsOrNull(),
                level = exp?.currentLevel ?: info.level,
                expCurrent = exp?.currentExp ?: 0,
                expMin = exp?.currentMin ?: 0,
                expNext = exp?.nextExpValue(),
                coins = info.coins,
                isVip = (vip?.status ?: 0) == 1,
                vipLabelText = vip?.label?.text.cleanText(),
                vipLabelImage = (vip?.label?.imgLabelUriHansStatic
                    ?: vip?.label?.imgLabelUriHans).toHttpsOrNull(),
                nicknameColor = vip?.nicknameColor,
                following = info.following,
                followers = info.follower,
                sign = info.sign.cleanText()
            )
        }

        // 兜底：nav + 统计
        val nav = nav()
        if (!nav.isLogin) return ProfileSummary()

        val navStatResult = runCatching { navStat() }.getOrNull()
        val following: Int
        val followers: Int
        if (navStatResult != null) {
            following = navStatResult.following
            followers = navStatResult.follower
        } else {
            val relation = runCatching { relationStat(nav.mid) }.getOrNull()
            following = relation?.following ?: 0
            followers = relation?.follower ?: 0
        }

        val vip = nav.vip
        val label = vip?.label ?: nav.vipLabel

        return ProfileSummary(
            isLogin = true,
            mid = nav.mid,
            name = nav.uname.cleanText(),
            face = nav.face.toHttpsOrNull(),
            level = nav.levelInfo?.currentLevel ?: 0,
            expCurrent = nav.levelInfo?.currentExp ?: 0,
            expMin = nav.levelInfo?.currentMin ?: 0,
            expNext = nav.levelInfo?.nextExpValue(),
            coins = nav.money,
            isVip = (vip?.status ?: nav.vipStatus) == 1,
            vipLabelText = label?.text.cleanText(),
            vipLabelImage = (label?.imgLabelUriHansStatic ?: label?.imgLabelUriHans)
                .toHttpsOrNull(),
            nicknameColor = vip?.nicknameColor ?: nav.vipNicknameColor,
            following = following,
            followers = followers
        )
    }

    /**
     * 其他用户主页信息：空间详情 + 关注/粉丝数。
     */
    suspend fun userProfile(mid: Long): ProfileSummary {
        val space = userSpaceInfo(mid)
        val stat = runCatching { relationStat(mid) }.getOrNull()
        val vip = space.vip
        return ProfileSummary(
            isLogin = true,
            mid = if (space.mid > 0) space.mid else mid,
            name = space.name.cleanText(),
            face = space.face.toHttpsOrNull(),
            level = space.level,
            isVip = (vip?.status ?: 0) == 1,
            vipLabelText = vip?.label?.text.cleanText(),
            vipLabelImage = (vip?.label?.imgLabelUriHansStatic
                ?: vip?.label?.imgLabelUriHans).toHttpsOrNull(),
            nicknameColor = vip?.nicknameColor,
            following = stat?.following ?: 0,
            followers = stat?.follower ?: 0,
            sign = space.sign.cleanText()
        )
    }

    private suspend fun userSpaceInfo(mid: Long): SpaceInfo {
        BiliApi.ensureBuvid()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/space/wbi/acc/info",
            mapOf("mid" to mid.toString())
        )
        val type = object : TypeToken<BiliResponse<SpaceInfo>>() {}.type
        val response = BiliHttp.get<BiliResponse<SpaceInfo>>(url, type)
        return response.data ?: SpaceInfo(mid = mid)
    }

    private suspend fun navStat(): NavStat {
        val url = "${BiliApi.API}/x/web-interface/nav/stat"
        val type = object : TypeToken<BiliResponse<NavStat>>() {}.type
        val response = BiliHttp.get<BiliResponse<NavStat>>(url, type)
        return response.data ?: NavStat()
    }

    private suspend fun relationStat(mid: Long): RelationStat {
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/relation/stat",
            mapOf("vmid" to mid.toString())
        )
        val type = object : TypeToken<BiliResponse<RelationStat>>() {}.type
        val response = BiliHttp.get<BiliResponse<RelationStat>>(url, type)
        return response.data ?: RelationStat()
    }

    // ---------------- 资料编辑 ----------------

    /** 登录用户空间详细信息：昵称、性别、签名 */
    suspend fun myInfo(): MyInfo {
        requireLogin()
        val url = "${BiliApi.API}/x/space/myinfo"
        val type = object : TypeToken<BiliResponse<MyInfo>>() {}.type
        val response = BiliHttp.get<BiliResponse<MyInfo>>(url, type)
        return response.data ?: MyInfo()
    }

    /**
     * 修改个人资料。
     * sex: 0 保密 / 1 男 / 2 女
     */
    suspend fun updateProfile(name: String, sex: Int, sign: String) {
        requireLogin()
        val csrf = BiliHttp.cookieJar.get("bili_jct")
            ?: throw BiliException(-111, "登录态已失效，请重新登录")

        val url = "${BiliApi.API}/x/member/web/update"
        val json = BiliHttp.postForm(
            url = url,
            form = mapOf(
                "uname" to name,
                "usersex" to sex.toString(),
                "user_sign" to sign,
                "csrf" to csrf
            ),
            referer = "https://account.bilibili.com/account/info"
        )
        ensureOk(json)
    }

    // ---------------- 我的投稿 ----------------

    suspend fun submissions(page: Int, mid: Long? = null): PagedResult<VideoEntry> {
        requireLogin()
        BiliApi.ensureBuvid()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/space/wbi/arc/search",
            mapOf(
                "mid" to (mid ?: myMid()).toString(),
                "pn" to page.toString(),
                "ps" to PAGE_SIZE.toString(),
                "order" to "pubdate"
            )
        )
        val type = object : TypeToken<BiliResponse<SubmissionData>>() {}.type
        val response = BiliHttp.get<BiliResponse<SubmissionData>>(url, type)
        val items = response.data?.list?.vlist.orEmpty().map { it.toEntry() }
        val count = response.data?.page?.count ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = count.toLong(),
            hasMore = page * PAGE_SIZE < count
        )
    }

    // ---------------- 合集 ----------------

    /**
     * 用户的合集列表。
     * [mid] 为 null 时取当前登录账号。
     */
    suspend fun collections(mid: Long? = null, page: Int = 1): PagedResult<CollectionEntry> {
        val target = mid ?: run {
            requireLogin()
            myMid()
        }
        val url = "${BiliApi.API}/x/polymer/web-space/seasons_series_list" +
            "?mid=$target&page_num=$page&page_size=$PAGE_SIZE"
        val type = object : TypeToken<BiliResponse<SeasonListData>>() {}.type
        val response = BiliHttp.get<BiliResponse<SeasonListData>>(
            url, type, "https://space.bilibili.com/$target/video"
        )
        val items = response.data?.itemsLists?.seasonsList.orEmpty()
            .mapNotNull { it.toEntry() }
        val total = response.data?.itemsLists?.page?.total ?: items.size
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    /** 合集内的视频列表 */
    suspend fun collectionArchives(
        mid: Long?,
        seasonId: Long,
        page: Int
    ): PagedResult<VideoEntry> {
        val target = mid ?: run {
            requireLogin()
            myMid()
        }
        val url = "${BiliApi.API}/x/polymer/web-space/seasons_archives_list" +
            "?mid=$target&season_id=$seasonId&sort_reverse=false" +
            "&page_num=$page&page_size=$PAGE_SIZE"
        val type = object : TypeToken<BiliResponse<SeasonArchivesData>>() {}.type
        val response = BiliHttp.get<BiliResponse<SeasonArchivesData>>(
            url, type, "https://space.bilibili.com/$target/video"
        )
        val items = response.data?.archives.orEmpty().map { it.toEntry() }
        val total = response.data?.page?.total ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    // ---------------- 番剧（PGC） ----------------

    /**
     * 追番列表：`/x/space/bangumi/follow/list`
     * type=1 番剧、type=2 影视；follow_status=0 全部。
     */
    suspend fun bangumiFollow(page: Int, mid: Long?, type: Int = 1): PagedResult<VideoEntry> {
        val target = mid ?: run {
            requireLogin()
            myMid()
        }
        val url = "${BiliApi.API}/x/space/bangumi/follow/list" +
            "?type=$type&follow_status=0&pn=$page&ps=$PAGE_SIZE&vmid=$target"
        // 这个接口文档写的是 data，但保险起见 data / result 都认
        val json = BiliHttp.rawGet(url, "https://space.bilibili.com/$target/bangumi")
        val code = json.optIntOr("code", -1)
        if (code != 0) {
            throw BiliException(code, json.optStringOr("message") ?: "获取追番失败")
        }
        val body = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: json.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw BiliException(-1, "追番数据为空")
        val data = try {
            gson.fromJson(body, BangumiFollowData::class.java)
        } catch (e: Exception) {
            // 字段类型跟接口对不上时会整段解析失败，把原始 JSON 打出来方便排查
            AppLog.e("bangumi", "追番解析失败: ${body.toString().take(400)}", e)
            null
        }
        val items = data?.list.orEmpty().map { it.toEntry() }
        AppLog.d("bangumi", "追番 mid=$target pn=$page 共 ${items.size} 条")
        val total = data?.total ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    /**
     * 番剧详情（含全部分集）：`/pgc/view/web/season`
     * epId / seasonId 二选一，都传时以 epId 为准。
     */
    suspend fun bangumiSeason(epId: Long = 0, seasonId: Long = 0): PgcSeason {
        val query = if (epId > 0) "ep_id=$epId" else "season_id=$seasonId"
        val referer = if (epId > 0) {
            "https://www.bilibili.com/bangumi/play/ep$epId"
        } else {
            "https://www.bilibili.com/bangumi/play/ss$seasonId"
        }
        val url = "${BiliApi.API}/pgc/view/web/season?$query"
        val type = object : TypeToken<PgcResponse<PgcSeason>>() {}.type
        val response = BiliHttp.get<PgcResponse<PgcSeason>>(url, type, referer)
        return response.result ?: throw BiliException(-404, "番剧不存在或已下架")
    }

    /**
     * 番剧播放地址：`/pgc/player/web/playurl`（fnval = 4048 要 DASH）。
     * 实测 PGC 的 CDN 可以正常播放（不像 UGC 那样 403），所以直接用 DASH。
     */
    suspend fun bangumiPlayUrl(epId: Long, cid: Long, qn: Int): PlayData {
        BiliApi.ensureBuvid()
        val url = "${BiliApi.API}/pgc/player/web/playurl" +
            "?ep_id=$epId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
        val type = object : TypeToken<PgcResponse<PlayData>>() {}.type
        val response = BiliHttp.get<PgcResponse<PlayData>>(
            url, type, "https://www.bilibili.com/bangumi/play/ep$epId"
        )
        return response.result ?: throw BiliException(-1, "获取播放地址失败")
    }

    // ---------------- 黑名单 ----------------

    suspend fun blacklist(page: Int): PagedResult<UserEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/relation/blacks",
            mapOf("pn" to page.toString(), "ps" to PAGE_SIZE.toString())
        )
        val type = object : TypeToken<BiliResponse<BlacklistData>>() {}.type
        val response = BiliHttp.get<BiliResponse<BlacklistData>>(url, type)
        val items = response.data?.list.orEmpty().map { it.toEntry() }
        val total = response.data?.total ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    /** 解除拉黑（act = 6） */
    suspend fun unblock(mid: Long) {
        requireLogin()
        val csrf = BiliHttp.cookieJar.get("bili_jct")
            ?: throw BiliException(-111, "登录态已失效，请重新登录")
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/relation/modify",
            form = mapOf(
                "fid" to mid.toString(),
                "act" to "6",
                "re_src" to "11",
                "csrf" to csrf
            ),
            referer = "https://space.bilibili.com/$mid"
        )
        ensureOk(json)
    }

    // ---------------- 搜索 ----------------

    suspend fun searchVideos(keyword: String, page: Int): PagedResult<VideoEntry> {
        val data = searchData(keyword, page, "video")
        val items: List<VideoItem> = parseList(data.get("result"), VideoItem::class.java)
        return paged(items.map { it.toEntry() }, data, page)
    }

    suspend fun searchBangumi(keyword: String, page: Int): PagedResult<VideoEntry> {
        val data = searchData(keyword, page, "media_bangumi")
        val items: List<BangumiItem> = parseList(data.get("result"), BangumiItem::class.java)
        return paged(items.map { it.toEntry() }, data, page)
    }

    suspend fun searchUsers(keyword: String, page: Int): PagedResult<UserEntry> {
        val data = searchData(keyword, page, "bili_user")
        val items: List<UserItem> = parseList(data.get("result"), UserItem::class.java)
        return paged(items.map { it.toEntry() }, data, page)
    }

    private suspend fun searchData(keyword: String, page: Int, searchType: String): JsonObject {
        return try {
            requestSearch(keyword, page, searchType)
        } catch (e: BiliException) {
            // 匿名搜索被风控时，换一个访客身份重试一次
            if (e.code == -352 && !isLoggedIn()) {
                BiliApi.resetGuest()
                BiliApi.ensureBuvid()
                requestSearch(keyword, page, searchType)
            } else {
                throw e
            }
        }
    }

    private suspend fun requestSearch(
        keyword: String,
        page: Int,
        searchType: String
    ): JsonObject {
        BiliApi.ensureBuvid()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/web-interface/wbi/search/type",
            mapOf(
                "search_type" to searchType,
                "keyword" to keyword,
                "page" to page.toString(),
                "page_size" to PAGE_SIZE.toString(),
                "platform" to "pc"
            )
        )
        val referer = "${BiliApi.SEARCH_REFERER}all?keyword=${Uri.encode(keyword)}"
        val json = BiliHttp.rawGet(url, referer)
        ensureOk(json)
        return dataOf(json)
    }

    private fun <T> parseList(element: JsonElement?, clazz: Class<T>): List<T> {
        if (element == null || !element.isJsonArray) return emptyList()
        val type = TypeToken.getParameterized(List::class.java, clazz).type
        return gson.fromJson(element, type) ?: emptyList()
    }

    private fun <T> paged(items: List<T>, data: JsonObject, requestPage: Int): PagedResult<T> {
        val totalPages = data.optIntOr("numPages", 0)
        return PagedResult(
            items = items,
            page = data.optIntOr("page", requestPage),
            totalPages = totalPages,
            total = data.optLongOr("numResults", 0),
            hasMore = items.size >= PAGE_SIZE && (totalPages == 0 || requestPage < totalPages)
        )
    }

    // ---------------- 历史记录 ----------------

    suspend fun history(cursorMax: Long, cursorViewAt: Long): PagedResult<VideoEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/web-interface/history/cursor",
            mapOf(
                "max" to cursorMax.toString(),
                "view_at" to cursorViewAt.toString(),
                "business" to "",
                "ps" to PAGE_SIZE.toString(),
                "type" to "archive"
            )
        )
        val type = object : TypeToken<BiliResponse<HistoryPage>>() {}.type
        val response = BiliHttp.get<BiliResponse<HistoryPage>>(url, type)
        val items = response.data?.list.orEmpty().map { it.toEntry() }
        val cursor = response.data?.cursor
        return PagedResult(
            items = items,
            page = 1,
            hasMore = items.size >= PAGE_SIZE,
            cursorMax = cursor?.max ?: 0,
            cursorViewAt = cursor?.viewAt ?: 0
        )
    }

    // ---------------- 稍后再看 ----------------

    suspend fun watchLater(): List<VideoEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/v2/history/toview",
            mapOf("ps" to "40")
        )
        val type = object : TypeToken<BiliResponse<WatchLaterData>>() {}.type
        val response = BiliHttp.get<BiliResponse<WatchLaterData>>(url, type)
        return response.data?.list.orEmpty().map { it.toEntry() }
    }

    // ---------------- 收藏 ----------------

    suspend fun favoriteFolders(mid: Long? = null): List<FolderEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to (mid ?: myMid()).toString())
        )
        val type = object : TypeToken<BiliResponse<FavFolderData>>() {}.type
        val response = BiliHttp.get<BiliResponse<FavFolderData>>(url, type)
        return response.data?.list.orEmpty().map { it.toEntry() }
    }

    suspend fun favoriteItems(mediaId: Long, page: Int): PagedResult<VideoEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/v3/fav/resource/list",
            mapOf(
                "media_id" to mediaId.toString(),
                "pn" to page.toString(),
                "ps" to PAGE_SIZE.toString(),
                "order" to "mtime",
                "type" to "0",
                "platform" to "web"
            )
        )
        val type = object : TypeToken<BiliResponse<FavListData>>() {}.type
        val response = BiliHttp.get<BiliResponse<FavListData>>(url, type)
        return PagedResult(
            items = response.data?.medias.orEmpty().map { it.toEntry() },
            page = page,
            hasMore = response.data?.hasMore == true
        )
    }

    // ---------------- 关注 / 粉丝 ----------------

    suspend fun following(page: Int, mid: Long? = null): PagedResult<UserEntry> =
        relation(page, "followings", mid)

    suspend fun followers(page: Int, mid: Long? = null): PagedResult<UserEntry> =
        relation(page, "fans", mid)

    private suspend fun relation(page: Int, path: String, mid: Long?): PagedResult<UserEntry> {
        requireLogin()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/relation/$path",
            mapOf(
                "vmid" to (mid ?: myMid()).toString(),
                "pn" to page.toString(),
                "ps" to PAGE_SIZE.toString(),
                "order" to "desc",
                "order_type" to "attention"
            )
        )
        val type = object : TypeToken<BiliResponse<RelationData>>() {}.type
        val response = BiliHttp.get<BiliResponse<RelationData>>(url, type)
        val items = response.data?.list.orEmpty().map { it.toEntry() }
        val total = response.data?.total ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    // ---------------- 视频详情 / 播放 ----------------

    /** 视频详情（含标题、UP 主、统计、分P、所属合集） */
    suspend fun videoDetail(bvid: String): VideoDetail {
        val url = "${BiliApi.API}/x/web-interface/view?bvid=$bvid"
        val type = object : TypeToken<BiliResponse<VideoDetail>>() {}.type
        val response = BiliHttp.get<BiliResponse<VideoDetail>>(
            url, type, "https://www.bilibili.com/video/$bvid"
        )
        return response.data ?: throw BiliException(-404, "视频不存在或已被删除")
    }

    /** 播放地址，fnval = 4048 表示要 DASH */
    suspend fun playUrl(bvid: String, cid: Long, qn: Int): PlayData {
        BiliApi.ensureBuvid()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/player/wbi/playurl",
            mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1"
            )
        )
        val type = object : TypeToken<BiliResponse<PlayData>>() {}.type
        // 实测：用「视频页」当 Referer 会被风控返回 HTTP 412，用首页 Referer 才正常
        suspend fun fetch(referer: String) =
            BiliHttp.get<BiliResponse<PlayData>>(url, type, referer)

        val response = try {
            fetch("https://www.bilibili.com/")
        } catch (e: Exception) {
            fetch("https://www.bilibili.com/video/$bvid")
        }
        return response.data ?: throw BiliException(-1, "获取播放地址失败")
    }

    /**
     * H5 端播放地址（`platform=html5`）。
     *
     * **这是本项目唯一稳定可播的通道**：实测 `platform=pc`（默认）签出来的
     * upos 地址在部分网络下恒定 403（换 UA / Referer / Range / Cookie / 换任何
     * upos 主机都无效），而同一台主机上 `platform=html5` 签出来的地址直接 206。
     * html5 只返回 durl（整段 mp4，音视频已合并），最高 720P（qn=80 → quality=64）。
     */
    suspend fun playUrlHtml5(bvid: String, cid: Long, qn: Int): PlayData {
        BiliApi.ensureBuvid()
        // 注意：这个接口不需要 WBI 签名（实测裸调用即可），保持和验证时一致
        val url = "${BiliApi.API}/x/player/playurl" +
            "?bvid=$bvid&cid=$cid&qn=$qn&fnval=1&fnver=0&fourk=1" +
            "&platform=html5&high_quality=1"
        val type = object : TypeToken<BiliResponse<PlayData>>() {}.type
        val response = BiliHttp.get<BiliResponse<PlayData>>(
            url, type, "https://www.bilibili.com/"
        )
        return response.data ?: throw BiliException(-1, "获取播放地址失败")
    }

    /**
     * 用稿件 aid 取播放地址（番剧分集也走这里）。
     *
     * 番剧**不要**用 `/pgc/player/web/playurl`：它签出来的地址恒为 `platform=pc`
     * （传 `platform=html5` 会被忽略），而且只会分配 `upos-*` 节点，在部分网络下全是 403。
     * 番剧分集本身有 `aid`，走普通视频播放接口会正常分配到 `*.mcdn.bilivideo.cn`，可直接播放。
     * 第三方客户端 PiliPala 也是这么做的（`VideoHttp.videoUrl` → `/x/player/wbi/playurl`）。
     */
    suspend fun playUrlByAid(aid: Long, cid: Long, qn: Int): PlayData {
        BiliApi.ensureBuvid()
        val params = mutableMapOf(
            "avid" to aid.toString(),
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            // 这俩是 web 播放器会带的参数，带上更容易拿到高码率
            "voice_balance" to "1",
            "gaia_source" to "pre-load"
        )
        // 未登录时 B 站只给 480P，try_look=1 可以拿到 1080P（仅试看）
        if (!isLoggedIn()) params["try_look"] = "1"
        val url = WbiSigner.sign("${BiliApi.API}/x/player/wbi/playurl", params)
        val type = object : TypeToken<BiliResponse<PlayData>>() {}.type
        val response = BiliHttp.get<BiliResponse<PlayData>>(
            url, type, "https://www.bilibili.com/"
        )
        return response.data ?: throw BiliException(-1, "获取播放地址失败")
    }

    /** 兼容模式播放地址（fnval = 1，返回普通 mp4/flv，失败时兜底用） */
    suspend fun playUrlProgressive(bvid: String, cid: Long): List<String> {
        BiliApi.ensureBuvid()
        val url = WbiSigner.sign(
            "${BiliApi.API}/x/player/wbi/playurl",
            mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to "64",
                "fnval" to "1",
                "fnver" to "0",
                "fourk" to "0"
            )
        )
        val type = object : TypeToken<BiliResponse<PlayData>>() {}.type
        val response = try {
            BiliHttp.get<BiliResponse<PlayData>>(url, type, "https://www.bilibili.com/")
        } catch (e: Exception) {
            AppLog.e("play", "durl 首页 referer 失败", e)
            BiliHttp.get<BiliResponse<PlayData>>(
                url, type, "https://www.bilibili.com/video/$bvid"
            )
        }
        return response.data?.durl.orEmpty().flatMap { segment ->
            listOfNotNull(segment.url) + segment.backupUrls.orEmpty()
        }
    }

    /** 弹幕列表（XML，实际返回 raw deflate 压缩，见 [decodeDanmakuBytes]） */
    suspend fun danmaku(cid: Long, bvid: String? = null): List<DanmakuItem> {
        val referer = if (bvid.isNullOrBlank()) {
            "https://www.bilibili.com/"
        } else {
            "https://www.bilibili.com/video/$bvid/"
        }
        val bytes = BiliHttp.rawBytes(
            "${BiliApi.API}/x/v1/dm/list.so?oid=$cid",
            referer
        )
        val text = decodeDanmakuBytes(bytes)
        if (text.isBlank()) {
            AppLog.e(
                "danmaku",
                "弹幕解码失败 cid=$cid 原始字节=${bytes.size} head=" +
                    bytes.take(4).joinToString(" ") { it.toString(16) }
            )
            return emptyList()
        }
        val items = parseDanmakuXml(text)
        AppLog.d("danmaku", "cid=$cid 原始=${bytes.size}B 解析=${items.size} 条")
        return items
    }

    // ---------------- 评论区 ----------------

    /**
     * 视频评论。sort: 0 按时间（最新）/ 1 按点赞（最热）
     */
    suspend fun comments(aid: Long, page: Int, sort: Int): PagedResult<CommentEntry> {
        val url = "${BiliApi.API}/x/v2/reply?type=1&oid=$aid&pn=$page&ps=$PAGE_SIZE&sort=$sort"
        val type = object : TypeToken<BiliResponse<ReplyData>>() {}.type
        val response = BiliHttp.get<BiliResponse<ReplyData>>(
            url, type, "https://www.bilibili.com/video/av$aid"
        )
        val data = response.data
        val items = data?.replies.orEmpty()
            .filter { it.root == 0L || it.root == it.rpid }
            .map { it.toEntry() }
        val total = data?.page?.count ?: 0
        return PagedResult(
            items = items,
            page = page,
            total = total.toLong(),
            hasMore = page * PAGE_SIZE < total
        )
    }

    /** 评论点赞 / 取消点赞：`/x/v2/reply/action`（action: 1 点赞 / 0 取消） */
    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean) {
        requireLogin()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v2/reply/action",
            form = mapOf(
                "oid" to aid.toString(),
                "type" to "1",
                "rpid" to rpid.toString(),
                "action" to if (like) "1" else "0",
                "csrf" to csrfOrThrow()
            ),
            referer = "https://www.bilibili.com/video/av$aid"
        )
        ensureOk(json)
    }

    /**
     * 发表评论：`/x/v2/reply/add`。
     * 回复某条评论时，`root` 传根评论的 rpid，`parent` 传被回复评论的 rpid。
     * 返回新评论的 rpid。
     */
    suspend fun addComment(aid: Long, message: String, root: Long = 0, parent: Long = 0): Long {
        requireLogin()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v2/reply/add",
            form = mapOf(
                "oid" to aid.toString(),
                "type" to "1",
                "message" to message,
                "root" to root.toString(),
                "parent" to parent.toString(),
                "plat" to "1",
                "csrf" to csrfOrThrow()
            ),
            referer = "https://www.bilibili.com/video/av$aid"
        )
        ensureOk(json)
        return json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.optLongOr("rpid", 0L) ?: 0L
    }

    /** UP 主概要：粉丝数与投稿数 */
    suspend fun upInfo(mid: Long): UpInfo {
        val stat = runCatching { relationStat(mid) }.getOrNull()
        val videos = runCatching { submissionCount(mid) }.getOrNull() ?: 0
        return UpInfo(
            mid = mid,
            followers = stat?.follower ?: 0,
            videos = videos
        )
    }

    private suspend fun submissionCount(mid: Long): Int {
        // arc/search 匿名请求容易被 412，这里用 navnum（无需登录）
        val url = "${BiliApi.API}/x/space/navnum?mid=$mid"
        val type = object : TypeToken<BiliResponse<NavNumData>>() {}.type
        val response = BiliHttp.get<BiliResponse<NavNumData>>(
            url, type, "https://space.bilibili.com/$mid"
        )
        return response.data?.video ?: 0
    }

    // ---------------- 三连 / 稍后再看 / 关注 ----------------

    private fun csrfOrThrow(): String = BiliHttp.cookieJar.get("bili_jct")
        ?.takeIf { it.isNotBlank() }
        ?: throw BiliException(-111, "登录态已失效，请重新登录")

    suspend fun likeVideo(aid: Long, like: Boolean, bvid: String? = null) {
        requireLogin()
        BiliApi.ensureBuvid()
        val csrf = csrfOrThrow()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/web-interface/archive/like",
            form = mapOf(
                "aid" to aid.toString(),
                "like" to if (like) "1" else "2",
                "csrf" to csrf
            ),
            referer = videoReferer(aid, bvid)
        )
        ensureOk(json)
    }

    /** 投币，count 为 1 或 2。不同风控场景对参数要求不同，遇到 -401/-403/-111 时换写法重试 */
    suspend fun coinVideo(aid: Long, count: Int, bvid: String? = null) {
        requireLogin()
        BiliApi.ensureBuvid()
        val csrf = csrfOrThrow()
        val multiply = count.coerceIn(1, 2).toString()
        val referer = videoReferer(aid, bvid)
        val base = "${BiliApi.API}/x/web-interface/coin/add"

        val attempts = listOf(
            base to mapOf(
                "aid" to aid.toString(),
                "multiply" to multiply,
                "select_like" to "0",
                "csrf" to csrf
            ),
            // 不带 select_like
            base to mapOf(
                "aid" to aid.toString(),
                "multiply" to multiply,
                "csrf" to csrf
            ),
            // csrf 同时放在 query
            "$base?csrf=$csrf" to mapOf(
                "aid" to aid.toString(),
                "multiply" to multiply,
                "select_like" to "0",
                "csrf" to csrf
            )
        )

        var lastError: Exception? = null
        attempts.forEach { (target, form) ->
            try {
                ensureOk(BiliHttp.postForm(target, form, referer))
                return
            } catch (e: Exception) {
                lastError = e
                val code = (e as? BiliException)?.code
                // 业务错误（硬币不足 / 已投过等）没必要重试
                if (code != null && code != -401 && code != -403 && code != -111) throw e
            }
        }
        throw lastError ?: BiliException(-1, "投币失败")
    }

    /** 收藏 / 取消收藏（可一次多个收藏夹） */
    suspend fun favoriteVideo(
        aid: Long,
        addFolderIds: Collection<Long>,
        delFolderIds: Collection<Long>,
        bvid: String? = null
    ) {
        requireLogin()
        BiliApi.ensureBuvid()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v3/fav/resource/deal",
            form = mapOf(
                "rid" to aid.toString(),
                "type" to "2",
                "add_media_ids" to addFolderIds.joinToString(","),
                "del_media_ids" to delFolderIds.joinToString(","),
                "csrf" to csrfOrThrow()
            ),
            referer = videoReferer(aid, bvid)
        )
        ensureOk(json)
    }

    /** 收藏夹列表（带该稿件是否已收藏） */
    suspend fun favoriteFoldersWithState(aid: Long): List<FavoriteFolderEntry> {
        requireLogin()
        val url = "${BiliApi.API}/x/v3/fav/folder/created/list-all" +
            "?up_mid=${myMid()}&type=2&rid=$aid"
        val type = object : TypeToken<BiliResponse<FavFolderData>>() {}.type
        val response = try {
            BiliHttp.get<BiliResponse<FavFolderData>>(url, type, videoReferer(aid, null))
        } catch (e: Exception) {
            AppLog.e("fav", "folder/list-all 失败", e)
            throw e
        }
        return response.data?.list.orEmpty().sortedByDescending { it.id }.map {
            FavoriteFolderEntry(
                id = it.id,
                title = it.title.cleanText().ifBlank { "收藏夹" },
                count = it.mediaCount,
                selected = it.favState == 1
            )
        }
    }

    private fun videoReferer(aid: Long, bvid: String?): String =
        if (!bvid.isNullOrBlank()) {
            "https://www.bilibili.com/video/$bvid/"
        } else {
            "https://www.bilibili.com/video/av$aid/"
        }

    /** 默认收藏夹 id（收藏时用） */
    suspend fun defaultFavFolderId(): Long =
        favoriteFolders().firstOrNull()?.id ?: throw BiliException(-1, "还没有可用的收藏夹")

    suspend fun addWatchLater(aid: Long) {
        requireLogin()
        BiliApi.ensureBuvid()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v2/history/toview/add",
            form = mapOf("aid" to aid.toString(), "csrf" to csrfOrThrow()),
            referer = "https://www.bilibili.com/video/av$aid"
        )
        ensureOk(json)
    }

    /**
     * 上报观看进度（这样视频才会出现在「观看记录」里）。
     * 需要登录 + csrf；失败不抛异常，避免打断播放。
     */
    suspend fun reportHistory(aid: Long, cid: Long, progressSeconds: Long) {
        if (!isLoggedIn()) return
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v2/history/report",
            form = mapOf(
                "aid" to aid.toString(),
                "cid" to cid.toString(),
                "progress" to progressSeconds.coerceAtLeast(0).toString(),
                "type" to "3",
                "platform" to "web",
                "csrf" to csrfOrThrow()
            ),
            referer = "https://www.bilibili.com/video/av$aid"
        )
        val code = json.get("code")?.asInt ?: 0
        if (code != 0) {
            AppLog.d("history", "上报观看进度 code=$code ${json.get("message")}")
        }
    }

    suspend fun followUser(mid: Long, follow: Boolean) {
        requireLogin()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/relation/modify",
            form = mapOf(
                "fid" to mid.toString(),
                "act" to if (follow) "1" else "2",
                "re_src" to "14",
                "csrf" to csrfOrThrow()
            ),
            referer = "https://space.bilibili.com/$mid"
        )
        ensureOk(json)
    }

    /** 当前用户对该视频的点赞 / 投币 / 收藏 / 关注状态（未登录或失败时返回空） */
    suspend fun archiveRelation(aid: Long, upMid: Long? = null): ArchiveRelation? {
        if (!isLoggedIn()) return null
        val like = likeState(aid)
        val coins = coinState(aid)
        val favorite = favoriteState(aid)
        val following = upMid?.let { followingState(it) } ?: false
        return ArchiveRelation(
            attention = following,
            favorite = favorite,
            like = like,
            coin = coins
        )
    }

    /** 是否已点赞：/x/web-interface/archive/has/like */
    suspend fun likeState(aid: Long): Boolean {
        if (!isLoggedIn()) return false
        return runCatching {
            val json = BiliHttp.rawGet(
                "${BiliApi.API}/x/web-interface/archive/has/like?aid=$aid",
                videoReferer(aid, null)
            )
            if (json.optIntOr("code", -1) != 0) {
                AppLog.e("like", "has/like code=${json.optIntOr("code", -1)}")
                false
            } else {
                json.get("data")?.takeIf { it.isJsonPrimitive }?.asInt == 1
            }
        }.getOrElse {
            AppLog.e("like", "has/like 失败", it)
            false
        }
    }

    /** 已投币数：/x/web-interface/archive/coins */
    suspend fun coinState(aid: Long): Int {
        if (!isLoggedIn()) return 0
        return runCatching {
            val json = BiliHttp.rawGet(
                "${BiliApi.API}/x/web-interface/archive/coins?aid=$aid",
                videoReferer(aid, null)
            )
            if (json.optIntOr("code", -1) != 0) {
                AppLog.e("coin", "archive/coins code=${json.optIntOr("code", -1)}")
                0
            } else {
                json.get("data")?.takeIf { it.isJsonObject }
                    ?.asJsonObject?.optIntOr("multiply", 0) ?: 0
            }
        }.getOrElse {
            AppLog.e("coin", "archive/coins 失败", it)
            0
        }
    }

    /** 是否已收藏：/x/v2/fav/video/favoured */
    suspend fun favoriteState(aid: Long): Boolean {
        if (!isLoggedIn()) return false
        return runCatching {
            val json = BiliHttp.rawGet(
                "${BiliApi.API}/x/v2/fav/video/favoured?aid=$aid",
                videoReferer(aid, null)
            )
            if (json.optIntOr("code", -1) != 0) {
                AppLog.e("fav", "video/favoured code=${json.optIntOr("code", -1)}")
                false
            } else {
                json.get("data")?.takeIf { it.isJsonObject }
                    ?.asJsonObject?.get("favoured")?.takeIf { it.isJsonPrimitive }
                    ?.asBoolean == true
            }
        }.getOrElse {
            AppLog.e("fav", "video/favoured 失败", it)
            false
        }
    }

    /** 是否已关注某个 UP 主：/x/relation?fid= */
    suspend fun followingState(mid: Long): Boolean {
        if (!isLoggedIn() || mid <= 0) return false
        return runCatching {
            val json = BiliHttp.rawGet(
                "${BiliApi.API}/x/relation?fid=$mid",
                "https://space.bilibili.com/$mid"
            )
            if (json.optIntOr("code", -1) != 0) {
                AppLog.e("follow", "relation code=${json.optIntOr("code", -1)}")
                false
            } else {
                // attribute: 0 未关注 / 2 已关注 / 6 互相关注
                val attribute = json.get("data")?.takeIf { it.isJsonObject }
                    ?.asJsonObject?.optIntOr("attribute", 0) ?: 0
                attribute == 2 || attribute == 6
            }
        }.getOrElse {
            AppLog.e("follow", "relation 失败", it)
            false
        }
    }

    /** 是否已把某个用户拉黑：`/x/relation?fid=` 的 attribute 为 128 表示已拉黑 */
    suspend fun isBlocked(mid: Long): Boolean {
        if (!isLoggedIn() || mid <= 0) return false
        return runCatching {
            val json = BiliHttp.rawGet(
                "${BiliApi.API}/x/relation?fid=$mid",
                "https://space.bilibili.com/$mid"
            )
            val attribute = json.get("data")?.takeIf { it.isJsonObject }
                ?.asJsonObject?.optIntOr("attribute", 0) ?: 0
            (attribute and 128) != 0
        }.getOrDefault(false)
    }

    /** 拉黑 / 移出黑名单（act = 5 拉黑，act = 6 取消拉黑；re_src 11 = 空间） */
    suspend fun setBlocked(mid: Long, blocked: Boolean) {
        requireLogin()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/relation/modify",
            form = mapOf(
                "fid" to mid.toString(),
                "act" to if (blocked) "5" else "6",
                "re_src" to "11",
                "csrf" to csrfOrThrow()
            ),
            referer = "https://space.bilibili.com/$mid"
        )
        ensureOk(json)
    }

    /** 该视频是否已在稍后再看里 */
    suspend fun isInWatchLater(aid: Long): Boolean {
        if (!isLoggedIn()) return false
        return runCatching {
            val url = WbiSigner.sign(
                "${BiliApi.API}/x/v2/history/toview",
                mapOf("ps" to "40")
            )
            val type = object : TypeToken<BiliResponse<WatchLaterData>>() {}.type
            val response = BiliHttp.get<BiliResponse<WatchLaterData>>(url, type)
            response.data?.list.orEmpty().any { it.aid == aid }
        }.getOrDefault(false)
    }

    /** 取消稍后再看 */
    suspend fun removeWatchLater(aid: Long) {
        requireLogin()
        BiliApi.ensureBuvid()
        val json = BiliHttp.postForm(
            url = "${BiliApi.API}/x/v2/history/toview/del",
            form = mapOf("aid" to aid.toString(), "csrf" to csrfOrThrow()),
            referer = "https://www.bilibili.com/video/av$aid"
        )
        ensureOk(json)
    }

    // ---------------- 内部工具 ----------------

    private fun ensureOk(json: JsonObject) {
        val code = json.optIntOr("code", -1)
        if (code != 0) {
            AppLog.e("api", "code=$code resp=${json.toString().take(500)}")
            throw BiliException(
                code,
                json.optStringOr("message").ifBlank { "请求失败（code $code）" }
            )
        }
        // 匿名搜索被风控时会返回 voucher，而不是结果
        if (json.get("data")?.takeIf { it.isJsonObject }
                ?.asJsonObject?.has("v_voucher") == true
        ) {
            AppLog.e("api", "voucher resp=${json.toString().take(400)}")
            throw BiliException(-352, "请求被风控拦截，请稍后重试或先登录")
        }
    }

    private fun dataOf(json: JsonObject): JsonObject =
        json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw BiliException(-1, "返回数据为空")

    private companion object {
        const val PAGE_SIZE = 20
    }
}
