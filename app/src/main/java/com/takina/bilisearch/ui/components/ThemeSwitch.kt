package com.takina.bilisearch.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.local.Prefs

/** 当前实际生效的是否深色（跟随系统时取决于系统设置） */
@Composable
fun isDarkThemeActive(): Boolean {
    val mode by AppGraph.prefs.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    return when (mode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> systemDark
    }
}

/** 在白天 / 夜间之间切换（会脱离「跟随系统」） */
fun setDarkTheme(dark: Boolean) {
    AppGraph.prefs.setThemeMode(if (dark) Prefs.THEME_DARK else Prefs.THEME_LIGHT)
}

fun setThemeMode(mode: Int) {
    AppGraph.prefs.setThemeMode(mode)
}
