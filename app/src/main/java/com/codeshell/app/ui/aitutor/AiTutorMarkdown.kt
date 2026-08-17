package com.codeshell.app.ui.aitutor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object AiTutorMarkdown {
    private val BOLD_DELIMITERS = listOf("**", "__")

    fun toAnnotatedString(
        text: String,
        baseStyle: SpanStyle = SpanStyle(),
    ): AnnotatedString {
        // Keep the model's lightweight Markdown readable in a plain Compose
        // Text. Normalize common heading/list variants before applying inline
        // bold styling, so responses remain structured even when the model
        // uses '# Heading' or '* item' instead of the requested format.
        val normalizedText = text
            .lineSequence()
            .joinToString("\n") { line ->
                val trimmed = line.trimStart()
                when {
                    trimmed.matches(Regex("#{1,3}\\s+.+")) ->
                        "**${trimmed.replaceFirst(Regex("^#{1,3}\\s+"), "").trim()}**"
                    trimmed.matches(Regex("[-*•]\\s+.+")) ->
                        "• ${trimmed.replaceFirst(Regex("^[-*•]\\s+"), "")}" 
                    else -> line
                }
            }
        return buildAnnotatedString {
            var index = 0
            while (index < normalizedText.length) {
                val boldRange = findBoldRange(normalizedText, index)
                if (boldRange != null) {
                    val (delimiterLength, contentEnd) = boldRange
                    withStyle(baseStyle.copy(fontWeight = FontWeight.SemiBold)) {
                        append(normalizedText.substring(index + delimiterLength, contentEnd))
                    }
                    index = contentEnd + delimiterLength
                } else {
                    append(normalizedText[index])
                    index++
                }
            }
        }
    }

    private fun findBoldRange(text: String, fromIndex: Int): Pair<Int, Int>? {
        for (delimiter in BOLD_DELIMITERS) {
            if (!text.startsWith(delimiter, fromIndex)) continue
            val contentStart = fromIndex + delimiter.length
            val closeIndex = text.indexOf(delimiter, contentStart)
            if (closeIndex > contentStart) {
                return delimiter.length to closeIndex
            }
        }
        return null
    }
}
