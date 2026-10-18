package com.takina.bilisearch.data

import android.content.Context
import com.takina.bilisearch.data.local.AppLog
import com.takina.bilisearch.data.local.Prefs
import com.takina.bilisearch.data.net.BiliApi
import com.takina.bilisearch.data.net.BiliHttp
import com.takina.bilisearch.data.repo.BiliRepository
import com.takina.bilisearch.data.repo.ProfileSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简依赖容器，避免为骨架工程引入 DI 框架。
 */
object AppGraph {

    private var prefsHolder: Prefs? = null
    private var repoHolder: BiliRepository? = null

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上一次拿到的登录账号资料，用于避免进入「我的」页时闪一下未登录状态 */
    @Volatile
    var lastProfile: ProfileSummary? = null

    val prefs: Prefs
        get() = prefsHolder ?: error("AppGraph 尚未初始化")

    val repo: BiliRepository
        get() = repoHolder ?: error("AppGraph 尚未初始化")

    fun init(context: Context) {
        if (repoHolder != null) return
        val prefs = Prefs(context)
        val repo = BiliRepository(prefs)
        BiliHttp.cookieJar.restore(repo.loadSavedCookies())
        prefsHolder = prefs
        repoHolder = repo

        AppLog.d("app", "初始化完成 已登录=${repo.isLoggedIn()}")

        // 已登录用户冷启动时重建 buvid3 / bili_ticket：
        // 投币、点赞等接口要求 buvid3「存在且正常」，旧的匿名 buvid 会触发风控
        if (repo.isLoggedIn()) {
            bgScope.launch {
                runCatching {
                    BiliApi.refreshBuvid()
                    repo.persistSession()
                }.onFailure {
                    AppLog.e("app", "刷新 buvid 失败", it)
                }
            }
        }
    }
}
