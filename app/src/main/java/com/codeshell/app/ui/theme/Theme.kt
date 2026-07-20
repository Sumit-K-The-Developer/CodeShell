package com.codeshell.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FabAccent,
    onPrimary = EditorBackground,
    secondary = EditorKeyword,
    background = EditorBackground,
    surface = EditorBackground,
    onBackground = EditorDefaultText,
    onSurface = EditorDefaultText,
)

private val LightColorScheme = lightColorScheme(
    primary = FabAccent,
    onPrimary = EditorBackground,
    secondary = EditorKeyword,
    background = EditorBackground,
    surface = EditorBackground,
    onBackground = EditorDefaultText,
    onSurface = EditorDefaultText,
)

@Composable
fun CodeShellTheme(content: @Composable () -> Unit) {
    val colorScheme = if (DisguiseTheme.isLight) LightColorScheme else DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TopBarBackground.toArgb()
            window.navigationBarColor = EditorBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = DisguiseTheme.isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
