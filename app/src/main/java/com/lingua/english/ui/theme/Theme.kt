package com.lingua.english.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LinguaDarkColors = darkColorScheme(
    primary = LinguaPrimary,
    onPrimary = LinguaBackground,
    secondary = LinguaSecondary,
    onSecondary = LinguaBackground,
    tertiary = LinguaTertiary,
    onTertiary = LinguaBackground,
    background = LinguaBackground,
    onBackground = LinguaOnBackground,
    surface = LinguaSurface,
    onSurface = LinguaOnBackground,
    surfaceVariant = LinguaSurfaceVariant,
    onSurfaceVariant = LinguaOnSurfaceMuted,
    outline = LinguaOutline,
    error = LinguaError,
)

@Composable
fun LinguaTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    fontChoice: AppFontChoice = AppFontChoice.DEFAULT,
    content: @Composable () -> Unit
) {
    // 永远深色 (charlotte 偏好 #06080f), 不跟系统切换
    val colorScheme = LinguaDarkColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = linguaTypography(fontChoice),
        content = content
    )
}
