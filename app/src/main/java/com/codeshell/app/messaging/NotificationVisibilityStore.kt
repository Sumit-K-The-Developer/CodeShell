package com.codeshell.app.messaging

/** Tracks whether the visible foreground surface should suppress ordinary alerts. */
object NotificationVisibilityStore {

    private var appInForeground = false
    private var visibleSurface = Surface.OTHER

    @Synchronized
    fun setAppInForeground(foreground: Boolean) {
        appInForeground = foreground
    }

    @Synchronized
    fun setVisibleSurface(surface: Surface) {
        visibleSurface = surface
    }

    @Synchronized
    fun shouldSuppressOrdinaryNotifications(): Boolean =
        appInForeground && visibleSurface != Surface.OTHER

    enum class Surface {
        OTHER,
        CHAT_HOME,
        IN_CHAT,
    }
}
