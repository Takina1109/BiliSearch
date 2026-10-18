package com.takina.bilisearch.ui.screens.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.repo.ProfileSummary
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val profile: ProfileSummary = ProfileSummary(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 我是否已拉黑 TA */
    val blocked: Boolean = false,
    val message: String? = null
)

class UserProfileViewModel(private val mid: Long) : ViewModel() {

    var state by mutableStateOf(UserProfileUiState())
        private set

    init {
        load()
        refreshBlockState()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                state = state.copy(profile = AppGraph.repo.userProfile(mid), loading = false)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    private fun refreshBlockState() {
        viewModelScope.launch {
            val blocked = runCatching { AppGraph.repo.isBlocked(mid) }.getOrDefault(false)
            state = state.copy(blocked = blocked)
        }
    }

    /** 拉黑 / 移出黑名单 */
    fun toggleBlock() {
        viewModelScope.launch {
            val target = !state.blocked
            runCatching { AppGraph.repo.setBlocked(mid, target) }
                .onSuccess {
                    state = state.copy(
                        blocked = target,
                        message = if (target) "已加入黑名单" else "已移出黑名单"
                    )
                }
                .onFailure {
                    state = state.copy(message = it.message ?: "操作失败")
                }
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }
}

class UserProfileViewModelFactory(
    private val mid: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UserProfileViewModel(mid) as T
    }
}
