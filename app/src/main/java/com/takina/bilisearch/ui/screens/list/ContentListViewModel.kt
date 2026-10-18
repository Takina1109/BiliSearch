package com.takina.bilisearch.ui.screens.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.ui.components.ListRow
import com.takina.bilisearch.ui.navigation.ListKind
import kotlinx.coroutines.launch

data class ListUiState(
    val rows: List<ListRow> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val total: Long = 0
)

class ContentListViewModel(
    private val kind: ListKind,
    private val folderId: Long,
    /** 指定查看某个用户的内容；-1 表示当前登录账号 */
    userMid: Long
) : ViewModel() {

    var state by mutableStateOf(ListUiState())
        private set

    private val repo = AppGraph.repo
    private val targetMid: Long? = userMid.takeIf { it > 0 }
    private var page = 0
    private var cursorMax = 0L
    private var cursorViewAt = 0L

    init {
        load(append = false)
    }

    fun load(append: Boolean = false) {
        if (state.loading || state.loadingMore) return
        if (append && !state.hasMore) return
        if (append && kind == ListKind.WatchLater) return

        val nextPage = if (append) page + 1 else 1
        val existing = state.rows

        state = state.copy(
            loading = !append,
            loadingMore = append,
            error = null
        )

        viewModelScope.launch {
            try {
                when (kind) {
                    ListKind.History -> {
                        val result = repo.history(cursorMax, cursorViewAt)
                        cursorMax = result.cursorMax
                        cursorViewAt = result.cursorViewAt
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Video(it) }
                            else result.items.map { ListRow.Video(it) },
                            hasMore = result.hasMore
                        )
                    }

                    ListKind.Submissions -> {
                        val result = repo.submissions(nextPage, targetMid)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Video(it) }
                            else result.items.map { ListRow.Video(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    ListKind.WatchLater -> {
                        val items = repo.watchLater()
                        state = state.copy(
                            rows = items.map { ListRow.Video(it) },
                            hasMore = false
                        )
                    }

                    ListKind.FavoriteFolders -> {
                        val items = repo.favoriteFolders(targetMid)
                        state = state.copy(
                            rows = items.map { ListRow.Folder(it) },
                            hasMore = false
                        )
                    }

                    ListKind.Favorites -> {
                        val result = repo.favoriteItems(folderId, nextPage)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Video(it) }
                            else result.items.map { ListRow.Video(it) },
                            hasMore = result.hasMore
                        )
                    }

                    ListKind.Collections -> {
                        val result = repo.collections(targetMid, nextPage)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Collection(it) }
                            else result.items.map { ListRow.Collection(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    ListKind.CollectionVideos -> {
                        val result = repo.collectionArchives(targetMid, folderId, nextPage)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Video(it) }
                            else result.items.map { ListRow.Video(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    ListKind.Following -> {
                        val result = repo.following(nextPage, targetMid)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.User(it) }
                            else result.items.map { ListRow.User(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    ListKind.Followers -> {
                        val result = repo.followers(nextPage, targetMid)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.User(it) }
                            else result.items.map { ListRow.User(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }

                    ListKind.Bangumis -> {
                        val result = repo.bangumiFollow(nextPage, targetMid)
                        page = nextPage
                        state = state.copy(
                            rows = if (append) existing + result.items.map { ListRow.Video(it) }
                            else result.items.map { ListRow.Video(it) },
                            hasMore = result.hasMore,
                            total = result.total
                        )
                    }
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "加载失败")
            } finally {
                state = state.copy(loading = false, loadingMore = false)
            }
        }
    }
}

class ContentListViewModelFactory(
    private val kind: ListKind,
    private val folderId: Long,
    private val userMid: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ContentListViewModel(kind, folderId, userMid) as T
    }
}
