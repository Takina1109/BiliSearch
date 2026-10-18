package com.takina.bilisearch.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.takina.bilisearch.ui.screens.list.ContentListScreen
import com.takina.bilisearch.ui.screens.login.LoginScreen
import com.takina.bilisearch.ui.screens.me.MeScreen
import com.takina.bilisearch.ui.screens.player.BangumiWebPlayer
import com.takina.bilisearch.ui.screens.player.PlayerScreen
import com.takina.bilisearch.ui.screens.profile.EditProfileScreen
import com.takina.bilisearch.ui.screens.search.SearchScreen
import com.takina.bilisearch.ui.screens.settings.BlacklistScreen
import com.takina.bilisearch.ui.screens.settings.SettingsScreen
import com.takina.bilisearch.ui.screens.user.UserProfileScreen

@Composable
fun BiliNavHost() {
    val nav = rememberNavController()

    // 快速连点时只让第一次跳转生效，避免重复入栈导致的空白页
    val lastNavigateAt = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val guardedNavigate: (() -> Unit) -> Unit = remember {
        { action ->
            val now = SystemClock.uptimeMillis()
            val previous = lastNavigateAt.get()
            if (now - previous > 450L && lastNavigateAt.compareAndSet(previous, now)) {
                action()
            }
        }
    }

    // Surface 同时提供背景色与内容色（LocalContentColor），
    // 这样夜间模式下未显式设置颜色的文字也会变成白字
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.SEARCH,
                modifier = Modifier.fillMaxSize(),
                // 关闭转场动画：快速「进入-返回-再进入」时，转场被打断会残留上一屏
                // （黑屏/白屏、旧界面还能点击）。直接切换最稳。
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onOpenMe = {
                            guardedNavigate { nav.navigate(Routes.ME) { launchSingleTop = true } }
                        },
                        onOpenUser = { mid ->
                            guardedNavigate { nav.navigate(Routes.user(mid)) }
                        },
                        onOpenSettings = {
                            guardedNavigate {
                                nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
                            }
                        },
                        onOpenVideo = { bvid ->
                            guardedNavigate { nav.navigate(Routes.video(bvid)) }
                        },
                        onOpenBangumi = { seasonId, epId ->
                            guardedNavigate { nav.navigate(Routes.bangumi(seasonId, epId)) }
                        }
                    )
                }

                composable(
                    route = Routes.VIDEO,
                    arguments = listOf(navArgument("bvid") { type = NavType.StringType })
                ) { entry ->
                    val bvid = entry.arguments?.getString("bvid").orEmpty()
                    PlayerScreen(
                        bvid = bvid,
                        onBack = { nav.popBackStack() },
                        onOpenUser = { mid -> guardedNavigate { nav.navigate(Routes.user(mid)) } },
                        onOpenVideo = { nextBvid ->
                            guardedNavigate { nav.navigate(Routes.video(nextBvid)) }
                        },
                        onOpenEpisode = { nextEp ->
                            guardedNavigate { nav.navigate(Routes.bangumi(epId = nextEp)) }
                        }
                    )
                }

                composable(
                    route = Routes.BANGUMI,
                    arguments = listOf(
                        navArgument("seasonId") {
                            type = NavType.StringType
                            defaultValue = "0"
                        },
                        navArgument("epId") {
                            type = NavType.StringType
                            defaultValue = "0"
                        }
                    )
                ) { entry ->
                    val seasonId = entry.arguments?.getString("seasonId")?.toLongOrNull() ?: 0L
                    val epId = entry.arguments?.getString("epId")?.toLongOrNull() ?: 0L
                    // 番剧优先用原生播放器（走普通视频接口拿 mcdn 地址，实测可播）
                    PlayerScreen(
                        bvid = "",
                        epId = epId,
                        seasonId = seasonId,
                        onBack = { nav.popBackStack() },
                        onOpenUser = { mid -> guardedNavigate { nav.navigate(Routes.user(mid)) } },
                        onOpenVideo = { nextBvid ->
                            guardedNavigate { nav.navigate(Routes.video(nextBvid)) }
                        },
                        onOpenEpisode = { nextEp ->
                            guardedNavigate { nav.navigate(Routes.bangumi(seasonId, nextEp)) }
                        },
                        onFallbackToWeb = {
                            guardedNavigate {
                                nav.navigate(Routes.bangumiWeb(seasonId, epId)) {
                                    popUpTo(Routes.BANGUMI) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(
                    route = Routes.BANGUMI_WEB,
                    arguments = listOf(
                        navArgument("seasonId") {
                            type = NavType.StringType
                            defaultValue = "0"
                        },
                        navArgument("epId") {
                            type = NavType.StringType
                            defaultValue = "0"
                        }
                    )
                ) { entry ->
                    val seasonId = entry.arguments?.getString("seasonId")?.toLongOrNull() ?: 0L
                    val epId = entry.arguments?.getString("epId")?.toLongOrNull() ?: 0L
                    // 兜底：内置播放器实在播不了时，用站内网页播放器
                    BangumiWebPlayer(
                        seasonId = seasonId,
                        epId = epId,
                        onBack = { nav.popBackStack() },
                        onUseNative = {
                            guardedNavigate {
                                nav.navigate(Routes.bangumi(seasonId, epId)) {
                                    popUpTo(Routes.BANGUMI_WEB) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenBlacklist = {
                            nav.navigate(Routes.BLACKLIST) { launchSingleTop = true }
                        },
                        onOpenLogin = {
                            nav.navigate(Routes.LOGIN) { launchSingleTop = true }
                        }
                    )
                }

                composable(Routes.BLACKLIST) {
                    BlacklistScreen(
                        onBack = { nav.popBackStack() },
                        onOpenUser = { mid -> nav.navigate(Routes.user(mid)) }
                    )
                }

                composable(
                    route = Routes.USER,
                    arguments = listOf(
                        navArgument("mid") { type = NavType.LongType }
                    )
                ) { entry ->
                    val mid = entry.arguments?.getLong("mid") ?: 0L
                    UserProfileScreen(
                        mid = mid,
                        onBack = { nav.popBackStack() },
                        onOpenSubmissions = {
                            nav.navigate(
                                Routes.list(ListKind.Submissions, title = "TA的投稿", mid = mid)
                            )
                        },
                        onOpenFavorites = {
                            nav.navigate(
                                Routes.list(ListKind.FavoriteFolders, title = "TA的收藏", mid = mid)
                            )
                        },
                        onOpenCollections = {
                            nav.navigate(
                                Routes.list(ListKind.Collections, title = "TA的合集", mid = mid)
                            )
                        },
                        onOpenFollowing = {
                            nav.navigate(
                                Routes.list(ListKind.Following, title = "TA的关注", mid = mid)
                            )
                        },
                        onOpenFollowers = {
                            nav.navigate(
                                Routes.list(ListKind.Followers, title = "TA的粉丝", mid = mid)
                            )
                        },
                        onOpenBangumis = {
                            nav.navigate(
                                Routes.list(ListKind.Bangumis, title = "TA的追番", mid = mid)
                            )
                        }
                    )
                }

                composable(Routes.ME) {
                    MeScreen(
                        onBack = { nav.popBackStack() },
                        onLogin = { nav.navigate(Routes.LOGIN) { launchSingleTop = true } },
                        onEditProfile = {
                            nav.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true }
                        },
                        onOpenList = { kind, folderId, title ->
                            nav.navigate(Routes.list(kind, folderId, title))
                        }
                    )
                }

                composable(Routes.EDIT_PROFILE) {
                    EditProfileScreen(onBack = { nav.popBackStack() })
                }

                composable(Routes.LOGIN) {
                    LoginScreen(onBack = { nav.popBackStack() })
                }

                composable(
                    route = Routes.LIST,
                    arguments = listOf(
                        navArgument("kind") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("folderId") {
                            type = NavType.StringType
                            defaultValue = "-1"
                        },
                        navArgument("title") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("mid") {
                            type = NavType.StringType
                            defaultValue = "-1"
                        }
                    )
                ) { entry ->
                    val kindName = entry.arguments?.getString("kind").orEmpty()
                    val kind = ListKind.entries.firstOrNull { it.name == kindName }
                        ?: ListKind.History
                    val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: -1L
                    val userMid = entry.arguments?.getString("mid")?.toLongOrNull() ?: -1L
                    val title = entry.arguments?.getString("title").orEmpty()
                        .ifBlank { kind.title }

                    ContentListScreen(
                        kind = kind,
                        folderId = folderId,
                        userMid = userMid,
                        title = title,
                        onBack = { nav.popBackStack() },
                        onOpenFolder = { id, name ->
                            nav.navigate(
                                Routes.list(ListKind.Favorites, id, name, mid = userMid)
                            )
                        },
                        onOpenUser = { targetMid ->
                            nav.navigate(Routes.user(targetMid))
                        },
                        onOpenCollection = { seasonId, name ->
                            nav.navigate(
                                Routes.list(
                                    ListKind.CollectionVideos,
                                    folderId = seasonId,
                                    title = name,
                                    mid = userMid
                                )
                            )
                        },
                        onOpenVideo = { bvid ->
                            guardedNavigate { nav.navigate(Routes.video(bvid)) }
                        },
                        onOpenBangumi = { seasonId, epId ->
                            guardedNavigate { nav.navigate(Routes.bangumi(seasonId, epId)) }
                        }
                    )
                }
            }
        }
    }
}
