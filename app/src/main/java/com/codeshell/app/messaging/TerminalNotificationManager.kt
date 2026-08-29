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
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.news.NewsCache
import com.codeshell.app.call.BackgroundAudioCallActivity
import com.codeshell.app.receiver.CallNotificationActionReceiver
import com.codeshell.app.receiver.NotificationDismissReceiver

object TerminalNotificationManager {

    const val ACTION_CONNECT_CALL = "com.codeshell.app.action.CONNECT_CALL"
    const val ACTION_REJECT_CALL = "com.codeshell.app.action.REJECT_CALL"
    const val ACTION_DISCONNECT_CALL = "com.codeshell.app.action.DISCONNECT_CALL"
    const val EXTRA_CHAT_ID = "extra_notification_chat_id"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALL_TYPE = "extra_call_type"
    const val EXTRA_DIRECT_CALL = "extra_direct_call"
    const val EXTRA_IS_CALL = "extra_notification_is_call"
    const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
    const val EXTRA_OPEN_LEARNING = "extra_open_learning"
    const val EXTRA_NOTIFICATION_CHAT_ID = "extra_notification_chat_id_for_clear"
    /** News flavor: deep-link URL of the article whose headline is shown. */
    const val EXTRA_NEWS_ARTICLE_URL = "extra_news_article_url"
    const val EXTRA_NEWS_CATEGORY_ID = "extra_news_category_id"
    const val EXTRA_NEWS_REGION_CC = "extra_news_region_cc"

    private const val PREFS = "terminal_notifications"
    // Versioned so existing installs receive the badge-disabled channel.
    private const val CHANNEL_ID = "terminal_alerts_v2"
    private const val COUNT_PREFIX = "count_"

    fun showMessageNotification(context: Context, chatId: String, notificationBody: String? = null) {
        if (LocalActiveChatStore.isViewingChat(chatId)) return

        val appContext = context.applicationContext
        ensureChannel(appContext)
        val count = incrementCount(appContext, chatId)
        val flavored = newsFlavoredBody(appContext, R.string.terminal_alert_message_body, prefixLive = false)
        postNotification(
            context = appContext,
            chatId = chatId,
            isCall = false,
            body = notificationBody ?: flavored.body,
            newsArticleUrl = flavored.articleUrl,
            newsCategoryId = flavored.categoryId,
            newsRegionCc = flavored.regionCc,
            count = count,
        )
    }

    fun showCallNotification(
        context: Context,
        chatId: String,
        callId: String,
        callType: CallType,
        controlsEnabled: Boolean,
    ) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val fallback = if (controlsEnabled) {
            if (callType == CallType.VIDEO) {
                R.string.call_notification_incoming_video
            } else {
                R.string.call_notification_incoming_audio
            }
        } else {
            R.string.terminal_alert_call_body
        }
        val flavored = if (controlsEnabled) {
            FlavoredBody(appContext.getString(fallback), null, null, null)
        } else {
            newsFlavoredBody(appContext, fallback, prefixLive = true)
        }
        postNotification(
            context = appContext,
            chatId = chatId,
            isCall = true,
            body = flavored.body,
            newsArticleUrl = flavored.articleUrl,
            newsCategoryId = flavored.categoryId,
            newsRegionCc = flavored.regionCc,
            count = 1,
            callId = callId,
            callType = callType,
            controlsEnabled = controlsEnabled,
        )
    }

    fun showCallOutcomeNotification(context: Context, callType: CallType, status: String) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val body = when (status.lowercase()) {
            "declined" -> appContext.getString(R.string.call_outcome_declined)
            "missed" -> appContext.getString(R.string.call_outcome_missed)
            "not_answered" -> appContext.getString(R.string.call_outcome_not_answered)
            else -> return
        }
        if (!canPostNotifications(appContext)) return
        // The backend uses "missed" for the receiver and
        // "not_answered"/"declined" for the caller. Keep the direction in
        // the heading instead of presenting every outcome as outgoing.
        val isIncomingOutcome = status.equals("missed", ignoreCase = true)
        val title = when {
            isIncomingOutcome && callType == CallType.VIDEO ->
                appContext.getString(R.string.video_call_incoming_indicator)
            isIncomingOutcome ->
                appContext.getString(R.string.call_incoming_indicator)
            callType == CallType.VIDEO ->
                appContext.getString(R.string.video_call_outgoing_indicator)
            else ->
                appContext.getString(R.string.call_outgoing_indicator)
        }
        NotificationManagerCompat.from(appContext).notify(
            ("call-outcome:$status:${callType.name}").hashCode() and 0x7FFFFFFF,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build(),
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

    fun clearAll(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancelAll()
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
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
        callId: String? = null,
        callType: CallType = CallType.VOICE,
        controlsEnabled: Boolean = false,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isCall && controlsEnabled && !callId.isNullOrBlank()) {
                putExtra(EXTRA_DIRECT_CALL, true)
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALL_TYPE, callType.name)
            } else if (isCall) {
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

        if (isCall && controlsEnabled && !callId.isNullOrBlank()) {
            builder
                .addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.call_notification_connect),
                    callActionIntent(context, ACTION_CONNECT_CALL, chatId, callId, callType, 20_001),
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.call_notification_reject),
                    callActionIntent(context, ACTION_REJECT_CALL, chatId, callId, callType, 20_002),
                )
        }

        if (!canPostNotifications(context)) return
        NotificationManagerCompat.from(context).notify(notificationId(chatId, isCall), builder.build())
    }

    private fun callActionIntent(
        context: Context,
        action: String,
        chatId: String,
        callId: String,
        callType: CallType,
        requestCode: Int,
    ): PendingIntent {
        if (action == ACTION_CONNECT_CALL) {
            val intent = if (callType == CallType.VOICE) {
                Intent(context, BackgroundAudioCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(BackgroundAudioCallActivity.EXTRA_CHAT_ID, chatId)
                    putExtra(BackgroundAudioCallActivity.EXTRA_CALL_ID, callId)
                }
            } else {
                // Video Connect must open the visible caller screen so the
                // existing permission and accept flow can run in the activity.
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_DIRECT_CALL, true)
                    putExtra(EXTRA_CHAT_ID, chatId)
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_CALL_TYPE, callType.name)
                }
            }
            return PendingIntent.getActivity(
                context,
                requestCode + notificationId(chatId, true),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val intent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALL_TYPE, callType.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
