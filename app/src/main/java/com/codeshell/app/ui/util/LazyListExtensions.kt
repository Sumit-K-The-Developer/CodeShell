package com.codeshell.app.ui.util

import androidx.compose.foundation.lazy.LazyListState

/** For a normal (top-down) [LazyColumn]. */
fun LazyListState.isAtBottom(threshold: Int = 2): Boolean {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return true
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    return lastVisible >= lastIndex - threshold
}

/** For [LazyColumn] with reverseLayout = true (index 0 anchors at the bottom). */
fun LazyListState.isPinnedToBottomReversed(threshold: Int = 2): Boolean {
    if (layoutInfo.totalItemsCount <= 0) return true
    return firstVisibleItemIndex <= threshold
}
