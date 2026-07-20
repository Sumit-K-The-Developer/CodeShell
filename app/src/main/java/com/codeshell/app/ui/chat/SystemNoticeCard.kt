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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.SystemNoticeType
import com.codeshell.app.data.model.systemActorLabel
import com.codeshell.app.data.model.systemNoticeType

@Composable
fun SystemNoticeCard(
    message: ChatMessage,
    theme: ChatTheme,
    modifier: Modifier = Modifier,
) {
    val colors = theme.colors
    val noticeType = message.systemNoticeType() ?: return
    val actor = message.systemActorLabel().ifBlank { stringResource(R.string.chat_actor_fallback) }
    val text = when (noticeType) {
        SystemNoticeType.AUTO_CLEAR_ENABLED ->
            stringResource(R.string.auto_clear_enabled_notice, actor)
        SystemNoticeType.AUTO_CLEAR_DISABLED ->
            stringResource(R.string.auto_clear_disabled_notice, actor)
    }
    val style = if (theme.useMonospaceText) {
        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodySmall
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = colors.mutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(colors.bubbleOther.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
