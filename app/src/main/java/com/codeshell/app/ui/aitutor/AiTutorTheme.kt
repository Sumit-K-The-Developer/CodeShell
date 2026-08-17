package com.codeshell.app.ui.aitutor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.FabAccent
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.theme.DisguiseTheme

object AiTutorColors {
    val Background = EditorBackground
    val Surface = TopBarBackground
    val TopBar = TopBarBackground
    val TextPrimary = EditorDefaultText
    val TextSecondary = if (DisguiseTheme.isLight) Color(0xFF5B6573) else Color(0xFFAAB4BE)
    val UserBubble = if (DisguiseTheme.isLight) Color(0xFFDCEAF7) else Color(0xFF263F5A)
    val AssistantBubble = if (DisguiseTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF252B33)
    val WelcomeBackground = if (DisguiseTheme.isLight) Color(0xFFF0EDE8) else Color(0xFF263238)
    val WelcomeBorder = if (DisguiseTheme.isLight) Color(0xFFC7CDD5) else Color(0xFF3A4452)
    val Accent = FabAccent
    val AccentSoft = Color(0xFF6AA7E8)
    val Divider = Color(0xFF3A4452)
    val InputBackground = if (DisguiseTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF2D3748)
    val ErrorBackground = if (DisguiseTheme.isLight) Color(0xFFFFE8E6) else Color(0xFF4A2528)
    val ErrorText = if (DisguiseTheme.isLight) Color(0xFF9B1C1C) else Color(0xFFFFB4AB)
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
