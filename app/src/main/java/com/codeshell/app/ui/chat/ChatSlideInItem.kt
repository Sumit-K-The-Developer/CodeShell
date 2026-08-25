package com.codeshell.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val SLIDE_DURATION_MS = 280

@Composable
fun ChatSlideInItem(
    itemKey: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember(itemKey) { mutableStateOf(!animate) }
    LaunchedEffect(itemKey, animate) {
        if (animate) {
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (animate) {
            fadeIn(animationSpec = tween(SLIDE_DURATION_MS)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(SLIDE_DURATION_MS)) +
                slideInVertically(animationSpec = tween(SLIDE_DURATION_MS)) { fullHeight ->
                    fullHeight
                }
        } else {
            EnterTransition.None
        },
    ) {
        content()
    }
}

@Composable
fun ChatSlideInVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) +
            slideInVertically(animationSpec = tween(180)) { fullHeight ->
                fullHeight / 2
            },
        exit = fadeOut(animationSpec = tween(120)) +
            slideOutVertically(animationSpec = tween(120)) { fullHeight ->
                fullHeight / 2
            },
    ) {
        content()
    }
}
