package com.codeshell.app.ui.disguise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codeshell.app.R
import com.codeshell.app.disguise.ShellFlavorConfigProvider
import com.codeshell.app.ui.disguise.snippets.LearnLesson
import com.codeshell.app.ui.disguise.snippets.LearnResourceLink
import com.codeshell.app.ui.disguise.snippets.LearnSection
import com.codeshell.app.ui.theme.CodeTypography
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.EditorGutter
import com.codeshell.app.ui.theme.EditorMutedText
import com.codeshell.app.ui.theme.FabAccent
import com.codeshell.app.util.SyntaxHighlighter

@Composable
fun LearningContentView(
    lesson: LearnLesson,
    topicId: String,
    modifier: Modifier = Modifier,
    onOpenLink: (LearnResourceLink) -> Unit = {},
) {
    val highlightCode = ShellFlavorConfigProvider.highlightCodeExamples
    val isGrammar = !highlightCode
    val highlightedExamples = remember(lesson, topicId, highlightCode) {
        lesson.sections.map { section ->
            val raw = section.examples.trimEnd()
            when {
                highlightCode -> SyntaxHighlighter.highlight(raw, topicId)
                else -> GrammarExampleHighlighter.highlight(raw)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(
            items = lesson.sections,
            key = { index, section -> "${topicId}_${section.title}_$index" },
        ) { index, section ->
            LessonSectionRow(
                section = section,
                exampleText = highlightedExamples[index],
                highlightCode = highlightCode,
                isGrammar = isGrammar,
            )
        }

        if (lesson.resources.isNotEmpty()) {
            item(key = "${topicId}_resources_divider") {
                HorizontalDivider(
                    color = EditorDefaultText.copy(alpha = 0.12f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            item(key = "${topicId}_resources_title") {
                Text(
                    text = stringResource(R.string.extended_learning),
                    style = if (isGrammar) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = if (isGrammar) FontWeight.Bold else FontWeight.Normal,
                    color = EditorDefaultText,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "${topicId}_resources_hint") {
                Text(
                    text = stringResource(R.string.extended_learning_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorMutedText,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(
                items = lesson.resources,
                key = { link -> "${topicId}_link_${link.url}" },
            ) { link ->
                ExtendedLearningLink(
                    link = link,
                    onClick = {
                        if (link.url.startsWith("http")) {
                            onOpenLink(link)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LessonSectionRow(
    section: LearnSection,
    exampleText: AnnotatedString,
    highlightCode: Boolean,
    isGrammar: Boolean,
) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = section.title,
                style = if (isGrammar) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = if (isGrammar) FontWeight.Bold else FontWeight.Normal,
                color = EditorDefaultText,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (section.body.isNotBlank()) {
                Text(
                    text = section.body,
                    style = if (isGrammar) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = EditorDefaultText.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = EditorGutter,
            ) {
                Text(
                    text = exampleText,
                    style = when {
                        highlightCode -> CodeTypography
                        isGrammar -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorGutter)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtendedLearningLink(
    link: LearnResourceLink,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = link.title,
            style = MaterialTheme.typography.bodyMedium,
            color = FabAccent,
            textDecoration = TextDecoration.Underline,
            softWrap = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
