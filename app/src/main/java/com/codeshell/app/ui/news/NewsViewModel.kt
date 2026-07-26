package com.codeshell.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.news.NewsArticle
import com.codeshell.app.data.news.NewsRegion
import com.codeshell.app.data.news.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val articles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentCategory: String = "general",
    val currentRegion: NewsRegion = NewsRegion.INDIA,
)

class NewsViewModel(
    private val repository: NewsRepository = NewsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    fun fetchNews(categoryId: String, region: NewsRegion) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentCategory = categoryId,
                    currentRegion = region,
                )
            }
            val result = repository.fetchNews(categoryId, region)
            result.fold(
                onSuccess = { articles ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            articles = articles,
                            error = if (articles.isEmpty()) "No articles found for this category." else null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load news.",
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        fetchNews(_uiState.value.currentCategory, _uiState.value.currentRegion)
    }
}
