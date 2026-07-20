package com.codeshell.app.data.repository

import com.codeshell.app.data.model.AppConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AppConfigRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun fetchAppConfig(): Result<AppConfig> {
        return try {
            val snapshot = firestore.collection(COLLECTION_CONFIG).document(DOC_APP).get().await()
            if (!snapshot.exists()) {
                return Result.success(AppConfig())
            }
            Result.success(
                AppConfig(
                    minVersionCode = snapshot.getLong(FIELD_MIN_VERSION) ?: 1L,
                    latestVersionCode = snapshot.getLong(FIELD_LATEST_VERSION) ?: 1L,
                    discontinued = snapshot.getBoolean(FIELD_DISCONTINUED) == true,
                    updateTitle = snapshot.getString(FIELD_UPDATE_TITLE).orEmpty()
                        .ifBlank { "Update available" },
                    updateMessage = snapshot.getString(FIELD_UPDATE_MESSAGE).orEmpty()
                        .ifBlank { "A newer version of CodeShell is available." },
                    storeUrl = snapshot.getString(FIELD_STORE_URL).orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val COLLECTION_CONFIG = "config"
        private const val DOC_APP = "app"
        private const val FIELD_MIN_VERSION = "minVersionCode"
        private const val FIELD_LATEST_VERSION = "latestVersionCode"
        private const val FIELD_DISCONTINUED = "discontinued"
        private const val FIELD_UPDATE_TITLE = "updateTitle"
        private const val FIELD_UPDATE_MESSAGE = "updateMessage"
        private const val FIELD_STORE_URL = "storeUrl"
    }
}
