package com.codeshell.app.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun ChatTypingIndicator(
    theme: ChatTheme,
    modifier: Modifier = Modifier,
) {
    val colors = theme.colors
    val shape = if (theme.useAsymmetricBubbleTail) {
        RoundedCornerShape(
            topStart = theme.bubbleCornerLarge,
            topEnd = theme.bubbleCornerLarge,
            bottomStart = theme.bubbleCornerSmall,
            bottomEnd = theme.bubbleCornerLarge,
        )
    } else {
        RoundedCornerShape(theme.bubbleCornerLarge)
    }
    val transition = rememberInfiniteTransition(label = "typing")
    val dotAlphas = List(3) { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = index * 150, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "typingDot$index",
        )
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .widthIn(max = theme.bubbleMaxWidth)
                .background(colors.bubbleOther, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dotAlphas.forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(alpha.value)
                        .background(colors.mutedText, CircleShape),
                )
            }
        }
    }
}
