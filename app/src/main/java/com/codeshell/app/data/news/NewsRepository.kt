package com.codeshell.app.data.news

import com.codeshell.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    )

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
     * India prefers Google News RSS (FEED) and falls back to GNews. Other
     * regions retain the GNews-first path and fall back to RSS enrichment.
     * When [skipCache] is true (refresh), skips cache and fetches fresh.
     */
    suspend fun fetchNews(
        categoryId: String,
        region: NewsRegion,
        skipCache: Boolean = false,
    ): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        if (!skipCache) {
            val cached = runCatching { NewsCache.instance.loadAll(categoryId, region) }
                .getOrNull()
            if (!cached.isNullOrEmpty()) {
                return@withContext Result.success(cached)
            }
        }

        fetchAndCache(
            categoryId = categoryId,
            region = region,
            fetchedAt = System.currentTimeMillis(),
            forceFresh = skipCache,
        )
    }

    /** Fetch readable publisher text for an article-scoped AI request. */
    suspend fun fetchArticleContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val pageUrl = if (isGoogleNewsArticleUrl(url)) decodeGoogleNewsUrl(url) ?: url else url
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Publisher page HTTP ${response.code}"))
                }
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) return@withContext Result.failure(IOException("Publisher page was empty"))
                val document = Jsoup.parse(html, pageUrl)
                val text = extractArticleParagraphs(document, targetWords = 120)
                    ?: document.select("article, [itemprop=articleBody], main")
                        .firstOrNull()
                        ?.text()
                        ?.takeIf { it.isNotBlank() }
                text?.take(MAX_BODY_CHARS)?.let { Result.success(it) }
                    ?: Result.failure(IOException("Publisher article text unavailable"))
            }
        } catch (error: Exception) {
            Result.failure(IOException("Could not fetch publisher article", error))
        }
    }

    /**
     * India tries RSS first so its visible articles are FEED articles. Other
     * regions try GNews first (fast + real descriptions) and fall back to RSS.
     * For RSS path, return immediately without blocking on publisher enrichment;
     * enrichment runs in background and saves to cache for next visit.
     *
     * For the India region, both English and Hindi feeds are fetched and
     * interleaved so the user sees both languages in the source order.
     */
    private suspend fun fetchAndCache(
        categoryId: String,
        region: NewsRegion,
        fetchedAt: Long,
        forceFresh: Boolean,
    ): Result<List<NewsArticle>> {
        val variants = langVariants(region)
        val isIndia = region == NewsRegion.INDIA

        // India should show RSS results whenever the feed is available. Keep
        // RSS deferred for international news so GNews remains its fast path.
        return coroutineScope {
            val rssDeferred = if (isIndia) {
                variants.map { v ->
                    async { fetchFromRss(categoryId, region, v.hl, v.ceid, forceFresh) }
                }
            } else {
                emptyList()
            }

            // Google News RSS carries no per-article images, so GNews is fetched
            // in parallel to supply image URLs for matching headlines. For India
            // the RSS articles keep their source order and pick up GNews images;
            // for every other region GNews remains the primary source below.
            val gnewsDeferred = variants.map { v ->
                async { fetchFromGnews(categoryId, region, v.gnewsLang, forceFresh) }
            }

            val rssResults = rssDeferred.awaitAll()
            val gnewsResults = gnewsDeferred.awaitAll()

            val rssArticles = rssResults
                .mapNotNull { it.getOrNull() }
                .flatten()
                .ifEmpty { null }
            val gnewsArticles = gnewsResults
                .mapNotNull { it.getOrNull() }
                .flatten()

            if (isIndia && !rssArticles.isNullOrEmpty()) {
                val withImages = applyGnewsImages(rssArticles, gnewsArticles)
                val merged = interleaveLanguages(withImages)
                runCatching { NewsCache.instance.save(categoryId, region, merged, fetchedAt) }
                launchFresh { enrichAndPersist(categoryId, region, merged, fetchedAt) }
                return@coroutineScope Result.success(merged)
            }

            // --- GNews path ---
            if (gnewsArticles.isNotEmpty()) {
                val merged = interleaveLanguages(gnewsArticles)
                runCatching { NewsCache.instance.save(categoryId, region, merged, fetchedAt) }
                return@coroutineScope Result.success(merged)
            }

            val fallbackRssResults = if (isIndia) {
                rssResults
            } else {
                variants.map { v ->
                    fetchFromRss(categoryId, region, v.hl, v.ceid, forceFresh)
                }
            }
            val fallbackRssArticles = fallbackRssResults
                .mapNotNull { it.getOrNull() }
                .flatten()
                .ifEmpty { null }

            if (!fallbackRssArticles.isNullOrEmpty()) {
                val merged = interleaveLanguages(fallbackRssArticles)
                runCatching { NewsCache.instance.save(categoryId, region, merged, fetchedAt) }
                launchFresh { enrichAndPersist(categoryId, region, merged, fetchedAt) }
                return@coroutineScope Result.success(merged)
            }

            // --- Both failed ---
            val firstRssError = fallbackRssResults.firstOrNull()?.exceptionOrNull()
            val gnewsErrorMessage = "Gnews: " + if (gnewsArticles.isEmpty()) "Failed" else "No articles"
            Result.failure(
                IOException(
                    "RSS: ${firstRssError?.message ?: "No articles"}" +
                        " | $gnewsErrorMessage",
                ),
            )
        }
    }

    /**
     * Language variants to fetch per region.
     * India: English + Hindi. International: English only.
     */
    private fun langVariants(region: NewsRegion): List<LangVariant> {
        return when (region) {
            NewsRegion.INDIA -> listOf(
                LangVariant(hl = "en-IN", ceid = "IN:en", gnewsLang = "en"),
                LangVariant(hl = "hi", ceid = "IN:hi", gnewsLang = "hi"),
            )
            NewsRegion.INTERNATIONAL -> listOf(
                LangVariant(hl = "en-US", ceid = "US:en", gnewsLang = "en"),
            )
        }
    }

    private data class LangVariant(
        val hl: String,
        val ceid: String,
        val gnewsLang: String,
    )

    /**
     * Interleave language lists round-robin while preserving each feed's
     * newest-first order. Shuffling made a refresh appear to return old
     * articles because an older item could land at the top of the pager.
     */
    private fun interleaveLanguages(articles: List<NewsArticle>): List<NewsArticle> {
        if (articles.size <= 1) return articles
        val groups = listOf("en", "hi", null).map { language ->
            articles.filter { it.language == language }
        }.filter { it.isNotEmpty() }
        val result = ArrayList<NewsArticle>(articles.size)
        var index = 0
        while (result.size < articles.size) {
            groups.forEach { group ->
                group.getOrNull(index)?.let(result::add)
            }
            index++
        }
        return result
    }

    private fun launchFresh(block: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (_: Exception) { /* best-effort background work */ }
        }
    }

    /**
     * Enrich first N articles with publisher OG descriptions/images,
     * then persist the enriched list to cache so next visit benefits.
     */
    private suspend fun enrichAndPersist(
        categoryId: String,
        region: NewsRegion,
        articles: List<NewsArticle>,
        fetchedAt: Long,
    ) {
        if (articles.isEmpty()) return
        val enriched = enrichArticles(articles)
        runCatching { NewsCache.instance.save(categoryId, region, enriched, fetchedAt) }
    }

    private fun fetchFromRss(
        categoryId: String,
        region: NewsRegion,
        hl: String = region.hl,
        ceid: String = region.ceid,
        forceFresh: Boolean = false,
    ): Result<List<NewsArticle>> {
        return try {
            val rssUrl = buildRssUrl(categoryId, region, hl, ceid, forceFresh)
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .header("Accept-Language", hl)
                .apply {
                    if (forceFresh) {
                        header("Cache-Control", "no-cache, no-store")
                        header("Pragma", "no-cache")
                    }
                }
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
                            feedTag = FEED_TAG_RSS,
                            language = if (hl.startsWith("hi")) "hi" else "en",
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

    private fun buildRssUrl(
        categoryId: String,
        region: NewsRegion,
        hl: String = region.hl,
        ceid: String = region.ceid,
        forceFresh: Boolean = false,
    ): String {
        val base = region.rssBaseUrl.trimEnd('/')
        val query = "hl=$hl&gl=${region.gl}&ceid=$ceid"
        val freshQuery = if (forceFresh) "$query&cacheBust=${System.currentTimeMillis()}" else query
        val normalized = categoryId.lowercase(Locale.US)

        if (normalized !in categoryToGoogleTopic) {
            val q = URLEncoder.encode(categoryId, StandardCharsets.UTF_8.name())
            return "$base/search?q=$q&$freshQuery"
        }

        val topic = categoryToGoogleTopic[normalized]
        return if (topic == null) {
            "$base?$freshQuery"
        } else {
            "$base/headlines/section/topic/$topic?$freshQuery"
        }
    }

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
        val jsonStart = raw.indexOf('\n')
        val payload = if (jsonStart >= 0) raw.substring(jsonStart).trim() else raw.trim()
        if (payload.isBlank()) return null

        return try {
            val root = json.parseToJsonElement(payload).jsonArray
            val first = root.firstOrNull()?.jsonArray ?: return null
            val encoded = first.getOrNull(2)?.jsonPrimitive?.content ?: return null
            val inner = json.parseToJsonElement(encoded).jsonArray
            inner.getOrNull(1)?.jsonPrimitive?.content?.takeIf {
                it.startsWith("http", ignoreCase = true)
            }
        } catch (_: Exception) {
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
                if (html.length > HTML_CAP) return article

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

                // Target: at least MIN_WORDS_WITH_IMAGE (80) words of body text when
                // a header image is available, MIN_WORDS_NO_IMAGE (120) when not.
                // The card shows description + content stacked, so the body
                // (content) makes up the remainder after `description`.
                val targetWords = if (!image.isNullOrBlank()) MIN_WORDS_WITH_IMAGE else MIN_WORDS_NO_IMAGE
                val descriptionWords = countWords(description)
                val neededWords = (targetWords - descriptionWords).coerceAtLeast(0)

                // Preserve the original RSS related-headlines as `content` fallback
                // so the card combines OG summary (description) + headlines (content).
                val preservedBody = article.description
                    ?.takeIf { it != ogDescription }
                    ?: article.content
                val preservedWords = countWords(preservedBody)

                // Extract publisher paragraphs.  Ask for enough words to hit the
                // target minus what description already provides; fall back to the
                // preserved RSS headlines if paragraphs come up short.
                val articleParagraphs = extractArticleParagraphs(doc, neededWords)
                val articleBody = when {
                    countWords(articleParagraphs) >= neededWords -> articleParagraphs
                    !articleParagraphs.isNullOrBlank() && preservedWords > 0 ->
                        "$articleParagraphs\n\n$preservedBody"
                    !articleParagraphs.isNullOrBlank() -> articleParagraphs
                    else -> preservedBody
                }

                article.copy(
                    description = description,
                    content = articleBody,
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

    /**
     * Extract the first few readable <p> paragraphs from the publisher article
     * to fill the card with real body text (10-15 lines).  Skips short or
     * noisy paragraphs (ads, newsletter prompts, etc.).
     */
    private fun extractArticleParagraphs(doc: Document, targetWords: Int = 0): String? {
        val candidates = mutableListOf<String>()
        val selectors = listOf(
            "article p",
            "[itemprop=articleBody] p",
            "main p",
            ".article-body p",
            ".article-content p",
            ".story-content p",
            "#articlebody p",
            "p",
        )
        for (selector in selectors) {
            candidates.clear()
            var totalWords = 0
            doc.select(selector).forEach { el ->
                val text = el.text().trim()
                if (text.length < MIN_PARAGRAPH_LEN || isBoilerplateParagraph(text)) return@forEach
                // Stop once we've hit the requested word threshold
                if (targetWords > 0 && totalWords >= targetWords) return@forEach
                candidates.add(text)
                totalWords += countWords(text)
            }
            if (candidates.size >= MIN_PARAGRAPHS) break
        }
        if (candidates.isEmpty()) return null
        return candidates
            .take(MAX_PARAGRAPHS)
            .joinToString("\n\n")
            .take(MAX_BODY_CHARS)
            .takeIf { it.isNotBlank() }
    }

    private fun countWords(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return text.trim().split(Regex("\\s+")).size
    }

    private fun isBoilerplateParagraph(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return lower.contains("sign up for") ||
            lower.contains("subscribe to") ||
            lower.contains("newsletter") ||
            lower.contains("cookie policy") ||
            lower.contains("privacy policy") ||
            lower.contains("advertisement") ||
            lower.contains("read more:") ||
            lower.startsWith("photo:") ||
            lower.startsWith("also read")
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
     * Google RSS <description> is an HTML-escaped <ol> of related story links.
     * Unescape, parse, and return all link texts as readable preview copy.
     * These fill ~12–18 lines in the card until publisher OG enrichment finishes.
     */
    private fun parseGoogleRssDescription(raw: String?, articleTitle: String): String? {
        if (raw.isNullOrBlank()) return null

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
            return cleaned.joinToString("\n\n")
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
        lang: String = "en",
        forceFresh: Boolean = false,
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
                .append("?lang=$lang")
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
                        .append("&lang=$lang")
                        .append("&country=${region.countryCode}")
                        .append("&max=10")
                        .append("&apikey=$apiKey")
                }
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .apply {
                    if (forceFresh) {
                        header("Cache-Control", "no-cache, no-store")
                        header("Pragma", "no-cache")
                    }
                }
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
                            feedTag = FEED_TAG_GNEWS,
                            language = if (lang == "hi") "hi" else "en",
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

    /**
     * Google News RSS has no per-article images, so image URLs are sourced
     * from the parallel GNews response by matching normalized headlines.
     * Articles without a confident match keep their (null) image and rely on
     * the background OpenGraph enrichment instead.
     */
    private fun applyGnewsImages(
        articles: List<NewsArticle>,
        gnewsArticles: List<NewsArticle>,
    ): List<NewsArticle> {
        if (gnewsArticles.isEmpty()) return articles

        val imagesByTitle = HashMap<String, String>()
        for (g in gnewsArticles) {
            val image = g.urlToImage?.trim().orEmpty()
            val key = normalizeForImageMatch(g.title)
            if (image.isNotEmpty() && key != null && key.length >= IMAGE_MATCH_MIN_TITLE) {
                imagesByTitle[key] = image
            }
        }
        if (imagesByTitle.isEmpty()) return articles

        return articles.map { article ->
            val current = article.urlToImage?.trim().orEmpty()
            if (current.isNotEmpty()) {
                article
            } else {
                val key = normalizeForImageMatch(article.title)
                val image = key?.let(imagesByTitle::get)
                if (image != null) article.copy(urlToImage = image) else article
            }
        }
    }

    /** Lowercases and strips punctuation while keeping Unicode letters so Hindi headlines also match. */
    private fun normalizeForImageMatch(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return text.lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
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
            } catch (_: Exception) { /* try next pattern */ }
        }
        return pubDate
    }

    companion object {
        private const val ENRICH_LIMIT = 8
        private const val ENRICH_CONCURRENCY = 6
        private const val HTML_CAP = 500_000
        private const val MIN_PARAGRAPH_LEN = 80
        private const val MIN_PARAGRAPHS = 2
        private const val MAX_PARAGRAPHS = 14
        private const val MAX_BODY_CHARS = 4000
        // Minimum body word count targets after enrichment.
        // Lower bar when a header image is present (image fills vertical space),
        // higher bar when there's no image (text must fill the card).
        private const val MIN_WORDS_WITH_IMAGE = 80
        private const val MIN_WORDS_NO_IMAGE = 120

        // Minimum normalized headline length (chars) before a GNews image is
        // attached to a matching RSS headline, to avoid noisy short matches.
        private const val IMAGE_MATCH_MIN_TITLE = 12

        const val FEED_TAG_RSS = "FEED"
        const val FEED_TAG_GNEWS = "Gnews"

        private val ATTR_SIGNATURE = Regex("""data-n-a-sg="([^"]+)"""")
        private val ATTR_TIMESTAMP = Regex("""data-n-a-ts="([^"]+)"""")
        private val PUBLISHER_URL_IN_BATCH =
            Regex("""garturlres","(https?://[^"\\]+)""")
    }
}
