package com.codeshell.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val REMOVE_DURATION_MS = 220

@Composable
fun ChatMessageRemoveVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(REMOVE_DURATION_MS)) +
            shrinkVertically(animationSpec = tween(REMOVE_DURATION_MS)),
    ) {
        content()
    }
}
