package com.takina.bilisearch.ui.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.repo.ProfileSummary
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: ProfileSummary = ProfileSummary(),
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * 登录账号概要：搜索首页左上角头像区与「我的」页面共用。
 */
class ProfileViewModel : ViewModel() {

    var state by mutableStateOf(
        ProfileUiState(profile = AppGraph.lastProfile ?: ProfileSummary())
    )
        private set

    private val repo = AppGraph.repo

    fun refresh() {
        if (state.loading) return
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val profile = repo.profile()
                if (profile.isLogin) AppGraph.lastProfile = profile
                state = ProfileUiState(profile = profile)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            AppGraph.lastProfile = null
            state = ProfileUiState()
        }
    }
}
