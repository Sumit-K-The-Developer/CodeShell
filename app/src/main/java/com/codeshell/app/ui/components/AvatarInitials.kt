package com.codeshell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.EditorKeyword
import com.codeshell.app.util.UsernameFormatter

@Composable
fun AvatarInitials(
    username: String,
    modifier: Modifier = Modifier
) {
    val digits = UsernameFormatter.parseRawInput(username)
    val initials = when {
        digits.length >= 2 -> digits.takeLast(2)
        digits.isNotEmpty() -> digits
        else -> "?"
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(EditorKeyword),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = EditorDefaultText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
