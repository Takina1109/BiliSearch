package com.takina.bilisearch.ui.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takina.bilisearch.data.AppGraph
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val loading: Boolean = true,
    val name: String = "",
    val sign: String = "",
    /** 0 保密 / 1 男 / 2 女 */
    val sex: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

class EditProfileViewModel : ViewModel() {

    var state by mutableStateOf(EditProfileUiState())
        private set

    private val repo = AppGraph.repo

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                val info = repo.myInfo()
                state = EditProfileUiState(
                    loading = false,
                    name = info.name.orEmpty(),
                    sign = info.sign.orEmpty(),
                    sex = when (info.sex) {
                        "男" -> 1
                        "女" -> 2
                        else -> 0
                    }
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun onNameChange(value: String) {
        state = state.copy(name = value, error = null)
    }

    fun onSignChange(value: String) {
        state = state.copy(sign = value, error = null)
    }

    fun onSexChange(value: Int) {
        state = state.copy(sex = value)
    }

    fun save() {
        if (state.saving) return
        val name = state.name.trim()
        if (name.isEmpty()) {
            state = state.copy(error = "昵称不能为空")
            return
        }
        viewModelScope.launch {
            state = state.copy(saving = true, error = null)
            try {
                repo.updateProfile(name, state.sex, state.sign.trim())
                state = state.copy(saving = false, saved = true)
            } catch (e: Exception) {
                state = state.copy(saving = false, error = e.message ?: "保存失败")
            }
        }
    }
}
