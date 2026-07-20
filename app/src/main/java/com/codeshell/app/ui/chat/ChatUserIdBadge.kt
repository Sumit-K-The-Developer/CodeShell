package com.codeshell.app.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun ChatUserIdBadge(
    digits: String?,
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    if (digits.isNullOrEmpty()) return
    Text(
        text = "#$digits",
        style = MaterialTheme.typography.labelSmall,
        color = colors.text.copy(alpha = 0.8f),
        modifier = modifier.padding(end = 4.dp),
    )
}
