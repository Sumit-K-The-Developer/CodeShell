package com.codeshell.app.ui.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeshell.app.disguise.ShellFlavorConfigProvider
import com.codeshell.app.ui.disguise.snippets.LearnTopic
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.EditorMutedText
import com.codeshell.app.ui.theme.EditorOnAccent
import com.codeshell.app.ui.theme.FabAccent
import com.codeshell.app.ui.theme.FabAccentSelected
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.util.tapOrDoubleTap

private val ChipShape = RoundedCornerShape(8.dp)

@Composable
fun TopicTopBar(
    topics: List<LearnTopic>,
    selectedTopicId: String,
    onTopicSelected: (LearnTopic) -> Unit,
    onSecretEntry: () -> Unit,
    secretChipDoubleTapEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (ShellFlavorConfigProvider.useTabChipStyle) {
        GrammarTabTopBar(
            topics = topics,
            selectedTopicId = selectedTopicId,
            onTopicSelected = onTopicSelected,
            onSecretEntry = onSecretEntry,
            secretChipDoubleTapEnabled = secretChipDoubleTapEnabled,
            modifier = modifier,
        )
    } else {
        FilterChipTopBar(
            topics = topics,
            selectedTopicId = selectedTopicId,
            onTopicSelected = onTopicSelected,
            onSecretEntry = onSecretEntry,
            secretChipDoubleTapEnabled = secretChipDoubleTapEnabled,
            modifier = modifier,
        )
    }
}

@Composable
private fun FilterChipTopBar(
    topics: List<LearnTopic>,
    selectedTopicId: String,
    onTopicSelected: (LearnTopic) -> Unit,
    onSecretEntry: () -> Unit,
    secretChipDoubleTapEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = topics.indexOfFirst { it.id == selectedTopicId }.coerceAtLeast(0)

    LaunchedEffect(selectedTopicId, topics) {
        if (topics.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
            val isLast = index == topics.lastIndex
            val selected = topic.id == selectedTopicId
            if (isLast) {
                CodingTopicChip(
                    label = chipLabel(topic),
                    selected = selected,
                    modifier = Modifier.tapOrDoubleTap(
                        onTap = { onTopicSelected(topic) },
                        onDoubleTap = onSecretEntry,
                        doubleTapEnabled = secretChipDoubleTapEnabled,
                    ),
                )
            } else {
                FilterChip(
                    selected = selected,
                    onClick = { onTopicSelected(topic) },
                    label = { Text(chipLabel(topic)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FabAccentSelected,
                        selectedLabelColor = EditorOnAccent,
                        containerColor = TopBarBackground,
                        labelColor = EditorDefaultText,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CodingTopicChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) FabAccentSelected else TopBarBackground
    val labelColor = if (selected) EditorOnAccent else EditorDefaultText
    val borderColor = if (selected) FabAccentSelected else EditorDefaultText.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = ChipShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
        )
    }
}

@Composable
private fun GrammarTabTopBar(
    topics: List<LearnTopic>,
    selectedTopicId: String,
    onTopicSelected: (LearnTopic) -> Unit,
    onSecretEntry: () -> Unit,
    secretChipDoubleTapEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = topics.indexOfFirst { it.id == selectedTopicId }.coerceAtLeast(0)

    LaunchedEffect(selectedTopicId, topics) {
        if (topics.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
            val isLast = index == topics.lastIndex
            val selected = topic.id == selectedTopicId
            val chipModifier = if (isLast) {
                Modifier.tapOrDoubleTap(
                    onTap = { onTopicSelected(topic) },
                    onDoubleTap = onSecretEntry,
                    doubleTapEnabled = secretChipDoubleTapEnabled,
                )
            } else {
                Modifier.clickable { onTopicSelected(topic) }
            }
            GrammarTabChip(
                label = chipLabel(topic),
                selected = selected,
                modifier = chipModifier,
            )
        }
    }
}

@Composable
private fun GrammarTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) FabAccent else EditorMutedText,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 2.dp else 0.dp)
                .background(if (selected) FabAccent else FabAccent.copy(alpha = 0f)),
        )
    }
}

private fun chipLabel(topic: LearnTopic): String =
    if (ShellFlavorConfigProvider.useFullTopicLabels) topic.label else topic.shortLabel
