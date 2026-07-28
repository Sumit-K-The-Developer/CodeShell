package com.codeshell.app.data.news

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val source: NewsSource?,
    val author: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    @SerialName("urlToImage") val urlToImage: String?,
    @SerialName("publishedAt") val publishedAt: String?,
    val content: String?,
    /** "FEED" for Google News RSS, "Gnews" for GNews.io API. */
    val feedTag: String? = null,
    /** ISO 639-1 code: "en" for English articles, "hi" for Hindi. Null on legacy cache entries. */
    val language: String? = null,
)

enum class NewsLanguageFilter(val label: String) {
    /** Only English-language articles. */
    ENGLISH("English"),

    /** Only Hindi-language articles. */
    HINDI("Hindi"),

    /** Both Hindi and English interleaved (the raw fetch order). */
    MIX("Mix"),
}

@Serializable
data class NewsSource(
    val id: String?,
    val name: String?,
)

enum class NewsRegion(
    val countryCode: String,
    val label: String,
    val rssBaseUrl: String,
    val hl: String,
    val gl: String,
    val ceid: String,
) {
    INDIA("in", "India", "https://news.google.com/rss", "en-IN", "IN", "IN:en"),
    INTERNATIONAL("us", "International", "https://news.google.com/rss", "en-US", "US", "US:en"),
}

// Gnews.io API response models
@Serializable
data class GnewsApiResponse(
    val totalArticles: Int = 0,
    val articles: List<GnewsArticle> = emptyList(),
    val code: String? = null,
    val message: String? = null,
    val errors: List<String>? = null,
)

@Serializable
data class GnewsArticle(
    val source: GnewsSource?,
    val title: String?,
    val description: String?,
    val content: String?,
    val url: String?,
    val image: String?,
    @SerialName("publishedAt") val publishedAt: String?,
)

@Serializable
data class GnewsSource(
    val name: String?,
    val url: String?,
)
