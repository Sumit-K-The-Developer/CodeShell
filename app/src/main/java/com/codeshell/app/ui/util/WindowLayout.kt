package com.codeshell.app.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object WindowLayout {

    @Composable
    fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

    @Composable
    fun contentMaxWidth(): Dp {
        val widthDp = LocalConfiguration.current.screenWidthDp
        return when {
            widthDp >= 840 -> 720.dp
            widthDp >= 600 -> 600.dp
            else -> Dp.Unspecified
        }
    }

    fun chatBubbleMaxWidth(base: Dp, screenWidthDp: Int): Dp {
        return when {
            screenWidthDp >= 840 -> maxOf(base, 440.dp)
            screenWidthDp >= 600 -> maxOf(base, 380.dp)
            else -> base
        }
    }
}

@Composable
fun AdaptiveWidthContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val maxWidth = WindowLayout.contentMaxWidth()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = if (maxWidth != Dp.Unspecified) {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            content = content,
        )
    }
}
