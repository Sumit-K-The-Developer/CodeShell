package com.codeshell.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatDateSeparator(
    label: String,
    theme: ChatTheme,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.colors.mutedText,
            modifier = Modifier
                .background(
                    color = theme.colors.topBar.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
