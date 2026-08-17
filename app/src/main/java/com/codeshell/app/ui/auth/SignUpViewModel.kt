package com.codeshell.app.ui.auth

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.ui.components.filterDigitUsernameInput
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.util.UsernameValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsernameAvailability {
    Idle,
    Checking,
    Available,
    Taken,
    Invalid
}

class SignUpViewModel(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val storageRepository: StorageRepository = StorageRepository(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    private var availabilityJob: Job? = null

    private fun offlineMessage(): String = NetworkConnectivity.message(getApplication())

    fun onUsernameChange(value: String) {
        val digits = filterDigitUsernameInput(value)
        _uiState.update {
            it.copy(
                username = digits,
                errorMessage = null,
                availability = UsernameAvailability.Idle,
                availabilityMessage = null
            )
        }
        availabilityJob?.cancel()

        val validation = UsernameValidator.validateForRegistration(digits)
        if (validation is UsernameValidator.Validation.Invalid) {
            if (digits.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        availability = UsernameAvailability.Invalid,
                        availabilityMessage = validation.reason
                    )
                }
            }
            return
        }

        if (NetworkConnectivity.isOffline(getApplication())) {
            _uiState.update {
                it.copy(availabilityMessage = offlineMessage())
            }
            return
        }

        val normalized = UsernameValidator.normalize(digits)
        _uiState.update {
            it.copy(
                availability = UsernameAvailability.Checking,
                availabilityMessage = "Checking shell ID…"
            )
        }

        availabilityJob = viewModelScope.launch {
            val result = userRepository.checkUsernameAvailability(normalized)
            result.fold(
                onSuccess = { isAvailable ->
                    _uiState.update { state ->
                        if (UsernameValidator.normalize(state.username) != normalized) return@update state
                        if (isAvailable) {
                            state.copy(
                                availability = UsernameAvailability.Available,
                                availabilityMessage = "CodeShell ID available"
                            )
                        } else {
                            state.copy(
                                availability = UsernameAvailability.Taken,
                                availabilityMessage = "CodeShell ID taken"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        if (UsernameValidator.normalize(state.username) != normalized) return@update state
                        state.copy(
                            availability = UsernameAvailability.Invalid,
                            availabilityMessage = error.message ?: "Could not check CodeShell ID"
                        )
                    }
                }
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, confirmPassword = it.confirmPassword, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun signUp(
        profilePhotoUri: Uri? = null,
        onSuccess: () -> Unit,
    ) {
        val state = _uiState.value
        val validation = UsernameValidator.validateForRegistration(state.username)
        if (validation is UsernameValidator.Validation.Invalid) {
            _uiState.update { it.copy(errorMessage = validation.reason) }
            return
        }
        if (state.availability != UsernameAvailability.Available) {
            _uiState.update { it.copy(errorMessage = "Choose an available CodeShell ID") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Access key must be at least 6 characters") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Access keys do not match") }
            return
        }
        if (NetworkConnectivity.isOffline(getApplication())) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val normalized = UsernameValidator.normalize(state.username)
            val availabilityResult = userRepository.checkUsernameAvailability(normalized)
            val isAvailable = availabilityResult.getOrElse { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Could not check CodeShell ID")
                }
                return@launch
            }
            if (!isAvailable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availability = UsernameAvailability.Taken,
                        availabilityMessage = "CodeShell ID taken",
                        errorMessage = "Choose an available CodeShell ID"
                    )
                }
                return@launch
            }

            val authResult = authRepository.signUp(state.username, state.password)
            authResult.fold(
                onSuccess = { userId ->
                    val profileResult = userRepository.createUserProfile(userId, state.username)
                    profileResult.fold(
                        onSuccess = {
                            if (profilePhotoUri != null) {
                                storageRepository.uploadProfilePhoto(userId, profilePhotoUri)
                                    .onSuccess { avatarUrl ->
                                        userRepository.updateAvatarUrl(userId, avatarUrl.withAvatarCacheVersion())
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            getApplication(),
                                            "Registration completed, but the profile picture could not be saved.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                            _uiState.update { it.copy(isLoading = false) }
                            onSuccess()
                        },
                        onFailure = { profileError ->
                            authRepository.signOut()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = profileError.message ?: "Could not create profile"
                                )
                            }
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Sign up failed")
                    }
                }
            )
        }
    }

    override fun onCleared() {
        availabilityJob?.cancel()
        super.onCleared()
    }
}

private fun String.withAvatarCacheVersion(): String {
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}v=${System.currentTimeMillis()}"
}

data class SignUpUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val availability: UsernameAvailability = UsernameAvailability.Idle,
    val availabilityMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
