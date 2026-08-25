package com.codeshell.app.ui.disguise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.codeshell.app.ui.theme.LearningColors.EditorDefaultText
import com.codeshell.app.ui.theme.LearningColors.EditorKeyword
import com.codeshell.app.ui.theme.LearningColors.EditorString
import com.codeshell.app.ui.theme.LearningColors.FabAccent

object GrammarExampleHighlighter {

    fun highlight(text: String): AnnotatedString {
        val lines = text.lines()
        return buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                appendStyledLine(line)
            }
        }
    }

    private fun AnnotatedString.Builder.appendStyledLine(line: String) {
        val trimmed = line.trimStart()
        val leadingSpaces = line.length - trimmed.length

        if (leadingSpaces > 0) {
            append(" ".repeat(leadingSpaces))
        }

        when {
            trimmed.startsWith("Example:", ignoreCase = true) ||
                trimmed.startsWith("Example sentences:", ignoreCase = true) -> {
                appendLineWithStyle(trimmed, FabAccent, bold = true)
            }
            trimmed.startsWith("Rules:", ignoreCase = true) ||
                trimmed.startsWith("Tip:", ignoreCase = true) ||
                trimmed.startsWith("Key rules", ignoreCase = true) -> {
                appendLineWithStyle(trimmed, EditorKeyword, bold = true)
            }
            trimmed.contains("देखो") || trimmed.contains("जैसे") -> {
                appendLineWithStyle(trimmed, EditorString, bold = false)
            }
            NUMBERED_ITEM.matches(trimmed) -> {
                val match = NUMBERED_ITEM.find(trimmed)!!
                val prefix = match.value
                val rest = trimmed.removePrefix(prefix)
                pushStyle(SpanStyle(color = EditorKeyword, fontWeight = FontWeight.Bold))
                append(prefix)
                pop()
                pushStyle(SpanStyle(color = EditorDefaultText))
                append(rest)
                pop()
            }
            trimmed.startsWith("Wrong:", ignoreCase = true) -> {
                appendLineWithStyle(trimmed, Color(0xFFC62828), bold = false)
            }
            trimmed.startsWith("Correct:", ignoreCase = true) ||
                trimmed.startsWith("Fixed:", ignoreCase = true) -> {
                appendLineWithStyle(trimmed, EditorString, bold = false)
            }
            trimmed.startsWith("- ") -> {
                appendLineWithStyle(trimmed, EditorDefaultText.copy(alpha = 0.92f), bold = false)
            }
            else -> {
                pushStyle(SpanStyle(color = EditorDefaultText))
                append(trimmed)
                pop()
            }
        }
    }

    private fun AnnotatedString.Builder.appendLineWithStyle(
        line: String,
        color: Color,
        bold: Boolean,
    ) {
        pushStyle(
            SpanStyle(
                color = color,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        append(line)
        pop()
    }

    private val NUMBERED_ITEM = Regex("^\\d+\\.\\s")
}
