package com.codeshell.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.codeshell.app.BuildConfig

/** Colors for the disguise/learning experience, which has its own visual theme. */
object LearningColors {
    val isGrammar = BuildConfig.SHELL_FLAVOR == "grammar"

    val EditorBackground = if (isGrammar) Color(0xFFFAF8F5) else Color(0xFF1B1F23)
    val EditorGutter = if (isGrammar) Color(0xFFF0EDE8) else Color(0xFF2D3748)
    val EditorMutedText = if (isGrammar) Color(0xFF5B6573) else Color(0xFFAAB4BE)
    val EditorDefaultText = if (isGrammar) Color(0xFF1A2B4A) else Color(0xFFE0E0E0)
    val EditorOnAccent = Color.White
    val EditorKeyword = if (isGrammar) Color(0xFF3D6BAA) else Color(0xFF4A90D9)
    val EditorString = if (isGrammar) Color(0xFF2E7D4F) else Color(0xFFCE9178)
    val FabAccent = if (isGrammar) Color(0xFF3D6BAA) else Color(0xFF4A90D9)
    val FabAccentSelected = if (isGrammar) Color(0xFF2E5488) else Color(0xFF3B7BC8)
    val TopBarBackground = if (isGrammar) Color.White else Color(0xFF263238)
}
