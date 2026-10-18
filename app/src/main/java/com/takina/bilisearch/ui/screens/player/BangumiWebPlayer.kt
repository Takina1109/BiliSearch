package com.takina.bilisearch.ui.screens.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.takina.bilisearch.data.net.BiliHttp
import com.takina.bilisearch.ui.components.TopBar

/**
 * 番剧播放（站内网页播放器）。
 *
 * 为什么不用原生播放器：`/pgc/player/web/playurl` 签出来的地址**恒为 `platform=pc`**
 * （传 `platform=html5` 会被忽略），在部分网络下 upos/cn 节点对这签名一律 403，
 * 实测同一集连试几十次不同清晰度、不同主机都播不了。而 B 站自己的网页播放器是能看的，
 * 所以这里内嵌官方播放页兜底，页内自带弹幕、清晰度和选集。
 *
 * 顶栏留了一个「内置播放器」入口，网络允许时可以切回原生播放器。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BangumiWebPlayer(
    seasonId: Long,
    epId: Long,
    onBack: () -> Unit,
    onUseNative: () -> Unit
) {
    val context = LocalContext.current
    val url = when {
        epId > 0L -> "https://www.bilibili.com/bangumi/play/ep$epId"
        seasonId > 0L -> "https://www.bilibili.com/bangumi/play/ss$seasonId"
        else -> "https://www.bilibili.com/bangumi/"
    }

    var webViewRef = remember { arrayOfNulls<WebView>(1) }
    var fullscreenDialog = remember { arrayOfNulls<Dialog>(1) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { fullscreenDialog[0]?.dismiss() }
            runCatching {
                webViewRef[0]?.let { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                }
            }
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "番剧",
            onBack = onBack,
            actions = {
                TextButton(onClick = onUseNative) { Text("内置播放器") }
            }
        )
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // 把 App 的登录态同步进 WebView，否则大会员内容看不到
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    BiliHttp.cookieJar.snapshot().forEach { (name, value) ->
                        runCatching {
                            cookieManager.setCookie(
                                "https://www.bilibili.com",
                                "$name=$value; Domain=.bilibili.com; Path=/"
                            )
                        }
                    }
                    cookieManager.flush()

                    WebView(ctx).apply {
                        webViewRef[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                runCatching {
                                    fullscreenDialog[0]?.dismiss()
                                    val dialog = Dialog(
                                        context,
                                        android.R.style.Theme_Black_NoTitleBar_Fullscreen
                                    )
                                    val container = android.widget.FrameLayout(context)
                                    container.addView(
                                        view,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    dialog.setContentView(container)
                                    dialog.setOnDismissListener {
                                        runCatching {
                                            container.removeAllViews()
                                            callback?.onCustomViewHidden()
                                        }
                                    }
                                    dialog.show()
                                    fullscreenDialog[0] = dialog
                                }.onFailure {
                                    callback?.onCustomViewHidden()
                                }
                            }

                            override fun onHideCustomView() {
                                runCatching { fullscreenDialog[0]?.dismiss() }
                                fullscreenDialog[0] = null
                            }
                        }
                        loadUrl(url)
                    }
                }
            )
        }
    }
}
