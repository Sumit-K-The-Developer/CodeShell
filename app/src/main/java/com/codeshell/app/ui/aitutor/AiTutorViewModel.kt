package com.codeshell.app.ui.aitutor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.R
import com.codeshell.app.data.ai.AiChatResult
import com.codeshell.app.data.local.NewsAiConversationRepository
import com.codeshell.app.data.local.toDomain
import com.codeshell.app.data.local.newsAiConversationKey
import com.codeshell.app.data.model.AiTutorEntrySource
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import com.codeshell.app.data.model.NewsAiArticleContext
import com.codeshell.app.data.repository.AiTutorRepository
import com.codeshell.app.data.news.NewsRepository
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
    val articleContext: NewsAiArticleContext? = null,
    val articleFetchWarning: String? = null,
    val articleFetchComplete: Boolean = false,
)

class AiTutorViewModel(
    application: Application,
    private val topicId: String,
    private val topicLabel: String,
    private val entrySource: AiTutorEntrySource,
    private val welcomeDigits: String,
    private val articleContext: NewsAiArticleContext? = null,
    private val repository: AiTutorRepository = AiTutorRepository(),
) : AndroidViewModel(application) {

    private val lessonRepository = (application as CodeShellApplication).lessonRepository
    private val articleConversationRepository = NewsAiConversationRepository(application)

    private fun currentLesson(): LearnLesson? {
        if (_uiState.value.entrySource == AiTutorEntrySource.BOT) return null
        return lessonRepository.manifest.value?.lessonFor(_uiState.value.topicId)
    }

    private fun usesGenericPrompt(): Boolean =
        _uiState.value.entrySource == AiTutorEntrySource.BOT ||
            _uiState.value.entrySource == AiTutorEntrySource.NEWS_ARTICLE ||
            _uiState.value.topicLabel.isBlank()

    private val _uiState = MutableStateFlow(
        AiTutorUiState(
            topicId = topicId,
            topicLabel = topicLabel,
            entrySource = entrySource,
            welcomeDigits = welcomeDigits,
            articleContext = articleContext,
        ),
    )
    val uiState: StateFlow<AiTutorUiState> = _uiState.asStateFlow()

    private var typingJob: Job? = null
    private var requestJob: Job? = null

    init {
        if (entrySource == AiTutorEntrySource.NEWS_ARTICLE && articleContext != null) {
            restoreArticleConversation()
        } else {
            appendWelcomeMessage()
        }
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
        persistArticleConversation()

        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            if (entrySource == AiTutorEntrySource.NEWS_ARTICLE) {
                resolveArticleContext()
            }
            sendMessageInternal(text, priorMessages, userMessage.id)
        }
    }

    fun sendArticlePrompt(prompt: String) {
        if (entrySource != AiTutorEntrySource.NEWS_ARTICLE ||
            _uiState.value.isLoading ||
            _uiState.value.isTypingAnimation ||
            prompt.isBlank()
        ) return
        val userMessage = AiTutorMessage(role = AiTutorMessageRole.USER, content = prompt)
        val priorMessages = _uiState.value.messages.filter {
            it.role == AiTutorMessageRole.USER || it.role == AiTutorMessageRole.ASSISTANT
        }
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                errorMessage = null,
            )
        }
        persistArticleConversation()
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            resolveArticleContext()
            sendMessageInternal(prompt, priorMessages, userMessage.id)
        }
    }

    private suspend fun resolveArticleContext() {
        val article = _uiState.value.articleContext ?: articleContext ?: return
        if (article.fullArticleFetched) return
        val fetched = NewsRepository().fetchArticleContent(article.url)
        val context = fetched.getOrNull()?.takeIf { it.isNotBlank() }
        val warning = if (context == null) {
                getApplication<Application>().getString(R.string.ai_tutor_article_fetch_warning)
            } else {
                null
            }
        _uiState.update {
            it.copy(
                articleContext = article.copy(
                    cachedText = context ?: article.cachedText,
                    fullArticleFetched = context != null,
                ),
                articleFetchWarning = warning,
                articleFetchComplete = context != null,
            )
        }
        persistArticleConversation()
    }

    private suspend fun sendMessageInternal(
        text: String,
        priorMessages: List<AiTutorMessage>,
        userMessageId: String,
    ) {
        val context = getApplication<Application>()
        try {
            val state = _uiState.value
            when (
                val result = repository.sendMessage(
                    topicLabel = state.topicLabel,
                    lesson = currentLesson(),
                    priorMessages = priorMessages,
                    userText = text,
                    generic = usesGenericPrompt(),
                    articleContext = state.articleContext,
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
                    is AiChatResult.RateLimited -> rollbackUserMessage(userMessageId, text, result.message)
                    is AiChatResult.NoApiKey -> rollbackUserMessage(
                        userMessageId,
                        text,
                        context.getString(R.string.ai_tutor_no_api_key),
                    )
                    is AiChatResult.Error -> rollbackUserMessage(userMessageId, text, result.message)
            }
        } catch (_: CancellationException) {
            _uiState.update { it.copy(isLoading = false) }
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
        persistArticleConversation()
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
        persistArticleConversation()
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
            persistArticleConversation()
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
            AiTutorEntrySource.BOT,
            AiTutorEntrySource.NEWS_ARTICLE -> {
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

    private fun restoreArticleConversation() {
        val article = articleContext ?: return
        viewModelScope.launch {
            val stored = articleConversationRepository.load(
                newsAiConversationKey(
                    url = article.url,
                    title = article.title,
                    sourceName = article.sourceName,
                    publishedAt = article.publishedAt,
                ),
            )
            val restoredMessages = stored?.messages?.mapNotNull { it.toDomain() }
            if (restoredMessages.isNullOrEmpty()) {
                appendWelcomeMessage()
                return@launch
            }
            _uiState.update {
                it.copy(
                    messages = restoredMessages,
                    articleContext = stored.articleContext?.toDomain() ?: article,
                )
            }
        }
    }

    private fun persistArticleConversation() {
        if (entrySource != AiTutorEntrySource.NEWS_ARTICLE) return
        val article = _uiState.value.articleContext ?: articleContext ?: return
        viewModelScope.launch {
            articleConversationRepository.save(
                articleKey = newsAiConversationKey(
                    url = article.url,
                    title = article.title,
                    sourceName = article.sourceName,
                    publishedAt = article.publishedAt,
                ),
                articleContext = article,
                messages = _uiState.value.messages,
            )
        }
    }

    companion object {
        private const val TYPING_CHAR_DELAY_MS = 12L
    }
}
