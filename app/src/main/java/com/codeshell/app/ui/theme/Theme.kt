package com.codeshell.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.codeshell.app.data.model.ChatInterfaceStyle

private val DarkColorScheme = darkColorScheme(
    primary = FabAccent,
    onPrimary = EditorOnAccent,
    secondary = EditorKeyword,
    background = EditorBackground,
    surface = EditorBackground,
    onBackground = EditorDefaultText,
    onSurface = EditorDefaultText,
    surfaceVariant = EditorGutter,
    onSurfaceVariant = EditorDefaultText,
)

private val LightColorScheme = lightColorScheme(
    primary = FabAccent,
    onPrimary = EditorOnAccent,
    secondary = EditorKeyword,
    background = EditorBackground,
    surface = EditorBackground,
    onBackground = EditorDefaultText,
    onSurface = EditorDefaultText,
    surfaceVariant = EditorGutter,
    onSurfaceVariant = EditorDefaultText,
)

private val IdeLightColorScheme = lightColorScheme(
    primary = IdeLightAccent,
    onPrimary = Color(0xFFFFFFFF),
    secondary = EditorKeyword,
    background = IdeLightBackground,
    surface = IdeLightTopBar,
    onBackground = IdeLightText,
    onSurface = IdeLightText,
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = IdeLightMutedText,
)

@Composable
fun CodeShellTheme(
    chatInterfaceStyle: ChatInterfaceStyle = ChatInterfaceStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val ideLight = chatInterfaceStyle == ChatInterfaceStyle.IDE_LIGHT && DisguiseTheme.supportsIdeLight
    val colorScheme = when {
        ideLight -> IdeLightColorScheme
        DisguiseTheme.isLight -> LightColorScheme
        else -> DarkColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (ideLight) IdeLightTopBar else TopBarBackground).toArgb()
            window.navigationBarColor = (if (ideLight) IdeLightBackground else EditorBackground).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = ideLight || DisguiseTheme.isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
