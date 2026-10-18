package com.takina.bilisearch.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.takina.bilisearch.data.AppGraph

/**
 * 设置页状态：登录态与退出登录。
 * （外观切换已统一放到搜索界面的按钮上，这里不再提供）
 */
class SettingsViewModel : ViewModel() {

    var isLoggedIn by mutableStateOf(AppGraph.repo.isLoggedIn())
        private set

    fun logout() {
        AppGraph.repo.logout()
        isLoggedIn = false
    }
}
