package com.codeshell.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codeshell.app.R
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.SessionLogStatus
import com.codeshell.app.data.model.formatSessionDuration
import com.codeshell.app.data.model.sessionCallType
import com.codeshell.app.data.model.sessionDurationSec
import com.codeshell.app.data.model.sessionLogStatus

@Composable
fun SessionLogCard(
    message: ChatMessage,
    isMine: Boolean,
    theme: ChatTheme,
    modifier: Modifier = Modifier,
) {
    val colors = theme.colors
    val status = message.sessionLogStatus() ?: return
    val isVideo = message.sessionCallType() == CallType.VIDEO
    val title = stringResource(
        if (isVideo) R.string.live_connect else R.string.voice_connect,
    )
    val detail = when (status) {
        SessionLogStatus.COMPLETED -> {
            message.sessionDurationSec()?.let { formatSessionDuration(it) }
                ?: stringResource(R.string.session_log_completed_short)
        }
        SessionLogStatus.MISSED -> stringResource(R.string.session_log_missed)
        SessionLogStatus.DECLINED -> stringResource(R.string.session_log_declined)
        SessionLogStatus.FAILED -> stringResource(R.string.session_log_failed)
    }
    val labelStyle = if (theme.useMonospaceText) {
        MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.labelMedium
    }
    val bubbleColor = if (isMine) colors.bubbleMine else colors.bubbleOther.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = labelStyle,
                color = colors.text,
            )
            Text(
                text = "·",
                style = labelStyle,
                color = colors.mutedText,
            )
            Text(
                text = detail,
                style = labelStyle,
                color = colors.mutedText,
            )
        }
    }
}
