package com.codeshell.app.ui.disguise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.R
import com.codeshell.app.messaging.PendingNewsArticle
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.news.NewsArticle
import com.codeshell.app.data.news.NewsLanguageFilter
import com.codeshell.app.data.news.NewsRegion
import com.codeshell.app.disguise.ShellFlavorConfigProvider
import com.codeshell.app.ui.components.CodeShellTopBar
import com.codeshell.app.ui.disguise.snippets.LearnManifest
import com.codeshell.app.ui.news.NewsScreen
import com.codeshell.app.ui.news.NewsViewModel
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisguiseLearnScreen(
    onSecretEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as CodeShellApplication).lessonRepository
    val manifest by repository.manifest.collectAsStateWithLifecycle()
    val loadedManifest = manifest

    if (loadedManifest == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = EditorDefaultText)
        }
        return
    }

    DisguiseLearnContent(
        manifest = loadedManifest,
        onSecretEntry = onSecretEntry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DisguiseLearnContent(
    manifest: LearnManifest,
    onSecretEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val secretChipDoubleTapEnabled by appPreferences.secretChipDoubleTapEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    val topics = manifest.topics
    val defaultTopicId = topics.firstOrNull()?.id.orEmpty()
    var selectedTopicId by rememberSaveable { mutableStateOf(defaultTopicId) }
    var openedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTitle by rememberSaveable { mutableStateOf<String?>(null) }

    val initialPage = topics.indexOfFirst { it.id == selectedTopicId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { topics.size }
    var lastSyncedPage by rememberSaveable { mutableIntStateOf(initialPage) }

    // News flavor specific state
    val isNewsFlavor = ShellFlavorConfigProvider.isNewsFlavor
    var isInternational by rememberSaveable { mutableStateOf(false) }
    var pendingArticleUrl by remember { mutableStateOf<String?>(null) }
    // Hoisted here so it persists across category (HorizontalPager) changes
    var languageFilter by rememberSaveable { mutableStateOf(NewsLanguageFilter.ENGLISH) }
    val newsViewModel: NewsViewModel = viewModel()

    // Re-fire on every set() call so a notification tap on an already-open screen
    // also navigates to the article (not just on the first composition).
    val pendingNewsVersion by PendingNewsArticle.version.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNewsVersion) {
        val pending = PendingNewsArticle.consume()
        if (pending != null) {
            selectedTopicId = pending.categoryId
            isInternational = pending.regionCountryCode != "in"
            pendingArticleUrl = pending.url
        }
    }

    // Fetch news when category or region changes
    LaunchedEffect(selectedTopicId, isInternational, isNewsFlavor) {
        if (isNewsFlavor) {
            val region = if (isInternational) NewsRegion.INTERNATIONAL else NewsRegion.INDIA
            newsViewModel.fetchNews(selectedTopicId, region)
        }
    }

    LaunchedEffect(selectedTopicId, topics) {
        val targetPage = topics.indexOfFirst { it.id == selectedTopicId }.coerceAtLeast(0)
        if (targetPage != pagerState.currentPage && targetPage != lastSyncedPage) {
            pagerState.scrollToPage(targetPage)
            lastSyncedPage = targetPage
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val topic = topics.getOrNull(page) ?: return@collect
                if (topic.id != selectedTopicId) {
                    selectedTopicId = topic.id
                    pendingArticleUrl = null
                }
                lastSyncedPage = page
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = EditorBackground,
            topBar = {
                Column {
                    CodeShellTopBar(
                        secretChipDoubleTapEnabled = secretChipDoubleTapEnabled,
                        onToggleSecretChipDoubleTap = {
                            scope.launch {
                                appPreferences.recordSecretChipToggle(!secretChipDoubleTapEnabled)
                            }
                        },
                        regionToggleChecked = if (isNewsFlavor) isInternational else null,
                        onRegionToggleChange = if (isNewsFlavor) { checked -> isInternational = checked } else null,
                        onNewsRefresh = if (isNewsFlavor) ({ newsViewModel.refresh() }) else null,
                    )
                    HorizontalDivider(color = EditorDefaultText.copy(alpha = 0.1f))
                    TopicTopBar(
                        topics = topics,
                        selectedTopicId = selectedTopicId,
                        onTopicSelected = { topic ->
                            selectedTopicId = topic.id
                            pendingArticleUrl = null
                        },
                        onSecretEntry = onSecretEntry,
                        secretChipDoubleTapEnabled = secretChipDoubleTapEnabled,
                    )
                }
            },
        ) { innerPadding ->
            if (isNewsFlavor) {
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 0,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    NewsScreen(
                        viewModel = newsViewModel,
                        onArticleClick = { article ->
                            val url = article.url
                            if (url != null) {
                                openedUrl = url
                                openedTitle = article.title
                            }
                        },
                        initialArticleUrl = pendingArticleUrl,
                        onInitialArticleConsumed = { pendingArticleUrl = null },
                        showLanguageFilter = !isInternational,
                        languageFilter = languageFilter,
                        onLanguageFilterChange = { languageFilter = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 0,
                        modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) { page ->
                    val topic = topics[page]
                    val lesson = manifest.lessonFor(topic.id)
                    if (lesson != null) {
                        LearningContentView(
                            lesson = lesson,
                            topicId = topic.id,
                            onOpenLink = { link ->
                                openedUrl = link.url
                                openedTitle = link.title
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.lesson_unavailable),
                                color = EditorDefaultText.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        val url = openedUrl
        if (url != null) {
            LearningWebView(
                url = url,
                title = openedTitle.orEmpty(),
                onClose = {
                    openedUrl = null
                    openedTitle = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
