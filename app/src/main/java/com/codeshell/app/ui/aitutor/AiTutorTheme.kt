package com.codeshell.app.ui.aitutor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AiTutorColors {
    val Background = Color(0xFFF7F8FA)
    val Surface = Color(0xFFFFFFFF)
    val TopBar = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1A1D21)
    val TextSecondary = Color(0xFF5C6370)
    val UserBubble = Color(0xFFE3F2EC)
    val AssistantBubble = Color(0xFFFFFFFF)
    val WelcomeBackground = Color(0xFFEAF6F2)
    val WelcomeBorder = Color(0xFFB8E0D4)
    val Accent = Color(0xFF0D9488)
    val AccentSoft = Color(0xFF14B8A6)
    val Divider = Color(0xFFE5E7EB)
    val InputBackground = Color(0xFFF3F4F6)
    val ErrorBackground = Color(0xFFFFF5F5)
    val ErrorText = Color(0xFFB42318)
}

object AiTutorTypography {
    val WelcomeTitle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 48.sp,
        letterSpacing = 0.25.sp,
    )

    val WelcomeAccent = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 50.sp,
        letterSpacing = 0.5.sp,
    )

    val WelcomeBody = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 42.sp,
        letterSpacing = 0.25.sp,
    )
}
