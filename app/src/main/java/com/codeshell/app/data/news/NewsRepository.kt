package com.codeshell.app.data.news

import com.codeshell.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Map our category IDs to Google News topic IDs
    private val categoryToTopic = mapOf(
        "general" to "TOP_STORIES",
        "science" to "SCIENCE",
        "politics" to "POLITICS",
        "technology" to "TECHNOLOGY",
        "health" to "HEALTH",
        "sports" to "SPORTS",
        "business" to "BUSINESS",
        "entertainment" to "ENTERTAINMENT",
        "world" to "WORLD",
        "nation" to "NATION",
    )

    /**
     * Fetches news articles.
     * Primary source: Google News RSS feed (no API key needed).
     * Secondary source: Gnews.io API (fallback if RSS fails).
     */
    suspend fun fetchNews(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        // Try Google News RSS first (primary source)
        val rssResult = fetchFromRss(categoryId, region)
        if (rssResult.isSuccess && !rssResult.getOrNull().isNullOrEmpty()) {
            return@withContext rssResult
        }

        // Fallback to Gnews.io API (secondary source)
        val gnewsResult = fetchFromGnews(categoryId, region)
        if (gnewsResult.isSuccess && !gnewsResult.getOrNull().isNullOrEmpty()) {
            return@withContext gnewsResult
        }

        // Both sources failed - return the most relevant error
        gnewsResult.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                val rssError = rssResult.exceptionOrNull()
                Result.failure(
                    IOException(
                        "RSS: ${rssError?.message ?: "No articles"}" +
                            " | Gnews: ${it.message ?: "Failed"}"
                    )
                )
            }
        )
    }

    /**
     * Primary source: Google News RSS feed.
     * Uses search-based RSS for reliable category filtering.
     * No API key required.
     */
    private fun fetchFromRss(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> {
        return try {
            val rssUrl = buildRssUrl(categoryId, region)

            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("RSS HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                return Result.failure(IOException("RSS feed returned empty response"))
            }

            // Parse RSS XML using jsoup
            val doc: Document = Jsoup.parse(body)
            val items = doc.select("item")

            if (items.isEmpty()) {
                return Result.failure(IOException("No articles found in RSS feed"))
            }

            val articles = items.mapNotNull { element ->
                val title = element.select("title").firstOrNull()?.text()
                val link = element.select("link").firstOrNull()?.text()
                val description = element.select("description").firstOrNull()?.text()
                val pubDate = element.select("pubDate").firstOrNull()?.text()

                // Try to extract image from media:thumbnail or media:content
                val imageUrl = element.select("media\\:thumbnail, media\\:content").firstOrNull()
                    ?.attr("url")
                    ?: element.select("enclosure").firstOrNull()
                        ?.attr("url")
                    ?: extractImageFromDescription(description)

                // Skip items without title or link
                if (title.isNullOrBlank() || link.isNullOrBlank()) {
                    null
                } else {
                    NewsArticle(
                        source = NewsSource(
                            id = null,
                            name = region.label,
                        ),
                        author = null,
                        title = title,
                        description = description?.let { cleanHtml(it) },
                        url = link,
                        urlToImage = imageUrl,
                        publishedAt = pubDate?.let { formatRssDate(it) },
                        content = description?.let { cleanHtml(it) },
                    )
                }
            }

            if (articles.isEmpty()) {
                Result.failure(IOException("No valid articles found in RSS feed"))
            } else {
                Result.success(articles)
            }
        } catch (e: Exception) {
            Result.failure(IOException("RSS parsing failed: ${e.message}"))
        }
    }

    private fun buildRssUrl(categoryId: String, region: NewsRegion): String {
        val baseUrl = region.rssBaseUrl
        val topicId = categoryToTopic[categoryId] ?: "TOP_STORIES"

        // Use topic-based RSS URL for better category filtering
        return "$baseUrl/topic/$topicId?hl=${region.hl}&gl=${region.gl}&ceid=${region.ceid}"
    }

    /**
     * Secondary source: Gnews.io API.
     * Requires GNEWS_API_KEY in local.properties.
     */
    private fun fetchFromGnews(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> {
        return try {
            val apiKey = BuildConfig.GNEWS_API_KEY
            if (apiKey.isBlank() || apiKey == "YOUR_GNEWS_API_KEY_HERE") {
                return Result.failure(
                    IOException("Gnews.io API key not configured. Add GNEWS_API_KEY to local.properties."),
                )
            }

            val urlBuilder = StringBuilder("https://gnews.io/api/v4/top-headlines")
                .append("?lang=en")
                .append("&country=${region.countryCode}")
                .append("&max=50")
                .append("&apikey=$apiKey")

            // Gnews.io doesn't have a "politics" category; use keyword search instead
            if (categoryId == "politics") {
                urlBuilder.append("&q=politics")
            } else {
                urlBuilder.append("&category=$categoryId")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("Gnews HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                return Result.failure(IOException("Gnews API returned empty response"))
            }

            val apiResponse = json.decodeFromString<GnewsApiResponse>(body)
            if (apiResponse.code != null) {
                return Result.failure(IOException("Gnews API error: ${apiResponse.message ?: apiResponse.code}"))
            }

            val articles = apiResponse.articles.mapNotNull { gnewsArticle ->
                val title = gnewsArticle.title
                val url = gnewsArticle.url
                if (title.isNullOrBlank() || url.isNullOrBlank()) {
                    null
                } else {
                    NewsArticle(
                        source = NewsSource(
                            id = null,
                            name = gnewsArticle.source?.name ?: region.label,
                        ),
                        author = null,
                        title = title,
                        description = gnewsArticle.description,
                        url = url,
                        urlToImage = gnewsArticle.image,
                        publishedAt = gnewsArticle.publishedAt,
                        content = gnewsArticle.content,
                    )
                }
            }

            if (articles.isEmpty()) {
                Result.failure(IOException("No articles found from Gnews.io"))
            } else {
                Result.success(articles)
            }
        } catch (e: Exception) {
            Result.failure(IOException("Gnews.io request failed: ${e.message}"))
        }
    }

    private fun cleanHtml(text: String): String {
        return text.replace(Regex("<[^>]*>"), "").trim()
    }

    private fun extractImageFromDescription(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val imgRegex = Regex("src=\"([^\"]+)\"")
        return imgRegex.find(description)?.groupValues?.get(1)
    }

    private fun formatRssDate(pubDate: String): String {
        return try {
            // RSS pubDate format: "Mon, 01 Jan 2024 12:00:00 GMT"
            val inputFormat = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
            val date = inputFormat.parse(pubDate) ?: return pubDate
            outputFormat.format(date)
        } catch (e: Exception) {
            pubDate
        }
    }
}