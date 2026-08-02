package com.codeshell.app

import android.app.Application
import com.codeshell.app.data.news.NewsCache
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.repository.LessonRepository
import com.codeshell.app.presence.PresenceCoordinator
import com.codeshell.app.scheduler.SecretEntryResetScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CodeShellApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var lessonRepository: LessonRepository
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        enableFirestoreOfflinePersistence()
        lessonRepository = LessonRepository(this)
        PresenceCoordinator(this).register()
        NewsCache.init(this)
        applicationScope.launch {
            val preferences = AppPreferences(applicationContext)
            preferences.applyDailySecretEntryResetIfDue()
            SecretEntryResetScheduler.scheduleNextReset(applicationContext)
            // Keep the share alias component state in sync with the stored
            // preference: app updates / OEM quirks can otherwise reset the
            // runtime component state back to the manifest default (disabled).
            SharePanelController.apply(
                applicationContext,
                preferences.sharePanelEnabled.first(),
            )
        }
    }

    private fun enableFirestoreOfflinePersistence() {
        try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            // Safe to ignore during Phase 1 placeholder Firebase config.
        }
    }
}
