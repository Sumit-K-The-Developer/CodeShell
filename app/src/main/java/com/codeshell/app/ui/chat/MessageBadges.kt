package com.codeshell.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.util.MessageExpiryUtil

@Composable
fun MessageBadges(
    message: ChatMessage,
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    val badges = buildList {
        if (!message.deleted && MessageExpiryUtil.isExpired(message.expiresAt)) {
            add(R.string.badge_expired)
        }
        if (!message.deleted && MessageExpiryUtil.isExpiringSoon(message.expiresAt)) {
            add(R.string.badge_expiring_soon)
        }
    }
    if (badges.isEmpty()) return

    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { labelRes ->
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                color = colors.mutedText,
            )
        }
    }
}
