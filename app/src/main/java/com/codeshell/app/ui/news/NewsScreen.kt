package com.codeshell.app.ui.news

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codeshell.app.data.news.NewsArticle
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.FabAccent
import java.text.SimpleDateFormat
import java.util.Locale

private val NewsCardBackground = Color(0xFF252B33)
private val NewsCardBorder = Color(0xFF3A4452)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel,
    onArticleClick: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.articles.isEmpty() && !uiState.isLoading) {
            viewModel.fetchNews(uiState.currentCategory, uiState.currentRegion)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.articles.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = EditorDefaultText,
                        strokeWidth = 2.dp,
                    )
                }
            }
            uiState.articles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "No articles found. Try switching categories.",
                        color = EditorDefaultText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                androidx.compose.foundation.pager.VerticalPager(
                    state = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = 0,
                    ) { uiState.articles.size },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    pageSpacing = 20.dp,
                    key = { page -> uiState.articles.getOrNull(page)?.url ?: "page_$page" },
                ) { page ->
                    val article = uiState.articles.getOrNull(page)
                    if (article != null) {
                        NewsArticleCard(
                            article = article,
                            onFullArticleClick = { onArticleClick(article) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (uiState.isLoading && uiState.articles.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CircularProgressIndicator(
                    color = EditorDefaultText,
                    modifier = Modifier
                        .background(
                            EditorDefaultText.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    onFullArticleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun stripTruncation(text: String): String =
        text.replace(Regex("""\s*\[\d+\s*chars?\]"""), "")

    val description = article.description?.let { stripTruncation(it) }
    val content = article.content?.let { stripTruncation(it) }
    val combinedText = buildString {
        if (!description.isNullOrBlank()) {
            append(description)
        }
        if (!content.isNullOrBlank() && content != description) {
            if (isNotBlank()) appendLine()
            appendLine()
            append(content)
        }
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NewsCardBackground,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = NewsCardBorder,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NewsCardBackground,
                            EditorBackground,
                        ),
                    ),
                ),
        ) {
            val imageUrl = article.urlToImage
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = ColorPainter(Color.Gray),
                    error = ColorPainter(Color.Gray),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                val sourceName = article.source?.name ?: "Unknown"
                val publishedAt = article.publishedAt
                val dateStr = publishedAt?.let { formatDate(it) } ?: ""
                val feedTag = article.feedTag

                Text(
                    text = buildString {
                        append(sourceName)
                        if (dateStr.isNotBlank()) {
                            append(" • ")
                            append(dateStr)
                        }
                        if (!feedTag.isNullOrBlank()) {
                            append(" • ")
                            append(feedTag)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FabAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                SelectionContainer {
                    Text(
                        text = article.title ?: "Untitled",
                        fontSize = 24.sp,
                        lineHeight = 36.sp,
                        color = EditorDefaultText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (combinedText.isNotBlank()) {
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = combinedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = EditorDefaultText.copy(alpha = 0.85f),
                            maxLines = 40,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    text = "Tap to read full article",
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorDefaultText.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { onFullArticleClick() }
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatDate(publishedAt: String): String {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
    )
    val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    for (pattern in patterns) {
        try {
            val date = SimpleDateFormat(pattern, Locale.US).parse(publishedAt) ?: continue
            return outputFormat.format(date)
        } catch (_: Exception) {
            // try next
        }
    }
    return publishedAt.take(10)
}
