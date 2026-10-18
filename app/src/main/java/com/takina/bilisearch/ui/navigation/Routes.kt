package com.takina.bilisearch.ui.navigation

import android.net.Uri

enum class ListKind(val title: String) {
    History("观看记录"),
    Submissions("我的投稿"),
    WatchLater("稍后再看"),
    FavoriteFolders("我的收藏"),
    Favorites("收藏夹"),
    Collections("合集"),
    CollectionVideos("合集"),
    Following("我的关注"),
    Followers("我的粉丝"),
    Bangumis("番剧")
}

object Routes {
    const val SEARCH = "search"
    const val ME = "me"
    const val LOGIN = "login"
    const val EDIT_PROFILE = "edit_profile"
    const val SETTINGS = "settings"
    const val BLACKLIST = "blacklist"
    const val ABOUT = "about"

    const val LIST = "list?kind={kind}&folderId={folderId}&title={title}&mid={mid}"
    const val USER = "user/{mid}"
    const val VIDEO = "video/{bvid}"
    const val BANGUMI = "bangumi?seasonId={seasonId}&epId={epId}"
    const val BANGUMI_WEB = "bangumi_web?seasonId={seasonId}&epId={epId}"

    fun bangumi(seasonId: Long = 0L, epId: Long = 0L): String =
        "bangumi?seasonId=$seasonId&epId=$epId"

    fun bangumiWeb(seasonId: Long = 0L, epId: Long = 0L): String =
        "bangumi_web?seasonId=$seasonId&epId=$epId"

    fun list(
        kind: ListKind,
        folderId: Long = -1L,
        title: String = "",
        mid: Long = -1L
    ): String {
        return "list?kind=${kind.name}&folderId=$folderId" +
            "&title=${Uri.encode(title)}&mid=$mid"
    }

    fun user(mid: Long): String = "user/$mid"

    fun video(bvid: String): String = "video/$bvid"
}
