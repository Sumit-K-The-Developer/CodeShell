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

    /**
     * Number of columns for the Home conversation-tile grid, scaling with both
     * screen width and orientation: 2 on portrait phones, 4 on landscape phones,
     * 3 on portrait tablets, scaling up on wide/landscape tablets.
     */
    @Composable
    fun homeGridColumns(): Int {
        val config = LocalConfiguration.current
        val widthDp = config.screenWidthDp
        val heightDp = config.screenHeightDp
        val isLandscape = widthDp > heightDp
        return when {
            widthDp >= 1200 -> 6
            widthDp >= 1000 -> 5
            widthDp >= 840 -> if (isLandscape) 4 else 3
            widthDp >= 600 -> if (isLandscape) 4 else 3
            widthDp >= 540 -> 3
            else -> if (isLandscape) 4 else 2
        }
    }

    fun chatListColumnWidth(screenWidthDp: Int): Dp {
        return when {
            screenWidthDp >= 840 -> 720.dp
            screenWidthDp >= 600 -> 600.dp
            else -> Dp.Unspecified
        }
    }

    /** Max width for single-column forms (login/signup) on wide screens. */
    @Composable
    fun formMaxWidth(): Dp {
        val config = LocalConfiguration.current
        val isLandscape = config.screenWidthDp > config.screenHeightDp
        return when {
            config.screenWidthDp >= 840 -> 480.dp
            config.screenWidthDp >= 600 -> 480.dp
            isLandscape -> 480.dp
            else -> Dp.Unspecified
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

/**
 * Centers a single-column form (login/sign-up) horizontally and constrains it
 * to a comfortable reading width in landscape / on tablets.
 */
@Composable
fun ConstrainedFormColumn(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val maxWidth = WindowLayout.formMaxWidth()
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
