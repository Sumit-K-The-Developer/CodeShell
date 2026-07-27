package com.codeshell.app.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codeshell.app.BuildConfig
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.news.NewsCache
import com.codeshell.app.receiver.NotificationDismissReceiver

object TerminalNotificationManager {

    const val EXTRA_CHAT_ID = "extra_notification_chat_id"
    const val EXTRA_IS_CALL = "extra_notification_is_call"
    const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
    const val EXTRA_OPEN_LEARNING = "extra_open_learning"
    const val EXTRA_NOTIFICATION_CHAT_ID = "extra_notification_chat_id_for_clear"
    /** News flavor: deep-link URL of the article whose headline is shown. */
    const val EXTRA_NEWS_ARTICLE_URL = "extra_news_article_url"
    const val EXTRA_NEWS_CATEGORY_ID = "extra_news_category_id"
    const val EXTRA_NEWS_REGION_CC = "extra_news_region_cc"

    private const val PREFS = "terminal_notifications"
    private const val CHANNEL_ID = "terminal_alerts"
    private const val COUNT_PREFIX = "count_"

    fun showMessageNotification(context: Context, chatId: String) {
        if (LocalActiveChatStore.isViewingChat(chatId)) return

        val appContext = context.applicationContext
        ensureChannel(appContext)
        val count = incrementCount(appContext, chatId)
        val flavored = newsFlavoredBody(appContext, R.string.terminal_alert_message_body, prefixLive = false)
        postNotification(
            context = appContext,
            chatId = chatId,
            isCall = false,
            body = flavored.body,
            newsArticleUrl = flavored.articleUrl,
            newsCategoryId = flavored.categoryId,
            newsRegionCc = flavored.regionCc,
            count = count,
        )
    }

    fun showCallNotification(context: Context, chatId: String) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val flavored = newsFlavoredBody(appContext, R.string.terminal_alert_call_body, prefixLive = true)
        postNotification(
            context = appContext,
            chatId = chatId,
            isCall = true,
            body = flavored.body,
            newsArticleUrl = flavored.articleUrl,
            newsCategoryId = flavored.categoryId,
            newsRegionCc = flavored.regionCc,
            count = 1,
        )
    }

    private data class FlavoredBody(
        val body: String,
        val articleUrl: String?,
        val categoryId: String?,
        val regionCc: String?,
    )

    /**
     * News flavor surfaces a random cached news headline as the notification
     * body instead of the disguised "snippet" string. For calls, the headline
     * is prefixed with "LIVE ". Falls back to [fallbackRes] when the cache is
     * empty (cold start) or when not running the news flavor.
     */
    private fun newsFlavoredBody(
        context: Context,
        fallbackRes: Int,
        prefixLive: Boolean,
    ): FlavoredBody {
        if (BuildConfig.SHELL_FLAVOR != "news") {
            return FlavoredBody(context.getString(fallbackRes), null, null, null)
        }
        val ref = runCatching { NewsCache.instance.randomArticle() }.getOrNull()
        if (ref == null) {
            return FlavoredBody(context.getString(fallbackRes), null, null, null)
        }
        val headline = ref.article.title?.takeIf { it.isNotBlank() }
            ?: return FlavoredBody(context.getString(fallbackRes), null, null, null)
        val body = if (prefixLive) "LIVE $headline" else headline
        return FlavoredBody(
            body = body,
            articleUrl = ref.article.url,
            categoryId = ref.categoryId,
            regionCc = ref.region.countryCode,
        )
    }

    fun clear(context: Context, chatId: String, isCall: Boolean = false) {
        val appContext = context.applicationContext
        if (!isCall) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(countKey(chatId))
                .apply()
        }
        NotificationManagerCompat.from(appContext)
            .cancel(notificationId(chatId, isCall))
    }

    fun clearAllForChat(context: Context, chatId: String) {
        clear(context, chatId, isCall = false)
        clear(context, chatId, isCall = true)
    }

    private fun postNotification(
        context: Context,
        chatId: String,
        isCall: Boolean,
        body: String,
        newsArticleUrl: String?,
        newsCategoryId: String?,
        newsRegionCc: String?,
        count: Int,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isCall) {
                putExtra(EXTRA_OPEN_LEARNING, true)
            } else {
                putExtra(EXTRA_OPEN_LEARNING, true)
                putExtra(EXTRA_NOTIFICATION_CHAT_ID, chatId)
            }
            // News flavor deep-link: route the user to the cached article
            // whose headline appears in this notification.
            if (!newsArticleUrl.isNullOrBlank()) {
                putExtra(EXTRA_NEWS_ARTICLE_URL, newsArticleUrl)
                putExtra(EXTRA_NEWS_CATEGORY_ID, newsCategoryId ?: "")
                putExtra(EXTRA_NEWS_REGION_CC, newsRegionCc ?: "")
            }
        }
        val requestCode = notificationId(chatId, isCall)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_IS_CALL, isCall)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 10_000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!isCall)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (isCall) {
                    NotificationCompat.CATEGORY_CALL
                } else {
                    NotificationCompat.CATEGORY_MESSAGE
                },
            )

        if (!isCall && count > 1) {
            builder.setNumber(count)
        }

        if (!canPostNotifications(context)) return
        NotificationManagerCompat.from(context).notify(notificationId(chatId, isCall), builder.build())
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun incrementCount(context: Context, chatId: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(countKey(chatId), 0) + 1
        prefs.edit().putInt(countKey(chatId), next).apply()
        return next
    }

    private fun countKey(chatId: String): String = COUNT_PREFIX + chatId

    private fun notificationId(chatId: String, isCall: Boolean): Int {
        val key = if (isCall) "call:$chatId" else "msg:$chatId"
        return key.hashCode() and 0x7FFFFFFF
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.terminal_alert_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.terminal_alert_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
