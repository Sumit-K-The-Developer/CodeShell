package com.codeshell.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.news.NewsArticle
import com.codeshell.app.data.news.NewsCache
import com.codeshell.app.data.news.NewsRegion
import com.codeshell.app.data.news.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

data class NewsUiState(
    val articles: List<NewsArticle> = emptyList(),
    /** True only for the initial full-screen spinner (no articles to show). */
    val isLoading: Boolean = false,
    /** True whenever a background or manual refresh is running (green progress line). */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentCategory: String = "top",
    val currentRegion: NewsRegion = NewsRegion.INDIA,
)

class NewsViewModel(
    private val repository: NewsRepository = NewsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val fetchGeneration = AtomicInteger(0)

    /** Tracks (categoryId, region) pairs we've already fetched at least once. */
    private val fetchedKeys = mutableSetOf<String>()

    private fun key(categoryId: String, region: NewsRegion): String =
        "${categoryId}_${region.countryCode}"

    fun fetchNews(categoryId: String, region: NewsRegion) {
        val gen = fetchGeneration.incrementAndGet()
        val k = key(categoryId, region)
        val alreadyFetched = fetchedKeys.contains(k)
        viewModelScope.launch {
            val categoryChanged =
                _uiState.value.currentCategory != categoryId ||
                    _uiState.value.currentRegion != region
            _uiState.update {
                it.copy(
                    isLoading = categoryChanged && it.articles.isEmpty(),
                    isRefreshing = false,
                    error = null,
                    currentCategory = categoryId,
                    currentRegion = region,
                    articles = if (categoryChanged) emptyList() else it.articles,
                )
            }
            val result = repository.fetchNews(categoryId, region, skipCache = false)
            result.fold(
                onSuccess = { cached ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = cached,
                            error = if (cached.isEmpty()) "No articles found for this category." else null,
                        )
                    }
                    // Launch background fetch only the FIRST time visiting this category.
                    // On subsequent visits, just return cached data silently.
                    if (cached.isNotEmpty() && !alreadyFetched && gen == fetchGeneration.get()) {
                        backgroundRefresh(categoryId, region, gen)
                    } else if (gen == fetchGeneration.get()) {
                        scheduleEnrichmentRefresh(categoryId, region, gen)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = emptyList(),
                            error = error.message ?: "Failed to load news.",
                        )
                    }
                },
            )
        }
        fetchedKeys.add(k)
    }

    private fun backgroundRefresh(categoryId: String, region: NewsRegion, gen: Int) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.fetchNews(categoryId, region, skipCache = true)
            if (gen != fetchGeneration.get()) return@launch
            result.fold(
                onSuccess = { articles ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            articles = articles,
                            error = if (articles.isEmpty()) "No articles found for this category." else null,
                        )
                    }
                    if (gen == fetchGeneration.get()) {
                        scheduleEnrichmentRefresh(categoryId, region, gen)
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                },
            )
        }
    }

    /**
     * Poll the cache after enrichment silently (no green line).
     * The green progress should disappear as soon as articles are refreshed.
     */
    private fun scheduleEnrichmentRefresh(
        categoryId: String,
        region: NewsRegion,
        gen: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(ENRICHMENT_POLL_DELAY_MS)
            if (gen != fetchGeneration.get()) return@launch
            val cached = runCatching { NewsCache.instance.loadAll(categoryId, region) }
                .getOrNull()
            if (cached.isNullOrEmpty()) return@launch
            if (gen != fetchGeneration.get()) return@launch
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    articles = cached,
                )
            }
        }
    }

    fun refresh() {
        val category = _uiState.value.currentCategory
        val region = _uiState.value.currentRegion
        val gen = fetchGeneration.incrementAndGet()
        viewModelScope.launch {
            runCatching { NewsCache.instance.clear(category, region) }
            _uiState.update {
                it.copy(
                    isLoading = it.articles.isEmpty(),
                    isRefreshing = it.articles.isNotEmpty(),
                    error = null,
                )
            }
            val result = repository.fetchNews(category, region, skipCache = true)
            result.fold(
                onSuccess = { articles ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            articles = articles,
                            error = if (articles.isEmpty()) "No articles found for this category." else null,
                        )
                    }
                    if (gen == fetchGeneration.get()) {
                        scheduleEnrichmentRefresh(category, region, gen)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Failed to load news.",
                        )
                    }
                },
            )
        }
    }

    fun cancelRefresh() {
        fetchGeneration.incrementAndGet()
        _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
    }

    companion object {
        private const val ENRICHMENT_POLL_DELAY_MS = 12_000L
    }
}