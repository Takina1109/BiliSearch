package com.takina.bilisearch.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.repo.UserEntry
import kotlinx.coroutines.launch

data class BlacklistUiState(
    val users: List<UserEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val total: Long = 0
)

class BlacklistViewModel : ViewModel() {

    var state by mutableStateOf(BlacklistUiState())
        private set

    private val repo = AppGraph.repo
    private var page = 0

    init {
        load(append = false)
    }

    fun load(append: Boolean = false) {
        if (state.loading || state.loadingMore) return
        if (append && !state.hasMore) return

        val nextPage = if (append) page + 1 else 1
        val existing = state.users
        state = state.copy(loading = !append, loadingMore = append, error = null)

        viewModelScope.launch {
            try {
                val result = repo.blacklist(nextPage)
                page = nextPage
                state = state.copy(
                    users = if (append) existing + result.items else result.items,
                    hasMore = result.hasMore,
                    total = result.total
                )
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "加载失败")
            } finally {
                state = state.copy(loading = false, loadingMore = false)
            }
        }
    }

    /** 解除拉黑并刷新列表 */
    fun unblock(mid: Long) {
        viewModelScope.launch {
            try {
                repo.unblock(mid)
                state = state.copy(users = state.users.filterNot { it.mid == mid })
                page = 0
                load(append = false)
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "操作失败")
            }
        }
    }
}
