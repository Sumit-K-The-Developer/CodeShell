package com.codeshell.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.ui.util.WindowLayout
import com.codeshell.app.ui.theme.ChatDarkAccent
import com.codeshell.app.ui.theme.ChatDarkBackground
import com.codeshell.app.ui.theme.ChatDarkBubbleMine
import com.codeshell.app.ui.theme.ChatDarkBubbleOther
import com.codeshell.app.ui.theme.ChatDarkMutedText
import com.codeshell.app.ui.theme.ChatDarkText
import com.codeshell.app.ui.theme.ChatDarkTopBar
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorGutter
import com.codeshell.app.ui.theme.EditorSenderText
import com.codeshell.app.ui.theme.EditorTileBorder
import com.codeshell.app.ui.theme.EditorTileText
import com.codeshell.app.ui.theme.IdeLightAccent
import com.codeshell.app.ui.theme.IdeLightBackground
import com.codeshell.app.ui.theme.IdeLightBubbleMine
import com.codeshell.app.ui.theme.IdeLightBubbleOther
import com.codeshell.app.ui.theme.IdeLightMutedText
import com.codeshell.app.ui.theme.IdeLightText
import com.codeshell.app.ui.theme.IdeLightTopBar
import com.codeshell.app.ui.theme.EditorType
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.theme.VintageDarkAccent
import com.codeshell.app.ui.theme.VintageDarkBackground
import com.codeshell.app.ui.theme.VintageDarkBubbleMine
import com.codeshell.app.ui.theme.VintageDarkBubbleOther
import com.codeshell.app.ui.theme.VintageDarkMutedText
import com.codeshell.app.ui.theme.VintageDarkText
import com.codeshell.app.ui.theme.VintageDarkTopBar

@Immutable
data class ChatThemeColors(
    val background: androidx.compose.ui.graphics.Color,
    val topBar: androidx.compose.ui.graphics.Color,
    val text: androidx.compose.ui.graphics.Color,
    val senderText: androidx.compose.ui.graphics.Color = text,
    val mutedText: androidx.compose.ui.graphics.Color,
    val tileBorder: androidx.compose.ui.graphics.Color = mutedText,
    val bubbleMine: androidx.compose.ui.graphics.Color,
    val bubbleOther: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
)

@Immutable
data class ChatTheme(
    val style: ChatInterfaceStyle,
    val colors: ChatThemeColors,
    val bubbleMaxWidth: Dp,
    val bubbleCornerLarge: Dp,
    val bubbleCornerSmall: Dp,
    val useAsymmetricBubbleTail: Boolean,
    val mediaPreviewHeight: Dp,
    val mediaPreviewCorner: Dp,
    val mediaBorderWidth: Dp,
    val inputBarCorner: Dp,
    val useMonospaceText: Boolean,
    val compactMediaAttachments: Boolean,
    val messageFontScale: Float = 1f,
)

@Composable
fun rememberChatTheme(
    style: ChatInterfaceStyle,
    messageFontScale: Float = ChatMessageFontScale.DEFAULT,
): ChatTheme {
    return remember(style, messageFontScale) {
        buildChatTheme(style, messageFontScale)
    }
}

@Composable
fun rememberChatColors(): ChatThemeColors {
    return rememberChatTheme(ChatInterfaceStyle.DEFAULT).colors
}

@Composable
fun rememberAdaptiveChatTheme(
    style: ChatInterfaceStyle,
    messageFontScale: Float = ChatMessageFontScale.DEFAULT,
): ChatTheme {
    val base = rememberChatTheme(style, messageFontScale)
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return remember(base, screenWidthDp) {
        base.copy(
            bubbleMaxWidth = WindowLayout.chatBubbleMaxWidth(base.bubbleMaxWidth, screenWidthDp),
        )
    }
}


private fun buildChatTheme(style: ChatInterfaceStyle, messageFontScale: Float): ChatTheme {
    val colors = chatThemeColors(style)
    val scale = ChatMessageFontScale.clamp(messageFontScale)
    return when (style) {
        ChatInterfaceStyle.DEFAULT -> ChatTheme(
            style = style,
            colors = colors,
            bubbleMaxWidth = 280.dp,
            bubbleCornerLarge = 12.dp,
            bubbleCornerSmall = 4.dp,
            useAsymmetricBubbleTail = true,
            mediaPreviewHeight = 120.dp,
            mediaPreviewCorner = 6.dp,
            mediaBorderWidth = 1.dp,
            inputBarCorner = 24.dp,
            useMonospaceText = false,
            compactMediaAttachments = false,
            messageFontScale = scale,
        )
        ChatInterfaceStyle.VINTAGE -> ChatTheme(
            style = style,
            colors = colors,
            bubbleMaxWidth = 300.dp,
            bubbleCornerLarge = 18.dp,
            bubbleCornerSmall = 18.dp,
            useAsymmetricBubbleTail = false,
            mediaPreviewHeight = 140.dp,
            mediaPreviewCorner = 12.dp,
            mediaBorderWidth = 0.dp,
            inputBarCorner = 24.dp,
            useMonospaceText = false,
            compactMediaAttachments = false,
            messageFontScale = scale,
        )
        ChatInterfaceStyle.IDE -> ChatTheme(
            style = style,
            colors = colors,
            bubbleMaxWidth = 320.dp,
            bubbleCornerLarge = 4.dp,
            bubbleCornerSmall = 4.dp,
            useAsymmetricBubbleTail = false,
            mediaPreviewHeight = 80.dp,
            mediaPreviewCorner = 2.dp,
            mediaBorderWidth = 1.dp,
            inputBarCorner = 4.dp,
            useMonospaceText = true,
            compactMediaAttachments = true,
            messageFontScale = scale,
        )
        ChatInterfaceStyle.IDE_LIGHT -> ChatTheme(
            style = style,
            colors = colors,
            bubbleMaxWidth = 320.dp,
            bubbleCornerLarge = 4.dp,
            bubbleCornerSmall = 4.dp,
            useAsymmetricBubbleTail = false,
            mediaPreviewHeight = 80.dp,
            mediaPreviewCorner = 2.dp,
            mediaBorderWidth = 1.dp,
            inputBarCorner = 4.dp,
            useMonospaceText = true,
            compactMediaAttachments = true,
            messageFontScale = scale,
        )
    }
}

private fun chatThemeColors(style: ChatInterfaceStyle): ChatThemeColors {
    return when (style) {
        ChatInterfaceStyle.DEFAULT -> ChatThemeColors(
            background = ChatDarkBackground,
            topBar = ChatDarkTopBar,
            text = ChatDarkText,
            mutedText = ChatDarkMutedText,
            bubbleMine = ChatDarkBubbleMine,
            bubbleOther = ChatDarkBubbleOther,
            accent = ChatDarkAccent,
        )
        ChatInterfaceStyle.VINTAGE -> ChatThemeColors(
            background = VintageDarkBackground,
            topBar = VintageDarkTopBar,
            text = VintageDarkText,
            mutedText = VintageDarkMutedText,
            bubbleMine = VintageDarkBubbleMine,
            bubbleOther = VintageDarkBubbleOther,
            accent = VintageDarkAccent,
        )
        ChatInterfaceStyle.IDE -> ChatThemeColors(
            background = EditorBackground,
            topBar = TopBarBackground,
            text = EditorTileText,
            senderText = EditorSenderText,
            mutedText = EditorGutter,
            tileBorder = EditorTileBorder,
            bubbleMine = ChatDarkBubbleMine,
            bubbleOther = EditorGutter,
            accent = EditorType,
        )
        ChatInterfaceStyle.IDE_LIGHT -> ChatThemeColors(
            background = IdeLightBackground,
            topBar = IdeLightTopBar,
            text = IdeLightText,
            mutedText = IdeLightMutedText,
            bubbleMine = IdeLightBubbleMine,
            bubbleOther = IdeLightBubbleOther,
            accent = IdeLightAccent,
        )
    }
}
