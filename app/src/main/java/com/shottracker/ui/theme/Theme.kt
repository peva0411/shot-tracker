package com.shottracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = White,
    primaryContainer = OrangeDark,
    onPrimaryContainer = OrangeLight,
    secondary = GrayMedium,
    onSecondary = White,
    background = GrayDark,
    onBackground = White,
    surface = Black,
    onSurface = White,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = White,
    primaryContainer = OrangeLight,
    onPrimaryContainer = OrangeDark,
    secondary = GrayMedium,
    onSecondary = White,
    background = White,
    onBackground = GrayDark,
    surface = White,
    onSurface = Black,
    error = ErrorRed
)

@Composable
fun ShotTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
