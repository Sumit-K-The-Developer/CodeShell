package com.codeshell.app.ui.news

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codeshell.app.data.news.NewsArticle
import com.codeshell.app.data.news.NewsLanguageFilter
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.FabAccent
import java.text.SimpleDateFormat
import java.util.Locale

private val NewsCardBackground = Color(0xFF252B33)
private val NewsCardBorder = Color(0xFF3A4452)
private val RefreshGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel,
    onArticleClick: (NewsArticle) -> Unit,
    initialArticleUrl: String? = null,
    onInitialArticleConsumed: () -> Unit = {},
    showLanguageFilter: Boolean = false,
    languageFilter: NewsLanguageFilter = NewsLanguageFilter.ENGLISH,
    onLanguageFilterChange: (NewsLanguageFilter) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.articles.isEmpty() && !uiState.isLoading && !uiState.isRefreshing) {
            viewModel.fetchNews(uiState.currentCategory, uiState.currentRegion)
        }
    }

    val rawFiltered = when (languageFilter) {
        NewsLanguageFilter.ENGLISH -> uiState.articles.filter { it.language == "en" }
        NewsLanguageFilter.HINDI -> uiState.articles.filter { it.language == "hi" }
        NewsLanguageFilter.MIX -> uiState.articles
    }
    // Fall back to MIX if filter yields nothing, so the user never sees a blank page.
    val filteredArticles = if (rawFiltered.isNotEmpty()) rawFiltered else uiState.articles

    // Infinite-loop pager: moderate virtual count that wraps modulo article count.
    val actualCount = filteredArticles.size
    val virtualCount = if (actualCount > 0) actualCount * 10_000 else 0
    val midVirtual = if (actualCount > 0) virtualCount / 2 else 0

    val pagerState = rememberPagerState(initialPage = midVirtual) { virtualCount }

    // Reset pager to midpoint when the filtered article set changes (e.g. language switch).
    LaunchedEffect(filteredArticles) {
        if (virtualCount > 0) {
            pagerState.scrollToPage(midVirtual)
        }
    }

    // Scroll to the deep-link article once it appears in the filtered list.
    // Uses instant scroll to avoid conflicting with the HorizontalPager animation.
    // Never gives up: keeps retrying until the correct category's articles load
    // and the URL is found. Only clears via the callback.
    LaunchedEffect(initialArticleUrl, filteredArticles, uiState.currentCategory) {
        val url = initialArticleUrl ?: return@LaunchedEffect
        if (filteredArticles.isEmpty()) return@LaunchedEffect
        val index = filteredArticles.indexOfFirst { it.url == url }
        if (index >= 0) {
            // Map real index to a nearby virtual page for instant scroll.
            val current = pagerState.currentPage
            val currentMod = ((current % actualCount) + actualCount) % actualCount
            val target = current + (index - currentMod)
            pagerState.scrollToPage(target)
            onInitialArticleConsumed()
        } else {
            val fullIndex = uiState.articles.indexOfFirst { it.url == url }
            if (fullIndex >= 0 && languageFilter != NewsLanguageFilter.MIX) {
                onLanguageFilterChange(NewsLanguageFilter.MIX)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thin green animated progress + cancel icon, shown whenever a
            // background or manual refresh is running (and articles are visible).
            AnimatedVisibility(
                visible = uiState.isRefreshing && filteredArticles.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp),
                        color = RefreshGreen,
                        trackColor = Color.Transparent,
                    )
                    IconButton(
                        onClick = { viewModel.cancelRefresh() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel refresh",
                            tint = EditorDefaultText.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            when {
                uiState.isLoading && filteredArticles.isEmpty() -> {
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
                filteredArticles.isEmpty() -> {
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
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        pageSpacing = 20.dp,
                        key = { page ->
                            val real = ((page % actualCount) + actualCount) % actualCount
                            "vpage_${real}_$page"
                        },
                    ) { page ->
                        val real = ((page % actualCount) + actualCount) % actualCount
                        val article = filteredArticles.getOrNull(real)
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
        }

        // Language filter – bottom-left plain bold text (no pill background),
        // cycled English → Hindi → Mix. Aligned with the "Tap to read" label
        // inside the card (16dp pager padding + 16dp card inner padding).
        if (showLanguageFilter && filteredArticles.isNotEmpty()) {
            Text(
                text = languageFilter.label,
                color = EditorDefaultText.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 26.dp)
                    .clickable {
                        onLanguageFilterChange(
                            when (languageFilter) {
                                NewsLanguageFilter.ENGLISH -> NewsLanguageFilter.HINDI
                                NewsLanguageFilter.HINDI -> NewsLanguageFilter.MIX
                                NewsLanguageFilter.MIX -> NewsLanguageFilter.ENGLISH
                            },
                        )
                    },
            )
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