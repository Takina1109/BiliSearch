package com.takina.bilisearch.ui.screens.login

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.util.QrCode
import kotlinx.coroutines.delay

/** 登录方式 */
enum class LoginMode(val label: String) {
    PASSWORD("账号密码登录"),
    QR("扫码登录")
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    vm: LoginViewModel = viewModel()
) {
    val state = vm.state
    // 默认账号密码登录
    var mode by remember { mutableStateOf(LoginMode.PASSWORD) }

    LaunchedEffect(state.success) {
        if (state.success) {
            delay(600)
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = mode.label, onBack = onBack)

        when (mode) {
            LoginMode.PASSWORD -> PasswordPane(
                onSwitchToQr = { mode = LoginMode.QR },
                onCookie = vm::onWebLogin
            )

            LoginMode.QR -> QrPane(
                state = state,
                onRefresh = vm::refresh,
                onSwitchToPassword = { mode = LoginMode.PASSWORD }
            )
        }
    }
}

// ---------------- 账号密码登录（内嵌官方登录页） ----------------

/**
 * B 站的账号密码登录**强制要求极验人机验证**：实测不带 validate 时，
 * 无论 `source=main_web` / `main_mini` 还是老接口 `/web/login/v2`，一律返回
 * `-105 验证码错误`；而极验是 JS 滑块，无法用纯接口实现。
 *
 * 所以这里内嵌官方登录页，让用户在页面内完成账号密码（含滑块）或短信登录，
 * 登录成功后从 WebView 的 Cookie 里把 SESSDATA 等同步进 App 自己的 CookieJar。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PasswordPane(
    onSwitchToQr: () -> Unit,
    onCookie: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                // 残留的旧登录态要先清掉，否则一进页面就会被误判为「登录成功」
                if (!AppGraph.repo.isLoggedIn()) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // 少数网络环境下系统证书校验会误报，直接放行（页面是 B 站官方域名）
                            handler?.proceed()
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            }
        )

        LoginSwitchRow(
            hint = "也可以用手机扫码登录",
            action = "使用扫码登录 ›",
            onClick = onSwitchToQr
        )

        // 轮询 WebView 的 Cookie，一旦出现 SESSDATA 就说明登录成功
        LaunchedEffect(Unit) {
            while (true) {
                delay(700)
                val raw = CookieManager.getInstance().getCookie("https://www.bilibili.com")
                val hasSession = raw
                    ?.split(';')
                    ?.any { it.trim().startsWith("SESSDATA=") } == true
                if (hasSession && raw != null) {
                    onCookie(raw)
                    break
                }
            }
        }
    }
}

private const val LOGIN_URL = "https://passport.bilibili.com/login"

// ---------------- 扫码登录 ----------------

@Composable
private fun ColumnScope.QrPane(
    state: LoginUiState,
    onRefresh: () -> Unit,
    onSwitchToPassword: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(240.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val content = state.qrContent
            if (content != null) {
                val image = remember(content) { QrCode.generate(content, 720) }
                Image(
                    bitmap = image,
                    contentDescription = "登录二维码",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRefresh) {
            Text("刷新二维码")
        }

        Spacer(Modifier.weight(1f))

        LoginSwitchRow(
            hint = "不想扫码？",
            action = "使用账号密码登录 ›",
            onClick = onSwitchToPassword
        )
    }
}

@Composable
private fun LoginSwitchRow(hint: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClick) {
            Text(action, style = MaterialTheme.typography.bodySmall)
        }
    }
}
