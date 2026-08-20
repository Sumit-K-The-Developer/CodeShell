package com.codeshell.app.ui.aitutor

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeshell.app.R
import com.codeshell.app.data.model.AiTutorEntrySource
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import com.codeshell.app.data.model.NewsAiArticleContext
import com.codeshell.app.util.NetworkConnectivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTutorScreen(
    topicId: String,
    topicLabel: String,
    entrySource: AiTutorEntrySource,
    welcomeDigits: String,
    articleContext: NewsAiArticleContext? = null,
    onNavigateBack: () -> Unit,
) {
    BackHandler(onBack = onNavigateBack)
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val articleKey = articleContext?.url?.takeIf { it.isNotBlank() }
        ?.let { "article-${it.hashCode()}" }
        ?: "no-article"
    val viewModel: AiTutorViewModel = viewModel(
        key = "ai-tutor-$topicId-$entrySource-$welcomeDigits-$articleKey",
    ) {
        AiTutorViewModel(
            application = app,
            topicId = topicId,
            topicLabel = topicLabel,
            entrySource = entrySource,
            welcomeDigits = welcomeDigits,
            articleContext = articleContext,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isFollowingGeneration = uiState.isLoading || uiState.isTypingAnimation
    val articlePrompts = if (entrySource == AiTutorEntrySource.NEWS_ARTICLE &&
        uiState.messages.none { it.role == AiTutorMessageRole.USER } &&
        !isFollowingGeneration
    ) {
        listOf(
            "Summarise this article",
            "Give me the key points",
            "Explain this article in simple English",
            "Teach me English vocabulary from this article",
            "Teach me the grammar of simple, compound, and complex sentences from this article",
            "Find useful idioms and phrases in this article",
            "Quiz me on this article",
            "What context or background should I know?",
        )
    } else {
        emptyList()
    }

    val scrollTargetIndex by remember {
        derivedStateOf {
            if (uiState.messages.isEmpty() && !uiState.isLoading) return@derivedStateOf -1
            // Index 0 is top padding; messages occupy 1..messages.size.
            if (uiState.isLoading) uiState.messages.size + 1 else uiState.messages.size
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Scroll when the conversation structure changes, but do not react to
    // every generated character. This lets the user read older article text
    // while the answer continues generating.
    LaunchedEffect(scrollTargetIndex) {
        if (scrollTargetIndex < 0) return@LaunchedEffect
        listState.animateScrollToItem(scrollTargetIndex, scrollOffset = Int.MAX_VALUE)
    }

    LaunchedEffect(imeBottom, scrollTargetIndex) {
        if (scrollTargetIndex < 0) return@LaunchedEffect
        listState.scrollToItem(scrollTargetIndex, scrollOffset = Int.MAX_VALUE)
    }

    val title = when (entrySource) {
        AiTutorEntrySource.DISGUISE,
        AiTutorEntrySource.BOT -> stringResource(R.string.ai_tutor_title_assistant)
        AiTutorEntrySource.NEWS_ARTICLE -> stringResource(R.string.ai_tutor_news_title)
        AiTutorEntrySource.FAB -> topicLabel
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AiTutorColors.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                color = AiTutorColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if ((entrySource == AiTutorEntrySource.DISGUISE ||
                                    entrySource == AiTutorEntrySource.BOT) &&
                                topicLabel.isNotBlank()
                            ) {
                                Text(
                                    text = topicLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AiTutorColors.TextSecondary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = AiTutorColors.TextPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AiTutorColors.TopBar,
                    ),
                    actions = {
                        if (entrySource == AiTutorEntrySource.NEWS_ARTICLE) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.ai_tutor_clear_article_chat),
                                    tint = AiTutorColors.TextPrimary,
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(color = AiTutorColors.Divider)
            }
        },
        bottomBar = {
            AiTutorInputBar(
                value = uiState.inputText,
                isLoading = uiState.isLoading,
                isTypingAnimation = uiState.isTypingAnimation,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                quickPrompts = articlePrompts,
                onQuickPrompt = viewModel::sendArticlePrompt,
                onStop = viewModel::stopGeneration,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AiTutorOfflineBanner()
            if (uiState.isArticleFetching) {
                Text(
                    text = stringResource(R.string.ai_tutor_fetching_article),
                    style = MaterialTheme.typography.bodySmall,
                    color = AiTutorColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            uiState.articleFetchWarning?.let { warning ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AiTutorColors.ErrorBackground)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = warning, style = MaterialTheme.typography.bodySmall, color = AiTutorColors.TextPrimary)
                    TextButton(onClick = viewModel::retryArticleFetch) {
                        Text(stringResource(R.string.ai_tutor_retry_fetch), color = AiTutorColors.Accent)
                    }
                }
            }
            uiState.articleContext?.let { article ->
                ArticleSourceRow(article = article, onOpen = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                    }
                })
            }
            if (uiState.articleFetchComplete) {
                Text(
                    text = "Fetched",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF16A34A),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (uiState.errorMessage != null) {
                AiTutorErrorBanner(
                    errorMessage = uiState.errorMessage.orEmpty(),
                    lessonHint = uiState.lessonHint,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Box(modifier = Modifier.padding(top = 12.dp)) }
                items(uiState.messages, key = { it.id }) { message ->
                    if (message.role == AiTutorMessageRole.WELCOME) {
                        AiTutorWelcomeHeader(
                            entrySource = entrySource,
                            topicLabel = topicLabel,
                            welcomeDigits = welcomeDigits,
                        )
                    } else {
                        AiTutorMessageBubble(
                            message = message,
                            onRegenerate = viewModel::regenerateResponse,
                        )
                    }
                }
                if (uiState.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AiTutorColors.Accent,
                            )
                            Text(
                                text = stringResource(R.string.ai_tutor_thinking),
                                style = MaterialTheme.typography.bodySmall,
                                color = AiTutorColors.TextSecondary,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.ai_tutor_clear_article_chat)) },
            text = { Text(stringResource(R.string.ai_tutor_clear_article_chat_message)) },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    viewModel.clearArticleConversation()
                }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AiTutorWelcomeHeader(
    entrySource: AiTutorEntrySource,
    topicLabel: String,
    welcomeDigits: String,
) {
    val annotatedText = when (entrySource) {
        AiTutorEntrySource.FAB -> buildAnnotatedString {
            withStyle(
                AiTutorTypography.WelcomeTitle.toSpanStyle().copy(color = AiTutorColors.TextPrimary),
            ) {
                append(stringResource(R.string.ai_tutor_welcome_topic_prefix))
                append(" ")
            }
            withStyle(
                AiTutorTypography.WelcomeAccent.toSpanStyle().copy(color = AiTutorColors.Accent),
            ) {
                append(topicLabel.ifBlank { "…" })
            }
            withStyle(
                AiTutorTypography.WelcomeTitle.toSpanStyle().copy(color = AiTutorColors.TextPrimary),
            ) {
                append(".")
            }
        }
        AiTutorEntrySource.DISGUISE,
        AiTutorEntrySource.BOT -> {
            val digits = welcomeDigits.ifBlank { "…" }
            buildAnnotatedString {
                withStyle(
                    AiTutorTypography.WelcomeTitle.toSpanStyle().copy(color = AiTutorColors.TextPrimary),
                ) {
                    append(stringResource(R.string.ai_tutor_welcome_disguise_prefix))
                    append(" ")
                }
                withStyle(
                    AiTutorTypography.WelcomeAccent.toSpanStyle().copy(color = AiTutorColors.Accent),
                ) {
                    append("#$digits")
                }
                withStyle(
                    AiTutorTypography.WelcomeBody.toSpanStyle().copy(color = AiTutorColors.TextSecondary),
                ) {
                    append("\n")
                    append(stringResource(R.string.ai_tutor_welcome_disguise_suffix))
                }
            }
        }
        AiTutorEntrySource.NEWS_ARTICLE -> buildAnnotatedString {
            withStyle(
                AiTutorTypography.WelcomeTitle.toSpanStyle().copy(
                    color = AiTutorColors.TextPrimary,
                ),
            ) {
                append(topicLabel.ifBlank { "Article" })
            }
        }
    }

    Text(
        text = annotatedText,
        style = if (entrySource == AiTutorEntrySource.NEWS_ARTICLE) {
            AiTutorTypography.WelcomeTitle.copy(lineHeight = 56.sp)
        } else {
            AiTutorTypography.WelcomeTitle
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        maxLines = if (entrySource == AiTutorEntrySource.NEWS_ARTICLE) 4 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ArticleSourceRow(
    article: NewsAiArticleContext,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Source: ${article.sourceName.ifBlank { "Unknown" }}",
            style = MaterialTheme.typography.labelMedium,
            color = AiTutorColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onOpen, enabled = article.url.isNotBlank()) {
            Text(stringResource(R.string.ai_tutor_open_source), color = AiTutorColors.Accent)
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = AiTutorColors.Accent,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun AiTutorMessageBubble(
    message: AiTutorMessage,
    onRegenerate: (String) -> Unit = {},
) {
    val isUser = message.role == AiTutorMessageRole.USER
    val isAssistant = message.role == AiTutorMessageRole.ASSISTANT
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val background = when {
        message.isError -> AiTutorColors.ErrorBackground
        isUser -> AiTutorColors.UserBubble
        else -> AiTutorColors.AssistantBubble
    }
    val borderColor = if (isUser) null else AiTutorColors.Divider
    val bubbleText = when {
        isAssistant -> message.displayedContent
        else -> message.content
    }
    val renderedText = remember(bubbleText, isAssistant) {
        if (isAssistant) {
            AiTutorMarkdown.toAnnotatedString(bubbleText)
        } else {
            AnnotatedString(bubbleText)
        }
    }
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.Bottom,
            ) {
                SelectionContainer(
                    modifier = if (isAssistant) Modifier.weight(1f) else Modifier.fillMaxWidth(0.88f),
                ) {
                    Text(
                        text = renderedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (borderColor != null) {
                                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .background(background, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (message.isError) AiTutorColors.ErrorText else AiTutorColors.TextPrimary,
                    )
                }
                if (isAssistant && !message.isTyping && message.content.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = AiTutorColors.TextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(
                        onClick = { onRegenerate(message.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate response",
                            tint = AiTutorColors.TextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTutorErrorBanner(
    errorMessage: String,
    lessonHint: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiTutorColors.ErrorBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = AiTutorColors.ErrorText,
        )
        if (!lessonHint.isNullOrBlank()) {
            Text(
                text = lessonHint,
                style = MaterialTheme.typography.bodySmall,
                color = AiTutorColors.Accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AiTutorInputBar(
    value: String,
    isLoading: Boolean,
    isTypingAnimation: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    quickPrompts: List<String> = emptyList(),
    onQuickPrompt: (String) -> Unit = {},
    onStop: () -> Unit,
) {
    val isBusy = isLoading || isTypingAnimation
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AiTutorColors.Surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (quickPrompts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickPrompts.forEach { prompt ->
                    AssistChip(
                        onClick = { onQuickPrompt(prompt) },
                        label = { Text(prompt, color = Color.White) },
                    )
                }
            }
        }
        HorizontalDivider(color = AiTutorColors.Divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(R.string.ai_tutor_input_hint),
                        color = AiTutorColors.TextSecondary,
                    )
                },
                maxLines = 5,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AiTutorColors.TextPrimary,
                    unfocusedTextColor = AiTutorColors.TextPrimary,
                    cursorColor = AiTutorColors.Accent,
                    focusedBorderColor = AiTutorColors.Accent,
                    unfocusedBorderColor = AiTutorColors.Divider,
                    focusedContainerColor = AiTutorColors.InputBackground,
                    unfocusedContainerColor = AiTutorColors.InputBackground,
                ),
            )
            if (isBusy) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.ai_tutor_stop),
                        tint = AiTutorColors.Accent,
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_tutor_send),
                        tint = if (value.isNotBlank()) {
                            AiTutorColors.Accent
                        } else {
                            AiTutorColors.TextSecondary.copy(alpha = 0.4f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTutorOfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isOnline by NetworkConnectivity.observe(context.applicationContext)
        .collectAsStateWithLifecycle(initialValue = NetworkConnectivity.hasInternet(context))
    if (!isOnline) {
        Text(
            text = stringResource(R.string.no_internet_connection),
            style = MaterialTheme.typography.bodySmall,
            color = AiTutorColors.TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
