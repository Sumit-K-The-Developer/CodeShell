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
        return buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                val boldRange = findBoldRange(text, index)
                if (boldRange != null) {
                    val (delimiterLength, contentEnd) = boldRange
                    withStyle(baseStyle.copy(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(index + delimiterLength, contentEnd))
                    }
                    index = contentEnd + delimiterLength
                } else {
                    append(text[index])
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
