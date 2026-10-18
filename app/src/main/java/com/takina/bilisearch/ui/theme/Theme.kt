package com.takina.bilisearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BiliPink = Color(0xFFFB7299)
val BiliPinkLight = Color(0xFFFF8CAC)
val BiliBlue = Color(0xFF00AEEC)

private val LightColors = lightColorScheme(
    primary = BiliPink,
    onPrimary = Color.White,
    secondary = BiliBlue,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF18191C),
    surface = Color.White,
    onSurface = Color(0xFF18191C),
    surfaceVariant = Color(0xFFEFF0F2),
    onSurfaceVariant = Color(0xFF61666D),
    outline = Color(0xFFE3E5E7)
)

private val DarkColors = darkColorScheme(
    primary = BiliPinkLight,
    onPrimary = Color(0xFF2A1219),
    secondary = BiliBlue,
    onSecondary = Color(0xFF00222F),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE7E8EA),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE7E8EA),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA8ABB0),
    outline = Color(0xFF3A3A3E)
)

@Composable
fun BiliSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
