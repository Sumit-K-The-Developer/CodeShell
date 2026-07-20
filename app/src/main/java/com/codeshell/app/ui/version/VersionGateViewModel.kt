package com.codeshell.app.ui.version

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.model.AppConfig
import com.codeshell.app.data.repository.AppConfigRepository
import com.codeshell.app.util.VersionCheckResult
import com.codeshell.app.util.VersionChecker
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VersionGateViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val appConfigRepository = AppConfigRepository()

    private val analytics: FirebaseAnalytics = Firebase.analytics

    private val _uiState = MutableStateFlow(VersionGateUiState())
    val uiState: StateFlow<VersionGateUiState> = _uiState.asStateFlow()

    init {
        checkVersion()
    }

    fun checkVersion() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val versionCode = VersionChecker.currentVersionCode(context)
            analytics.setUserProperty("app_version_code", versionCode.toString())
            logEvent("app_open")

            val configResult = appConfigRepository.fetchAppConfig()
            configResult.fold(
                onSuccess = { config ->
                    val result = VersionChecker.evaluate(context, config)
                    logEvent(
                        "version_check_result",
                        mapOf("result" to result.name.lowercase())
                    )
                    when (result) {
                        VersionCheckResult.Ok -> {
                            _uiState.update {
                                it.copy(isLoading = false, canProceed = true, config = config)
                            }
                        }
                        VersionCheckResult.SoftUpdate -> {
                            logEvent("soft_update_shown")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    canProceed = true,
                                    config = config,
                                    showSoftUpdate = true
                                )
                            }
                        }
                        VersionCheckResult.ForceUpdate -> {
                            logEvent("force_update_shown")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    canProceed = false,
                                    config = config,
                                    showBlockingUpdate = true
                                )
                            }
                        }
                        VersionCheckResult.Discontinued -> {
                            logEvent("app_discontinued")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    canProceed = false,
                                    config = config,
                                    showDiscontinued = true
                                )
                            }
                        }
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, canProceed = true)
                    }
                }
            )
        }
    }

    fun dismissSoftUpdate() {
        _uiState.update { it.copy(showSoftUpdate = false) }
    }

    fun openStore(context: Context) {
        val url = _uiState.value.config?.storeUrl.orEmpty()
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val bundle = android.os.Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        analytics.logEvent(name, bundle)
    }
}

data class VersionGateUiState(
    val isLoading: Boolean = false,
    val canProceed: Boolean = true,
    val config: AppConfig? = null,
    val showSoftUpdate: Boolean = false,
    val showBlockingUpdate: Boolean = false,
    val showDiscontinued: Boolean = false
)
