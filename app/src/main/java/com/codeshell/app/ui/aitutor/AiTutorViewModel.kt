package com.codeshell.app.ui.aitutor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.R
import com.codeshell.app.data.ai.AiChatResult
import com.codeshell.app.data.model.AiTutorEntrySource
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import com.codeshell.app.data.repository.AiTutorRepository
import com.codeshell.app.ui.disguise.snippets.LearnLesson
import com.codeshell.app.util.NetworkConnectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiTutorUiState(
    val topicId: String = "",
    val topicLabel: String = "",
    val entrySource: AiTutorEntrySource = AiTutorEntrySource.FAB,
    val welcomeDigits: String = "",
    val messages: List<AiTutorMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isTypingAnimation: Boolean = false,
    val errorMessage: String? = null,
    val lessonHint: String? = null,
)

class AiTutorViewModel(
    application: Application,
    private val topicId: String,
    private val topicLabel: String,
    private val entrySource: AiTutorEntrySource,
    private val welcomeDigits: String,
    private val repository: AiTutorRepository = AiTutorRepository(),
) : AndroidViewModel(application) {

    private val lessonRepository = (application as CodeShellApplication).lessonRepository

    private fun currentLesson(): LearnLesson? {
        if (_uiState.value.entrySource == AiTutorEntrySource.BOT) return null
        return lessonRepository.manifest.value?.lessonFor(_uiState.value.topicId)
    }

    private fun usesGenericPrompt(): Boolean =
        _uiState.value.entrySource == AiTutorEntrySource.BOT ||
            _uiState.value.topicLabel.isBlank()

    private val _uiState = MutableStateFlow(
        AiTutorUiState(
            topicId = topicId,
            topicLabel = topicLabel,
            entrySource = entrySource,
            welcomeDigits = welcomeDigits,
        ),
    )
    val uiState: StateFlow<AiTutorUiState> = _uiState.asStateFlow()

    private var typingJob: Job? = null
    private var requestJob: Job? = null

    init {
        appendWelcomeMessage()
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading || _uiState.value.isTypingAnimation) return

        val context = getApplication<Application>()
        if (!NetworkConnectivity.hasInternet(context)) {
            showFailureWithLessonHint(context.getString(R.string.ai_tutor_offline))
            return
        }

        val userMessage = AiTutorMessage(role = AiTutorMessageRole.USER, content = text)
        val priorMessages = _uiState.value.messages.filter {
            it.role == AiTutorMessageRole.USER || it.role == AiTutorMessageRole.ASSISTANT
        }
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isLoading = true,
                errorMessage = null,
                lessonHint = null,
            )
        }

        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            try {
                val state = _uiState.value
                when (
                    val result = repository.sendMessage(
                        topicLabel = state.topicLabel,
                        lesson = currentLesson(),
                        priorMessages = priorMessages,
                        userText = text,
                        generic = usesGenericPrompt(),
                    )
                ) {
                    is AiChatResult.Success -> {
                        val assistantMessage = AiTutorMessage(
                            role = AiTutorMessageRole.ASSISTANT,
                            content = result.text,
                            displayedContent = "",
                            isTyping = true,
                        )
                        _uiState.update {
                            it.copy(
                                messages = it.messages + assistantMessage,
                                isLoading = false,
                                isTypingAnimation = true,
                            )
                        }
                        animateAssistantTyping(assistantMessage.id, result.text)
                    }
                    is AiChatResult.RateLimited -> rollbackUserMessage(userMessage.id, text, result.message)
                    is AiChatResult.NoApiKey -> rollbackUserMessage(
                        userMessage.id,
                        text,
                        context.getString(R.string.ai_tutor_no_api_key),
                    )
                    is AiChatResult.Error -> rollbackUserMessage(userMessage.id, text, result.message)
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun stopGeneration() {
        requestJob?.cancel()
        requestJob = null
        typingJob?.cancel()
        typingJob = null

        _uiState.update { state ->
            val updatedMessages = state.messages.mapNotNull { message ->
                if (!message.isTyping) return@mapNotNull message
                val partial = message.displayedContent
                if (partial.isBlank()) {
                    null
                } else {
                    message.copy(
                        content = partial,
                        displayedContent = partial,
                        isTyping = false,
                    )
                }
            }
            state.copy(
                messages = updatedMessages,
                isLoading = false,
                isTypingAnimation = false,
            )
        }
    }

    private fun rollbackUserMessage(userMessageId: String, failedText: String, message: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.filter { it.id != userMessageId },
                inputText = failedText,
                isLoading = false,
            )
        }
        showFailureWithLessonHint(message)
    }

    private fun animateAssistantTyping(messageId: String, fullText: String) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            val builder = StringBuilder()
            for (char in fullText) {
                builder.append(char)
                val partial = builder.toString()
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == messageId) {
                                message.copy(displayedContent = partial)
                            } else {
                                message
                            }
                        },
                    )
                }
                delay(TYPING_CHAR_DELAY_MS)
            }
            _uiState.update { state ->
                state.copy(
                    isTypingAnimation = false,
                    messages = state.messages.map { message ->
                        if (message.id == messageId) {
                            message.copy(
                                displayedContent = fullText,
                                isTyping = false,
                            )
                        } else {
                            message
                        }
                    },
                )
            }
        }
    }

    override fun onCleared() {
        requestJob?.cancel()
        typingJob?.cancel()
        super.onCleared()
    }

    private fun showFailureWithLessonHint(message: String) {
        val context = getApplication<Application>()
        val topicLabel = _uiState.value.topicLabel
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                lessonHint = if (topicLabel.isBlank()) {
                    null
                } else {
                    context.getString(R.string.ai_tutor_lesson_hint, topicLabel)
                },
            )
        }
    }

    private fun appendWelcomeMessage() {
        val context = getApplication<Application>()
        val state = _uiState.value
        val welcome = when (state.entrySource) {
            AiTutorEntrySource.FAB -> context.getString(
                R.string.ai_tutor_welcome_topic,
                state.topicLabel,
            )
            AiTutorEntrySource.DISGUISE,
            AiTutorEntrySource.BOT -> {
                val digits = state.welcomeDigits.ifBlank { "…" }
                context.getString(R.string.ai_tutor_welcome_disguise, digits)
            }
        }
        _uiState.update {
            it.copy(
                messages = listOf(
                    AiTutorMessage(
                        role = AiTutorMessageRole.WELCOME,
                        content = welcome,
                    ),
                ),
            )
        }
    }

    companion object {
        private const val TYPING_CHAR_DELAY_MS = 12L
    }
}
