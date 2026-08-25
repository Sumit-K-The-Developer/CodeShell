package com.codeshell.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.ui.components.filterDigitUsernameInput
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.util.UsernameValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val appPreferences: AppPreferences = AppPreferences(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update {
            it.copy(username = filterDigitUsernameInput(value), errorMessage = null)
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validation = UsernameValidator.validateForLogin(state.username)
        if (validation is UsernameValidator.Validation.Invalid) {
            _uiState.update { it.copy(errorMessage = validation.reason) }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Access key must be at least 6 characters") }
            return
        }
        if (NetworkConnectivity.isOffline(getApplication())) {
            _uiState.update {
                it.copy(errorMessage = NetworkConnectivity.message(getApplication()))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signIn(state.username, state.password)
            _uiState.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = {
                    authRepository.currentUser?.uid?.let { userId ->
                        appPreferences.restoreNewTerminalAlertForAccount(userId)
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Login failed") }
                }
            )
        }
    }
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
