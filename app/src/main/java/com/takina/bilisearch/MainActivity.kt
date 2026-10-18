package com.takina.bilisearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.takina.bilisearch.data.AppGraph
import com.takina.bilisearch.data.local.Prefs
import com.takina.bilisearch.ui.navigation.BiliNavHost
import com.takina.bilisearch.ui.theme.BiliSearchTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by AppGraph.prefs.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                Prefs.THEME_LIGHT -> false
                Prefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            SideEffect {
                val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            BiliSearchTheme(darkTheme = darkTheme) {
                BiliNavHost()
            }
        }
    }
}
