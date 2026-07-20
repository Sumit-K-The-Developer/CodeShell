package com.codeshell.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeshell.app.ui.chat.ChatThemeColors

@Composable
fun TerminalPromptIcon(
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .padding(end = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[>_]",
            color = colors.mutedText,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
