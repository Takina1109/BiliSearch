package com.takina.bilisearch.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.net.BiliApi
import com.takina.bilisearch.data.net.BiliHttp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LoginUiState(
    val qrContent: String? = null,
    val status: String = "",
    val loading: Boolean = false,
    val success: Boolean = false
)

class LoginViewModel : ViewModel() {

    var state by mutableStateOf(LoginUiState(status = "正在获取二维码…"))
        private set

    private val repo = AppGraph.repo
    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        viewModelScope.launch {
            state = LoginUiState(loading = true, status = "正在获取二维码…")
            try {
                val qr = repo.qrGenerate()
                state = LoginUiState(
                    qrContent = qr.url,
                    status = "请使用哔哩哔哩 App 扫码"
                )
                startPolling(qr.qrcodeKey)
            } catch (e: Exception) {
                state = LoginUiState(status = "获取二维码失败：${e.message}")
            }
        }
    }

    private fun startPolling(qrKey: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(90) {
                delay(2000)
                val poll = try {
                    repo.qrPoll(qrKey)
                } catch (e: Exception) {
                    return@launch
                }
                when (poll.code) {
                    0 -> {
                        finishLogin()
                        return@launch
                    }

                    86090 -> state = state.copy(status = "已扫码，请在手机上确认")
                    86038 -> {
                        state = state.copy(status = "二维码已过期，请刷新")
                        return@launch
                    }

                    else -> state = state.copy(status = "等待扫码…")
                }
            }
        }
    }

    /**
     * 内嵌登录页登录成功后，把 WebView 里的 Cookie 同步到 App 的 CookieJar。
     */
    fun onWebLogin(cookieHeader: String) {
        val jar = BiliHttp.cookieJar
        cookieHeader.split(';').forEach { part ->
            val sep = part.indexOf('=')
            if (sep <= 0) return@forEach
            val name = part.substring(0, sep).trim()
            val value = part.substring(sep + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) jar.put(name, value)
        }
        if (!repo.isLoggedIn()) return
        viewModelScope.launch {
            finishLogin()
        }
    }

    /** 登录成功后的收尾：持久化 + 重建风控用到的 buvid / 票据 */
    private suspend fun finishLogin() {
        repo.persistSession()
        // 登录后重建 buvid3 / bili_ticket，否则投币等互动接口会被风控
        runCatching { BiliApi.refreshBuvid() }
        repo.persistSession()
        state = state.copy(status = "登录成功", success = true)
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
