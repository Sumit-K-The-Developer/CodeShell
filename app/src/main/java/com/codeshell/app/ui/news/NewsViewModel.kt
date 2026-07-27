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
    val isLoading: Boolean = false,
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

    fun fetchNews(categoryId: String, region: NewsRegion) {
        val gen = fetchGeneration.incrementAndGet()
        viewModelScope.launch {
            val categoryChanged =
                _uiState.value.currentCategory != categoryId ||
                    _uiState.value.currentRegion != region
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentCategory = categoryId,
                    currentRegion = region,
                    articles = if (categoryChanged) emptyList() else it.articles,
                )
            }
            val result = repository.fetchNews(categoryId, region, skipCache = false)
            result.fold(
                onSuccess = { articles ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            articles = articles,
                            error = if (articles.isEmpty()) "No articles found for this category." else null,
                        )
                    }
                    // After RSS returns basic data, enrichment runs in background.
                    // Re-read cache later to pick up enriched descriptions.
                    if (gen == fetchGeneration.get()) {
                        scheduleEnrichmentRefresh(categoryId, region, gen)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            articles = emptyList(),
                            error = error.message ?: "Failed to load news.",
                        )
                    }
                },
            )
        }
    }

    /**
     * After RSS data appears, the repository enriches descriptions/images
     * in the background and saves to cache.  We poll the cache once
     * after a delay so the card text updates without user interaction.
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
            // Only update if the enriched list is different (has OG descriptions)
            _uiState.update { it.copy(articles = cached) }
        }
    }

    fun refresh() {
        val category = _uiState.value.currentCategory
        val region = _uiState.value.currentRegion
        val gen = fetchGeneration.incrementAndGet()
        viewModelScope.launch {
            runCatching { NewsCache.instance.clear(category, region) }
            _uiState.update {
                it.copy(isLoading = true, error = null, articles = emptyList())
            }
            val result = repository.fetchNews(category, region, skipCache = true)
            result.fold(
                onSuccess = { articles ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
                            articles = emptyList(),
                            error = error.message ?: "Failed to load news.",
                        )
                    }
                },
            )
        }
    }

    companion object {
        /** How long to wait before re-reading the cache for enriched descriptions. */
        private const val ENRICHMENT_POLL_DELAY_MS = 12_000L
    }
}
