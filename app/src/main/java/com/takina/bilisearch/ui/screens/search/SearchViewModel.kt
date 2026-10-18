package com.takina.bilisearch.ui.screens.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.repo.UserEntry
import com.takina.bilisearch.data.repo.VideoEntry
import com.takina.bilisearch.ui.components.ListRow
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    Video("视频"),
    Bangumi("番剧"),
    User("用户")
}

data class SearchUiState(
    val keyword: String = "",
    val submitted: String = "",
    val tab: SearchTab = SearchTab.Video,
    val searched: Boolean = false,
    val videos: List<VideoEntry> = emptyList(),
    val bangumi: List<VideoEntry> = emptyList(),
    val users: List<UserEntry> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val total: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null
) {
    val rows: List<ListRow>
        get() = when (tab) {
            SearchTab.Video -> videos.map { ListRow.Video(it) }
            SearchTab.Bangumi -> bangumi.map { ListRow.Video(it) }
            SearchTab.User -> users.map { ListRow.User(it) }
        }
}

class SearchViewModel : ViewModel() {

    var state by mutableStateOf(SearchUiState())
        private set

    private val repo = AppGraph.repo

    fun onKeywordChange(value: String) {
        state = state.copy(keyword = value)
    }

    fun submit() {
        val keyword = state.keyword.trim()
        if (keyword.isEmpty()) return
        state = state.copy(
            submitted = keyword,
            searched = true,
            page = 0,
            hasMore = false,
            total = 0,
            videos = emptyList(),
            bangumi = emptyList(),
            users = emptyList(),
            error = null
        )
        load(page = 1)
    }

    fun switchTab(tab: SearchTab) {
        if (tab == state.tab) return
        state = state.copy(tab = tab, error = null, page = 0, hasMore = false)
        val empty = when (tab) {
            SearchTab.Video -> state.videos.isEmpty()
            SearchTab.Bangumi -> state.bangumi.isEmpty()
            SearchTab.User -> state.users.isEmpty()
        }
        if (empty && state.submitted.isNotEmpty()) {
            load(page = 1)
        }
    }

    fun backToLanding() {
        state = state.copy(searched = false)
    }

    fun loadMore() {
        if (state.loading || state.loadingMore) return
        if (!state.hasMore) return
        load(page = state.page + 1)
    }

    private fun load(page: Int) {
        val keyword = state.submitted
        if (keyword.isEmpty()) return
        val tab = state.tab

        state = state.copy(
            loading = page <= 1,
            loadingMore = page > 1,
            error = null
        )

        viewModelScope.launch {
            try {
                when (tab) {
                    SearchTab.Video -> {
                        val result = repo.searchVideos(keyword, page)
                        if (state.tab != tab) return@launch
                        state = state.copy(
                            videos = if (page <= 1) result.items else state.videos + result.items,
                            page = page,
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    SearchTab.Bangumi -> {
                        val result = repo.searchBangumi(keyword, page)
                        if (state.tab != tab) return@launch
                        state = state.copy(
                            bangumi = if (page <= 1) result.items else state.bangumi + result.items,
                            page = page,
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    SearchTab.User -> {
                        val result = repo.searchUsers(keyword, page)
                        if (state.tab != tab) return@launch
                        state = state.copy(
                            users = if (page <= 1) result.items else state.users + result.items,
                            page = page,
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }
                }
            } catch (e: Exception) {
                if (state.tab == tab) {
                    state = state.copy(error = e.message ?: "加载失败")
                }
            } finally {
                if (state.tab == tab) {
                    state = state.copy(loading = false, loadingMore = false)
                }
            }
        }
    }
}
