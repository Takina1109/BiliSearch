package com.takina.bilisearch.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 轻量本地存储：Cookie 与主题设置。
 * Cookie 需要在同步时机恢复，因此这里用 SharedPreferences 而不是 DataStore。
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("bilisearch", Context.MODE_PRIVATE)

    /** 0 = 跟随系统，1 = 白天，2 = 夜间 */
    val themeMode = MutableStateFlow(sp.getInt(KEY_THEME_MODE, THEME_SYSTEM))

    fun setThemeMode(mode: Int) {
        themeMode.value = mode
        sp.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun saveCookies(json: String) {
        sp.edit().putString(KEY_COOKIES, json).apply()
    }

    fun cookiesJson(): String? = sp.getString(KEY_COOKIES, null)

    fun clearCookies() {
        sp.edit().remove(KEY_COOKIES).apply()
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COOKIES = "cookies"
    }
}
