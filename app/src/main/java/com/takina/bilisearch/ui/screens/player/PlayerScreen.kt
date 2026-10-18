package com.takina.bilisearch.ui.screens.player

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CommentsDisabled
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.takina.bilisearch.R
import com.takina.bilisearch.data.local.AppLog
import com.takina.bilisearch.data.net.BiliHttp
import com.takina.bilisearch.data.repo.CommentEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/** 播放错误立即上报，不在同一个地址上反复重试 */
@OptIn(UnstableApi::class)
private val FAST_FAIL_POLICY = object : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): Long = androidx.media3.common.C.TIME_UNSET
}

private const val BOOST_HINT = "2 倍速播放中"

/** 播放顺序 */
enum class PlayOrder(val label: String) {
    STOP("播完停止"),
    NEXT("顺序播放"),
    SINGLE("单个循环"),
    LIST("列表循环")
}

enum class TimerOption(val label: String, val minutes: Int) {
    OFF("关闭", 0),
    M15("15 分钟", 15),
    M30("30 分钟", 30),
    M45("45 分钟", 45),
    M60("60 分钟", 60),
    CUSTOM("自定义", -1)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    bvid: String,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenEpisode: (Long) -> Unit = {},
    /** 番剧内置播放器彻底播不了时切到网页播放器 */
    onFallbackToWeb: (() -> Unit)? = null,
    epId: Long = 0L,
    seasonId: Long = 0L,
    vm: PlayerViewModel = viewModel(
        key = when {
            epId > 0 -> "ep$epId"
            seasonId > 0 -> "ss$seasonId"
            else -> bvid
        },
        factory = PlayerViewModel.Factory(bvid, epId, seasonId)
    )
) {
    val state = vm.state
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 番剧没有 bvid，用 epId 区分「同一页内的换集」和「换视频」
    val screenKey = when {
        epId > 0 -> "ep$epId"
        seasonId > 0 -> "ss$seasonId"
        else -> bvid
    }
    val shareUrl = if (state.isBangumi && state.epId > 0) {
        "https://www.bilibili.com/bangumi/play/ep${state.epId}"
    } else {
        "https://www.bilibili.com/video/$bvid"
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var boosting by remember { mutableStateOf(false) }
    var mirror by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showCoinDialog by remember { mutableStateOf(false) }
    var showFavoriteSheet by remember { mutableStateOf(false) }
    var pendingFolders by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var seekHint by remember { mutableStateOf<String?>(null) }

    // 弹幕设置
    var danmakuOn by remember { mutableStateOf(true) }
    var danmakuScroll by remember { mutableStateOf(true) }
    var danmakuTop by remember { mutableStateOf(true) }
    var danmakuBottom by remember { mutableStateOf(true) }
    var danmakuColorful by remember { mutableStateOf(true) }
    var danmakuOpacity by remember { mutableFloatStateOf(0.9f) }
    var danmakuScale by remember { mutableFloatStateOf(1f) }
    var danmakuDuration by remember { mutableLongStateOf(8000L) }

    var playOrder by remember { mutableStateOf(PlayOrder.STOP) }
    var timerOption by remember { mutableStateOf(TimerOption.OFF) }
    // 定时关闭：用「结束时间戳」驱动，这样才能实时算出剩余时间
    var timerEndsAt by remember { mutableLongStateOf(0L) }
    var timerRemainingMs by remember { mutableLongStateOf(0L) }
    var showCustomTimer by remember { mutableStateOf(false) }

    // 评论输入条：平时收起，点一下在原位置展开到输入法上方
    var commentEditing by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var commentReplyTo by remember { mutableStateOf<CommentEntry?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(BiliHttp.mediaClient)
                )
                    // CDN 返回 403 时不要重试 3 次（否则每个坏地址都要等 3.5 秒）
                    .setLoadErrorHandlingPolicy(FAST_FAIL_POLICY)
            )
            .build()
            .apply { playWhenReady = true }
    }

    var isPlaying by remember { mutableStateOf(true) }
    // 播放进度用 State 对象传递：读它的只是弹幕层 / 控件 / 进度条，
    // 避免每 100ms 把整个 PlayerScreen（含评论列表）重组一遍
    val positionState = remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val currentSpeed = rememberUpdatedState(speed)
    val currentOrder = rememberUpdatedState(playOrder)
    val currentNextBvid = rememberUpdatedState(vm.nextBvid())
    val currentNextEpisode = rememberUpdatedState(vm.nextEpisodeId())
    val currentSource = rememberUpdatedState(state.source)
    val onOpenVideoState = rememberUpdatedState(onOpenVideo)
    val onOpenEpisodeState = rememberUpdatedState(onOpenEpisode)
    val onFallbackToWebState = rememberUpdatedState(onFallbackToWeb)

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // 换源（支持在多候选 CDN 地址之间自动重试）
    var loadedBvid by remember { mutableStateOf<String?>(null) }
    var lastSource by remember { mutableStateOf<PlaySource?>(null) }
    val attempt = remember { mutableIntStateOf(0) }
    LaunchedEffect(state.source, attempt.intValue) {
        val src = state.source ?: return@LaunchedEffect
        // 换了新的播放源（重试拿到新地址 / 切清晰度）就从头试第一个候选
        if (lastSource !== src) {
            lastSource = src
            attempt.intValue = 0
        }
        val index = attempt.intValue.coerceIn(0, src.candidates.lastIndex.coerceAtLeast(0))
        val candidate = src.candidateAt(index) ?: return@LaunchedEffect
        val videoUrl = candidate.videoUrl
        val audioUrl = candidate.audioUrl
        // 切换视频时从头播放，只有切换清晰度才保留进度
        val resumeAt = if (loadedBvid == screenKey) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            0L
        }
        loadedBvid = screenKey
        exoPlayer.stop()
        playerError = null
        AppLog.d(
            "player",
            "play index=$index/${src.candidates.size} " +
                "video=${runCatching { java.net.URI(videoUrl).host }.getOrNull()} " +
                "audio=${audioUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }}"
        )
        exoPlayer.setMediaSource(buildMediaSource(videoUrl, audioUrl), resumeAt)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        durationMs = 0
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val src = currentSource.value
                val next = attempt.intValue + 1
                val httpError = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                AppLog.e(
                    "player",
                    "code=${error.errorCodeName} http=${httpError?.responseCode} " +
                        "uri=${httpError?.dataSpec?.uri?.host}${httpError?.dataSpec?.uri?.encodedPath} " +
                        "attempt=${attempt.intValue}/${src?.candidates?.size ?: 0} " +
                        "cause=${error.cause?.message}"
                )
                if (src != null && next <= src.candidates.lastIndex) {
                    // 换一个已经拿到的候选地址
                    attempt.intValue = next
                    playerError = null
                } else if (vm.reloadPlaySource()) {
                    // 重新请求 playurl，B 站会换一个 CDN
                    playerError = "正在重试播放…"
                } else if (onFallbackToWebState.value != null) {
                    // 番剧内置播放器救不回来，切到站内网页播放器
                    playerError = "内置播放器播不了，正在切换网页播放器…"
                    onFallbackToWebState.value?.invoke()
                } else {
                    // DASH 都不行，切到普通 mp4/flv 兼容模式
                    vm.useProgressiveFallback()
                    playerError = "播放失败（${error.errorCodeName}），已尝试兼容模式"
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                }
                if (playbackState == Player.STATE_ENDED) {
                    controlsVisible = true
                    when (currentOrder.value) {
                        PlayOrder.STOP -> Unit
                        PlayOrder.SINGLE -> {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }
                        PlayOrder.NEXT, PlayOrder.LIST -> {
                            val nextEp = currentNextEpisode.value
                            val next = currentNextBvid.value
                            if (nextEp != null) {
                                onOpenEpisodeState.value(nextEp)
                            } else if (next != null) {
                                onOpenVideoState.value(next)
                            } else if (currentOrder.value == PlayOrder.LIST) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            }
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionState.longValue = exoPlayer.currentPosition.coerceAtLeast(0)
            val d = exoPlayer.duration
            if (d > 0 && abs(d - durationMs) > 500) durationMs = d
            delay(100)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(speed, boosting) {
        exoPlayer.setPlaybackSpeed(if (boosting) 2f else speed)
    }

    // 定时关闭：每 250ms 刷新一次剩余时间，到点暂停
    LaunchedEffect(timerEndsAt) {
        if (timerEndsAt <= 0L) {
            timerRemainingMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            val left = timerEndsAt - System.currentTimeMillis()
            timerRemainingMs = left.coerceAtLeast(0L)
            if (left <= 0L) {
                exoPlayer.pause()
                Toast.makeText(context, "已按定时关闭播放", Toast.LENGTH_SHORT).show()
                timerEndsAt = 0L
                timerOption = TimerOption.OFF
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    // 上报观看进度，视频才会出现在「观看记录」里
    LaunchedEffect(screenKey) {
        var lastReportedMs = 0L
        while (true) {
            delay(5_000)
            val pos = exoPlayer.currentPosition
            if (exoPlayer.isPlaying && pos > 3_000 && pos - lastReportedMs > 8_000) {
                lastReportedMs = pos
                vm.reportProgress(pos / 1000)
            }
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.consumeMessage()
    }

    LaunchedEffect(seekHint) {
        val hint = seekHint ?: return@LaunchedEffect
        // 2 倍速提示由手势自己控制显隐，不参与自动消失
        if (hint == BOOST_HINT) return@LaunchedEffect
        delay(700)
        seekHint = null
    }

    fun setFullscreen(value: Boolean) {
        isFullscreen = value
        activity ?: return
        activity.requestedOrientation = if (value) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (value) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun seekBy(deltaMs: Long) {
        val total = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, total)
        exoPlayer.seekTo(target)
        seekHint = if (deltaMs > 0) "快进 10 秒" else "快退 10 秒"
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = isFullscreen) { setFullscreen(false) }

    // 输入框展开时，返回键先收起输入框
    BackHandler(enabled = !isFullscreen && commentEditing) {
        commentEditing = false
        commentReplyTo = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isFullscreen) Modifier.weight(1f)
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color.Black)
                .clipToBounds()
        ) {
            AndroidView(
                factory = { ctx ->
                    // 用 res/layout/view_player.xml（surface_type=texture_view）
                    // SurfaceView 的画面由系统单独合成，Compose 的 graphicsLayer 镜像对它无效
                    (android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.view_player, null) as PlayerView).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = if (mirror) -1f else 1f }
            )

            if (danmakuOn && state.danmaku.isNotEmpty()) {
                DanmakuOverlay(
                    items = state.danmaku,
                    positionState = positionState,
                    isPlaying = isPlaying,
                    speed = if (boosting) 2f else speed,
                    opacity = danmakuOpacity,
                    scale = danmakuScale,
                    durationMs = danmakuDuration,
                    showScroll = danmakuScroll,
                    showTop = danmakuTop,
                    showBottom = danmakuBottom,
                    showColorful = danmakuColorful,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 手势层：单击显隐控件 / 双击快进快退 / 长按 2 倍速
            // 注意：长按和双击必须放在同一个 pointerInput 里处理。以前拆成两个 pointerInput，
            // detectTapGestures 会先把长按 consume 掉，导致另一个手势协程立即收到「已抬手」，
            // 2 倍速刚开就被关掉（表现为提示闪一下、全程没有加速）。
            val gestureScope = rememberCoroutineScope()
            var pendingTap by remember { mutableStateOf<Job?>(null) }
            var lastTapAt by remember { mutableLongStateOf(0L) }
            var lastTapX by remember { mutableFloatStateOf(0f) }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val upBeforeLongPress = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis
                            ) {
                                waitForUpOrCancellation()
                            }
                            if (upBeforeLongPress == null) {
                                // 长按中：2 倍速，松手才恢复
                                boosting = true
                                seekHint = BOOST_HINT
                                waitForUpOrCancellation()
                                boosting = false
                                seekHint = null
                                return@awaitEachGesture
                            }

                            val now = System.currentTimeMillis()
                            val x = down.position.x
                            val isDoubleTap = now - lastTapAt < viewConfiguration.doubleTapTimeoutMillis &&
                                abs(x - lastTapX) < viewConfiguration.touchSlop * 3
                            if (isDoubleTap) {
                                lastTapAt = 0L
                                pendingTap?.cancel()
                                pendingTap = null
                                val width = size.width.toFloat()
                                val edge = width * 2f / 7f
                                when {
                                    x < edge -> seekBy(-10_000)
                                    x > width - edge -> seekBy(10_000)
                                    else -> {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        controlsVisible = true
                                    }
                                }
                            } else {
                                // 等一个双击窗口，没等到第二下才算单击
                                lastTapAt = now
                                lastTapX = x
                                pendingTap?.cancel()
                                pendingTap = gestureScope.launch {
                                    delay(viewConfiguration.doubleTapTimeoutMillis)
                                    controlsVisible = !controlsVisible
                                }
                            }
                        }
                    }
            )

            seekHint?.let { hint ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(hint, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 定时关闭的实时倒计时，一直显示在右上角
            if (timerRemainingMs > 0L) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "定时 " + formatTime(timerRemainingMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = Color.White)
                }
            }
            state.error?.let { error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            playerError?.let { error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (controlsVisible) {
                PlayerControls(
                    isFullscreen = isFullscreen,
                    isPlaying = isPlaying,
                    danmakuOn = danmakuOn,
                    speed = speed,
                    positionState = positionState,
                    durationMs = durationMs,
                    showSpeedMenu = showSpeedMenu,
                    onShowSpeedMenuChange = { showSpeedMenu = it },
                    onToggleDanmaku = {
                        danmakuOn = !danmakuOn
                        controlsVisible = true
                    },
                    onBack = {
                        if (isFullscreen) setFullscreen(false) else onBack()
                    },
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        controlsVisible = true
                    },
                    onSpeedSelected = { value ->
                        speed = value
                        showSpeedMenu = false
                    },
                    onToggleFullscreen = { setFullscreen(!isFullscreen) },
                    onOpenDrawer = { showDrawer = true }
                )
            }

            // 和 B 站一样贴在视频最下方的细进度条
            ThinProgressBar(
                positionState = positionState,
                durationMs = durationMs,
                onSeek = { ratio ->
                    val total = durationMs
                    if (total > 0) exoPlayer.seekTo((total * ratio).toLong())
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (!isFullscreen) {
            VideoDetailContent(
                modifier = Modifier.weight(1f),
                state = state,
                onOpenUser = onOpenUser,
                onOpenVideo = onOpenVideo,
                onOpenEpisode = onOpenEpisode,
                onLike = vm::toggleLike,
                onCoin = { showCoinDialog = true },
                onFavorite = {
                    pendingFolders = state.favoriteFolders
                        .filter { it.selected }
                        .map { it.id }
                        .toSet()
                    showFavoriteSheet = true
                },
                onWatchLater = vm::addWatchLater,
                onShare = {
                    val url = shareUrl
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, state.detail?.title.orEmpty())
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(intent, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onFollow = vm::toggleFollow,
                onSortChange = vm::switchSort,
                onLikeComment = vm::toggleCommentLike,
                onReplyComment = { comment ->
                    commentReplyTo = comment
                    commentEditing = true
                },
                onLoadMoreComments = { vm.loadComments() }
            )

            // 最下方长长的评论条（B 站同款），点击后原地展开成输入框
            CommentInputBar(
                editing = commentEditing,
                draft = commentDraft,
                replyToName = commentReplyTo?.name,
                posting = state.commentPosting,
                total = if (state.commentsTotal > 0) state.commentsTotal
                else (state.detail?.stat?.reply ?: 0).toInt(),
                onStartEdit = { commentEditing = true },
                onDraftChange = { commentDraft = it },
                onSend = {
                    vm.postComment(commentDraft, commentReplyTo)
                    commentDraft = ""
                    commentEditing = false
                    commentReplyTo = null
                }
            )
        }
    }

    if (showDrawer) {
        PlayerDrawer(
            qualities = state.qualities,
            currentQn = state.currentQn,
            onQualitySelected = { qn ->
                vm.selectQuality(qn)
                showDrawer = false
            },
            playOrder = playOrder,
            onPlayOrderChange = { playOrder = it },
            timerOption = timerOption,
            timerRemainingMs = timerRemainingMs,
            onTimerChange = { option ->
                when (option) {
                    TimerOption.OFF -> {
                        timerOption = TimerOption.OFF
                        timerEndsAt = 0L
                    }
                    TimerOption.CUSTOM -> {
                        showDrawer = false
                        showCustomTimer = true
                    }
                    else -> {
                        timerOption = option
                        timerEndsAt = System.currentTimeMillis() + option.minutes * 60_000L
                    }
                }
            },
            mirror = mirror,
            onMirrorChange = { mirror = it },
            danmakuOn = danmakuOn,
            onDanmakuChange = { danmakuOn = it },
            danmakuScroll = danmakuScroll,
            onScrollChange = { danmakuScroll = it },
            danmakuTop = danmakuTop,
            onTopChange = { danmakuTop = it },
            danmakuBottom = danmakuBottom,
            onBottomChange = { danmakuBottom = it },
            danmakuColorful = danmakuColorful,
            onColorfulChange = { danmakuColorful = it },
            danmakuOpacity = danmakuOpacity,
            onOpacityChange = { danmakuOpacity = it },
            danmakuScale = danmakuScale,
            onScaleChange = { danmakuScale = it },
            danmakuDuration = danmakuDuration,
            onDurationChange = { danmakuDuration = it },
            onDismiss = { showDrawer = false }
        )
    }

    if (showCoinDialog) {
        CoinDialog(
            onDismiss = { showCoinDialog = false },
            onConfirm = { count ->
                showCoinDialog = false
                vm.addCoin(count)
            }
        )
    }

    if (showCustomTimer) {
        CustomTimerDialog(
            onDismiss = { showCustomTimer = false },
            onConfirm = { minutes ->
                showCustomTimer = false
                timerOption = TimerOption.CUSTOM
                timerEndsAt = System.currentTimeMillis() + minutes * 60_000L
            }
        )
    }

    LaunchedEffect(showFavoriteSheet) {
        if (showFavoriteSheet) vm.loadFavoriteFolders()
    }
    LaunchedEffect(showFavoriteSheet, state.favoriteFolders) {
        if (showFavoriteSheet) {
            val selected = state.favoriteFolders.filter { it.selected }.map { it.id }.toSet()
            if (selected != pendingFolders) pendingFolders = selected
        }
    }
    if (showFavoriteSheet) {
        FavoriteSheet(
            folders = state.favoriteFolders,
            selected = pendingFolders,
            onToggle = { id ->
                pendingFolders = if (id in pendingFolders) {
                    pendingFolders - id
                } else {
                    pendingFolders + id
                }
            },
            onConfirm = {
                vm.applyFavorite(pendingFolders)
                showFavoriteSheet = false
            },
            onDismiss = { showFavoriteSheet = false }
        )
    }
}

@OptIn(UnstableApi::class)
private fun buildMediaSource(videoUrl: String, audioUrl: String?): MediaSource {
    val video = dataSourceFactory(videoUrl).createMediaSource(MediaItem.fromUri(videoUrl))
    return if (audioUrl.isNullOrBlank()) {
        video
    } else {
        val audio = dataSourceFactory(audioUrl).createMediaSource(MediaItem.fromUri(audioUrl))
        MergingMediaSource(video, audio)
    }
}

/** B 站官方代理对 UA 有要求，走代理的地址要单独用桌面 UA 的客户端 */
@OptIn(UnstableApi::class)
private fun dataSourceFactory(url: String): ProgressiveMediaSource.Factory {
    val client = if (url.contains("proxy-tf-all-ws")) {
        BiliHttp.proxyMediaClient
    } else {
        BiliHttp.mediaClient
    }
    return ProgressiveMediaSource.Factory(
        OkHttpDataSource.Factory(client).setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
                "Accept" to "*/*",
                "Accept-Encoding" to "identity"
            )
        )
    )
}

@Composable
private fun PlayerControls(
    isFullscreen: Boolean,
    isPlaying: Boolean,
    danmakuOn: Boolean,
    speed: Float,
    positionState: State<Long>,
    durationMs: Long,
    showSpeedMenu: Boolean,
    onShowSpeedMenuChange: (Boolean) -> Unit,
    onToggleDanmaku: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            // 弹幕开关（在「三个点」左边）
            IconButton(onClick = onToggleDanmaku) {
                Icon(
                    imageVector = if (danmakuOn) {
                        Icons.Filled.Comment
                    } else {
                        Icons.Filled.CommentsDisabled
                    },
                    contentDescription = if (danmakuOn) "关闭弹幕" else "打开弹幕",
                    tint = if (danmakuOn) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多设置", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White
                )
            }
            Text(
                text = "${formatTime(positionState.value)} / ${formatTime(durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))

            Box {
                TextButton(onClick = { onShowSpeedMenuChange(true) }) {
                    Text(
                        text = formatSpeed(speed),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { onShowSpeedMenuChange(false) }
                ) {
                    SPEED_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(formatSpeed(option)) },
                            onClick = { onSpeedSelected(option) }
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit
                    else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = Color.White
                )
            }
        }
    }
}

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun formatSpeed(value: Float): String {
    val text = if (value == value.toInt().toFloat()) {
        "${value.toInt()}.0"
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0')
    }
    return "${text}x"
}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hour = total / 3600
    val minute = (total % 3600) / 60
    val second = total % 60
    return if (hour > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hour, minute, second)
    } else {
        String.format(Locale.US, "%02d:%02d", minute, second)
    }
}

/** 自定义定时关闭：输入分钟数 */
@Composable
private fun CustomTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("30") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..600 }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义定时关闭") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    label = { Text("分钟（1-600）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                Text(
                    text = if (minutes != null) "到点后 ${minutes} 分钟后自动暂停" else "请输入 1-600 之间的整数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null,
                onClick = { minutes?.let { onConfirm(it) } }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun Context.findActivity(): android.app.Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** B 站风格的细进度条：2dp 高，可拖动 / 点击跳转 */
@Composable
private fun ThinProgressBar(
    positionState: State<Long>,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) {
        positionState.value.toFloat() / durationMs
    } else {
        0f
    }
    val shown = (if (dragging) dragValue else progress).coerceIn(0f, 1f)
    val barHeight = if (dragging) 3.dp else 2.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragValue)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragValue)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(barHeight)
                .background(Color(0xFFFB7299))
        )
    }
}
