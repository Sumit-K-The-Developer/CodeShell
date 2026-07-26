package com.codeshell.app.data.news

import com.codeshell.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** App category IDs → Google News section topic names. */
    private val categoryToGoogleTopic = mapOf(
        "top" to null,
        "general" to null,
        "world" to "WORLD",
        "nation" to "NATION",
        "business" to "BUSINESS",
        "technology" to "TECHNOLOGY",
        "entertainment" to "ENTERTAINMENT",
        "sports" to "SPORTS",
        "science" to "SCIENCE",
        "health" to "HEALTH",
        "politics" to "NATION",
    )

    /** App category IDs → GNews top-headlines categories. */
    private val categoryToGnews = mapOf(
        "top" to "general",
        "general" to "general",
        "world" to "world",
        "nation" to "nation",
        "business" to "business",
        "technology" to "technology",
        "entertainment" to "entertainment",
        "sports" to "sports",
        "science" to "science",
        "health" to "health",
    )

    /**
     * Fetches news articles.
     * Primary: Google News RSS (no API key), enriched with publisher OG meta.
     * Fallback: GNews.io API.
     */
    suspend fun fetchNews(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        val rssResult = fetchFromRss(categoryId, region)
        if (rssResult.isSuccess && !rssResult.getOrNull().isNullOrEmpty()) {
            val articles = rssResult.getOrNull().orEmpty()
            return@withContext Result.success(enrichArticles(articles))
        }

        val gnewsResult = fetchFromGnews(categoryId, region)
        if (gnewsResult.isSuccess && !gnewsResult.getOrNull().isNullOrEmpty()) {
            return@withContext gnewsResult
        }

        gnewsResult.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                val rssError = rssResult.exceptionOrNull()
                Result.failure(
                    IOException(
                        "RSS: ${rssError?.message ?: "No articles"}" +
                            " | Gnews: ${it.message ?: "Failed"}",
                    ),
                )
            },
        )
    }

    private fun fetchFromRss(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> {
        return try {
            val rssUrl = buildRssUrl(categoryId, region)
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .header("Accept-Language", region.hl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException("RSS HTTP ${response.code}: ${response.message}"),
                    )
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.failure(IOException("RSS feed returned empty response"))
                }

                val doc = Jsoup.parse(body, "", Parser.xmlParser())
                val items = doc.select("item")
                if (items.isEmpty()) {
                    return Result.failure(IOException("No articles found in RSS feed"))
                }

                val articles = items.mapNotNull { element ->
                    val title = element.selectFirst("title")?.text()?.trim()
                    val link = element.selectFirst("link")?.text()?.trim()
                        ?: element.selectFirst("link")?.attr("href")?.trim()
                    val descriptionRaw = element.selectFirst("description")?.wholeText()
                        ?: element.selectFirst("description")?.text()
                    val pubDate = element.selectFirst("pubDate")?.text()
                    val sourceName = element.selectFirst("source")?.text()?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: extractSourceFromTitle(title)
                        ?: region.label

                    if (title.isNullOrBlank() || link.isNullOrBlank()) {
                        null
                    } else {
                        val cleanedTitle = cleanTitle(title, sourceName)
                        val fallbackBody = parseGoogleRssDescription(descriptionRaw, cleanedTitle)
                        NewsArticle(
                            source = NewsSource(id = null, name = sourceName),
                            author = null,
                            title = cleanedTitle,
                            description = fallbackBody,
                            url = link,
                            urlToImage = extractImageFromDescription(descriptionRaw),
                            publishedAt = pubDate?.let { formatRssDate(it) },
                            content = null,
                        )
                    }
                }

                if (articles.isEmpty()) {
                    Result.failure(IOException("No valid articles found in RSS feed"))
                } else {
                    Result.success(articles)
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("RSS parsing failed: ${e.message}", e))
        }
    }

    /**
     * Google News RSS endpoints:
     * - Top: https://news.google.com/rss?hl=&gl=&ceid=
     * - Topic: https://news.google.com/rss/headlines/section/topic/{TOPIC}?hl=&gl=&ceid=
     * - Fallback search: https://news.google.com/rss/search?q=...&hl=&gl=&ceid=
     */
    private fun buildRssUrl(categoryId: String, region: NewsRegion): String {
        val base = region.rssBaseUrl.trimEnd('/')
        val query = "hl=${region.hl}&gl=${region.gl}&ceid=${region.ceid}"
        val normalized = categoryId.lowercase(Locale.US)

        if (normalized !in categoryToGoogleTopic) {
            val q = URLEncoder.encode(categoryId, StandardCharsets.UTF_8.name())
            return "$base/search?q=$q&$query"
        }

        val topic = categoryToGoogleTopic[normalized]
        return if (topic == null) {
            "$base?$query"
        } else {
            "$base/headlines/section/topic/$topic?$query"
        }
    }

    /**
     * Decode Google News article URLs and scrape publisher OG description/image.
     * Limited concurrency so category loads stay responsive.
     */
    private suspend fun enrichArticles(articles: List<NewsArticle>): List<NewsArticle> {
        if (articles.isEmpty()) return articles
        val toEnrich = articles.take(ENRICH_LIMIT)
        val rest = articles.drop(ENRICH_LIMIT)
        val semaphore = Semaphore(ENRICH_CONCURRENCY)

        return coroutineScope {
            val enriched = toEnrich.map { article ->
                async {
                    semaphore.withPermit {
                        runCatching { enrichArticle(article) }.getOrDefault(article)
                    }
                }
            }.awaitAll()
            enriched + rest
        }
    }

    private fun enrichArticle(article: NewsArticle): NewsArticle {
        val googleUrl = article.url ?: return article
        if (!isGoogleNewsArticleUrl(googleUrl)) {
            return scrapeOpenGraph(article, googleUrl)
        }

        val publisherUrl = decodeGoogleNewsUrl(googleUrl) ?: return article
        return scrapeOpenGraph(article.copy(url = publisherUrl), publisherUrl)
    }

    private fun isGoogleNewsArticleUrl(url: String): Boolean {
        return url.contains("news.google.com/", ignoreCase = true) &&
            (url.contains("/articles/") || url.contains("/read/"))
    }

    private fun extractGoogleArticleId(url: String): String? {
        val marker = when {
            url.contains("/articles/") -> "/articles/"
            url.contains("/read/") -> "/read/"
            else -> return null
        }
        val id = url.substringAfter(marker).substringBefore("?").substringBefore("#")
        return id.takeIf { it.isNotBlank() }
    }

    /**
     * Decodes news.google.com/rss/articles/... to the original publisher URL.
     * Uses Google's batchexecute endpoint (same approach as public GN URL decoders).
     */
    private fun decodeGoogleNewsUrl(googleUrl: String): String? {
        val articleId = extractGoogleArticleId(googleUrl) ?: return null
        val (signature, timestamp) = fetchGoogleDecodeParams(articleId) ?: return null

        val inner =
            """["garturlreq",[["X","X",["X","X"],null,null,1,1,"US:en",null,1,null,null,null,null,null,0,1],"X","X",1,[1,1,1],1,1,null,0,0,null,0],"$articleId",$timestamp,"$signature"]"""
        val payload = JsonArray(
            listOf(
                JsonArray(
                    listOf(
                        JsonArray(
                            listOf(
                                JsonPrimitive("Fbv4je"),
                                JsonPrimitive(inner),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val form = FormBody.Builder()
            .add("f.req", payload.toString())
            .build()
        val request = Request.Builder()
            .url("https://news.google.com/_/DotsSplashUi/data/batchexecute")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Origin", "https://news.google.com")
            .header("Referer", "https://news.google.com/")
            .post(form)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return parseDecodedPublisherUrl(body)
        }
    }

    private fun fetchGoogleDecodeParams(articleId: String): Pair<String, String>? {
        val urls = listOf(
            "https://news.google.com/articles/$articleId",
            "https://news.google.com/rss/articles/$articleId",
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val html = response.body?.string().orEmpty()
                    val signature = ATTR_SIGNATURE.find(html)?.groupValues?.getOrNull(1)
                    val timestamp = ATTR_TIMESTAMP.find(html)?.groupValues?.getOrNull(1)
                    if (!signature.isNullOrBlank() && !timestamp.isNullOrBlank()) {
                        return signature to timestamp
                    }
                }
            } catch (_: Exception) {
                // try next url
            }
        }
        return null
    }

    private fun parseDecodedPublisherUrl(raw: String): String? {
        // Response prefix: )]}'\n\n[[...]]
        val jsonStart = raw.indexOf('\n')
        val payload = if (jsonStart >= 0) raw.substring(jsonStart).trim() else raw.trim()
        if (payload.isBlank()) return null

        return try {
            val root = json.parseToJsonElement(payload).jsonArray
            val first = root.firstOrNull()?.jsonArray ?: return null
            // ["wrb.fr","Fbv4je","[\"garturlres\",\"https://...\"]", ...]
            val encoded = first.getOrNull(2)?.jsonPrimitive?.content ?: return null
            val inner = json.parseToJsonElement(encoded).jsonArray
            // ["garturlres","https://publisher...", ...]
            inner.getOrNull(1)?.jsonPrimitive?.content?.takeIf {
                it.startsWith("http", ignoreCase = true)
            }
        } catch (_: Exception) {
            // Fallback regex if JSON shape changes slightly
            PUBLISHER_URL_IN_BATCH.find(raw)?.groupValues?.getOrNull(1)
        }
    }

    private fun scrapeOpenGraph(article: NewsArticle, pageUrl: String): NewsArticle {
        return try {
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return article
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) return article

                val doc = Jsoup.parse(html, pageUrl)
                val ogDescription = firstMetaContent(
                    doc,
                    "meta[property=og:description]",
                    "meta[name=twitter:description]",
                    "meta[name=description]",
                )?.takeIf { it.isNotBlank() && !isGenericGoogleDescription(it) }

                val ogImage = firstMetaContent(
                    doc,
                    "meta[property=og:image]",
                    "meta[property=og:image:secure_url]",
                    "meta[name=twitter:image]",
                    "meta[name=twitter:image:src]",
                )?.let { absolutizeUrl(pageUrl, it) }
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }

                val description = ogDescription
                    ?: article.description
                val image = ogImage
                    ?: article.urlToImage

                article.copy(
                    description = description,
                    content = ogDescription ?: article.content,
                    urlToImage = image,
                    url = pageUrl,
                )
            }
        } catch (_: Exception) {
            article
        }
    }

    private fun firstMetaContent(doc: Document, vararg selectors: String): String? {
        for (selector in selectors) {
            val value = doc.selectFirst(selector)?.attr("content")?.trim()
            if (!value.isNullOrBlank()) return Parser.unescapeEntities(value, false)
        }
        return null
    }

    private fun absolutizeUrl(baseUrl: String, maybeRelative: String): String {
        return try {
            java.net.URI(baseUrl).resolve(maybeRelative).toString()
        } catch (_: Exception) {
            maybeRelative
        }
    }

    private fun isGenericGoogleDescription(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return lower.contains("aggregated from sources all over the world by google news") ||
            lower.contains("comprehensive, up-to-date news coverage")
    }

    /**
     * Google RSS <description> is an HTML-escaped <ol> of related story links,
     * not article body. Unescape, parse, and turn link texts into readable copy.
     */
    private fun parseGoogleRssDescription(raw: String?, articleTitle: String): String? {
        if (raw.isNullOrBlank()) return null

        // wholeText/text may still contain entity-escaped HTML (&lt;ol&gt;...)
        val unescaped = Parser.unescapeEntities(raw, false).trim()
        val html = if (unescaped.contains('<')) {
            unescaped
        } else {
            Parser.unescapeEntities(unescaped, false)
        }

        val doc = Jsoup.parseBodyFragment(html)

        val linkTexts = doc.select("a").mapNotNull { el ->
            el.text().trim().takeIf { it.isNotBlank() }
        }.ifEmpty {
            doc.select("li").mapNotNull { el ->
                el.text().trim().takeIf { it.isNotBlank() }
            }
        }

        val cleaned = linkTexts
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { looksLikeUrlNoise(it) }
            .distinct()
            .filterNot { titlesRoughlyEqual(it, articleTitle) }

        if (cleaned.isNotEmpty()) {
            // Lead with the first related blurb; keep a couple more as context.
            return cleaned.take(3).joinToString("\n\n")
        }

        val plain = doc.text()
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() && !looksLikeUrlNoise(it) }

        return plain
    }

    private fun looksLikeUrlNoise(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("href=", ignoreCase = true)) return true
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
            return t.length > 40 && !t.contains(' ')
        }
        if (t.contains("news.google.com/rss/articles", ignoreCase = true)) return true
        return Regex("""(?i)href\s*=\s*['"]https?://""").containsMatchIn(t)
    }

    private fun titlesRoughlyEqual(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val na = norm(a)
        val nb = norm(b)
        if (na.isBlank() || nb.isBlank()) return false
        return na == nb || na.startsWith(nb) || nb.startsWith(na)
    }

    private fun fetchFromGnews(
        categoryId: String,
        region: NewsRegion,
    ): Result<List<NewsArticle>> {
        return try {
            val apiKey = BuildConfig.GNEWS_API_KEY
            if (apiKey.isBlank() || apiKey == "YOUR_GNEWS_API_KEY_HERE") {
                return Result.failure(
                    IOException(
                        "Gnews.io API key not configured. Add GNEWS_API_KEY to local.properties.",
                    ),
                )
            }

            val normalized = categoryId.lowercase(Locale.US)
            val urlBuilder = StringBuilder("https://gnews.io/api/v4/top-headlines")
                .append("?lang=en")
                .append("&country=${region.countryCode}")
                .append("&max=10")
                .append("&apikey=$apiKey")

            when {
                normalized == "politics" -> urlBuilder.append("&q=politics")
                categoryToGnews.containsKey(normalized) ->
                    urlBuilder.append("&category=${categoryToGnews[normalized]}")
                else -> {
                    val q = URLEncoder.encode(categoryId, StandardCharsets.UTF_8.name())
                    urlBuilder.clear()
                    urlBuilder.append("https://gnews.io/api/v4/search")
                        .append("?q=$q")
                        .append("&lang=en")
                        .append("&country=${region.countryCode}")
                        .append("&max=10")
                        .append("&apikey=$apiKey")
                }
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = parseGnewsError(body)
                    return Result.failure(
                        IOException(
                            apiMessage
                                ?: "Gnews HTTP ${response.code}: ${response.message}",
                        ),
                    )
                }

                if (body.isBlank()) {
                    return Result.failure(IOException("Gnews API returned empty response"))
                }

                val apiResponse = json.decodeFromString<GnewsApiResponse>(body)
                if (apiResponse.code != null || !apiResponse.errors.isNullOrEmpty()) {
                    val message = apiResponse.message
                        ?: apiResponse.errors?.joinToString()
                        ?: apiResponse.code
                    return Result.failure(IOException("Gnews API error: $message"))
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
            }
        } catch (e: Exception) {
            Result.failure(IOException("Gnews.io request failed: ${e.message}", e))
        }
    }

    private fun parseGnewsError(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val err = json.decodeFromString<GnewsApiResponse>(body)
            err.message
                ?: err.errors?.joinToString()
                ?: err.code
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanTitle(title: String, sourceName: String): String {
        val suffix = " - $sourceName"
        return if (title.endsWith(suffix, ignoreCase = true)) {
            title.dropLast(suffix.length).trim()
        } else {
            title
        }
    }

    private fun extractSourceFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val sep = title.lastIndexOf(" - ")
        if (sep <= 0 || sep >= title.length - 3) return null
        return title.substring(sep + 3).trim().takeIf { it.isNotBlank() && it.length <= 80 }
    }

    private fun extractImageFromDescription(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val unescaped = Parser.unescapeEntities(description, false)
        val parsed = Jsoup.parseBodyFragment(unescaped)
        return parsed.selectFirst("img[src]")?.attr("src")?.takeIf { it.isNotBlank() }
    }

    private fun formatRssDate(pubDate: String): String {
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssX",
        )
        val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (pattern in patterns) {
            try {
                val inputFormat = SimpleDateFormat(pattern, Locale.US)
                val date = inputFormat.parse(pubDate) ?: continue
                return outputFormat.format(date)
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return pubDate
    }

    companion object {
        private const val ENRICH_LIMIT = 12
        private const val ENRICH_CONCURRENCY = 4

        private val ATTR_SIGNATURE = Regex("""data-n-a-sg="([^"]+)"""")
        private val ATTR_TIMESTAMP = Regex("""data-n-a-ts="([^"]+)"""")
        private val PUBLISHER_URL_IN_BATCH =
            Regex("""garturlres","(https?://[^"\\]+)""")
    }
}
