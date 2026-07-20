package com.codeshell.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.codeshell.app.ui.theme.EditorComment
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.EditorFunction
import com.codeshell.app.ui.theme.EditorKeyword
import com.codeshell.app.ui.theme.EditorNumber
import com.codeshell.app.ui.theme.EditorString
import com.codeshell.app.ui.theme.EditorType

object SyntaxHighlighter {

    fun highlight(code: String, topicId: String): AnnotatedString {
        return when (topicId) {
            "python" -> highlightWithRules(code, PYTHON_KEYWORDS)
            "javascript" -> highlightWithRules(code, JS_KEYWORDS)
            "java" -> highlightWithRules(code, JAVA_KEYWORDS)
            "cpp" -> highlightWithRules(code, CPP_KEYWORDS)
            "kotlin" -> highlightWithRules(code, KOTLIN_KEYWORDS)
            "html_css" -> highlightHtmlCss(code)
            "sql" -> highlightWithRules(code, SQL_KEYWORDS)
            else -> highlightWithRules(code, JS_KEYWORDS)
        }
    }

    private fun highlightWithRules(code: String, keywords: Set<String>): AnnotatedString {
        return buildAnnotatedString {
            var index = 0
            while (index < code.length) {
                when {
                    code.startsWith("//", index) || code.startsWith("#", index) && !isInsideWord(code, index) -> {
                        val end = code.indexOf('\n', index).let { if (it == -1) code.length else it }
                        pushStyle(SpanStyle(color = EditorComment))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code.startsWith("--", index) -> {
                        val end = code.indexOf('\n', index).let { if (it == -1) code.length else it }
                        pushStyle(SpanStyle(color = EditorComment))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code.startsWith("/*", index) -> {
                        val end = code.indexOf("*/", index + 2).let { if (it == -1) code.length else it + 2 }
                        pushStyle(SpanStyle(color = EditorComment))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index] == '"' || code[index] == '\'' || code[index] == '`' -> {
                        val quote = code[index]
                        val end = findStringEnd(code, index, quote)
                        pushStyle(SpanStyle(color = EditorString))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index].isDigit() -> {
                        val end = code.indexOfFirstFrom(index) { !it.isDigit() && it != '.' }
                        pushStyle(SpanStyle(color = EditorNumber))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index].isLetter() || code[index] == '_' -> {
                        val end = code.indexOfFirstFrom(index) { !it.isLetterOrDigit() && it != '_' }
                        val word = code.substring(index, end)
                        val style = when {
                            keywords.contains(word) -> SpanStyle(color = EditorKeyword)
                            word.first().isUpperCase() -> SpanStyle(color = EditorType)
                            index + word.length < code.length && code[end] == '(' -> SpanStyle(color = EditorFunction)
                            else -> SpanStyle(color = EditorDefaultText)
                        }
                        pushStyle(style)
                        append(word)
                        pop()
                        index = end
                    }
                    else -> {
                        append(code[index])
                        index++
                    }
                }
            }
        }
    }

    private fun highlightHtmlCss(code: String): AnnotatedString {
        return buildAnnotatedString {
            var index = 0
            while (index < code.length) {
                when {
                    code.startsWith("<!--", index) -> {
                        val end = code.indexOf("-->", index + 4).let { if (it == -1) code.length else it + 3 }
                        pushStyle(SpanStyle(color = EditorComment))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code.startsWith("/*", index) -> {
                        val end = code.indexOf("*/", index + 2).let { if (it == -1) code.length else it + 2 }
                        pushStyle(SpanStyle(color = EditorComment))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index] == '<' -> {
                        val end = code.indexOf('>', index).let { if (it == -1) code.length else it + 1 }
                        pushStyle(SpanStyle(color = EditorKeyword))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index] == '"' || code[index] == '\'' -> {
                        val quote = code[index]
                        val end = findStringEnd(code, index, quote)
                        pushStyle(SpanStyle(color = EditorString))
                        append(code.substring(index, end))
                        pop()
                        index = end
                    }
                    code[index] == '{' || code[index] == '}' -> {
                        pushStyle(SpanStyle(color = EditorKeyword))
                        append(code[index])
                        pop()
                        index++
                    }
                    code[index].isLetter() || code[index] == '.' || code[index] == '#' -> {
                        val end = code.indexOfFirstFrom(index) { ch ->
                            !(ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '#')
                        }
                        val word = code.substring(index, end)
                        val style = when {
                            word.startsWith(".") || word.startsWith("#") -> SpanStyle(color = EditorType)
                            CSS_PROPERTIES.contains(word) -> SpanStyle(color = EditorFunction)
                            else -> SpanStyle(color = EditorDefaultText)
                        }
                        pushStyle(style)
                        append(word)
                        pop()
                        index = end
                    }
                    else -> {
                        append(code[index])
                        index++
                    }
                }
            }
        }
    }

    private fun findStringEnd(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            if (code[i] == '\\') {
                i += 2
                continue
            }
            if (code[i] == quote) return i + 1
            i++
        }
        return code.length
    }

    private fun isInsideWord(code: String, index: Int): Boolean {
        return index > 0 && (code[index - 1].isLetterOrDigit() || code[index - 1] == '_')
    }

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (i in start until length) {
            if (predicate(this[i])) return i
        }
        return length
    }

    private val PYTHON_KEYWORDS = setOf(
        "def", "return", "if", "else", "elif", "for", "in", "while", "True", "False", "None", "print", "range", "len"
    )

    private val JS_KEYWORDS = setOf(
        "let", "const", "var", "if", "else", "for", "of", "function", "return", "true", "false", "console", "log"
    )

    private val JAVA_KEYWORDS = setOf(
        "public", "class", "static", "void", "main", "String", "int", "boolean", "if", "else", "for", "return",
        "System", "out", "println"
    )

    private val CPP_KEYWORDS = setOf(
        "include", "int", "main", "std", "string", "bool", "if", "else", "for", "return", "cout", "endl"
    )

    private val KOTLIN_KEYWORDS = setOf(
        "fun", "val", "var", "if", "else", "for", "in", "return", "main", "println", "true", "false"
    )

    private val SQL_KEYWORDS = setOf(
        "CREATE", "TABLE", "INTEGER", "PRIMARY", "KEY", "TEXT", "NOT", "NULL", "INSERT", "INTO", "VALUES",
        "SELECT", "FROM", "WHERE", "ORDER", "BY", "AND", "OR", "UPDATE", "SET", "DELETE", "AS", "IS",
        "COUNT", "SUM", "AVG", "MIN", "MAX", "INNER", "JOIN", "ON", "FOREIGN", "REFERENCES", "LIKE", "LIMIT"
    )

    private val CSS_PROPERTIES = setOf(
        "body", "font-family", "font-weight", "margin", "padding", "border", "border-radius", "color",
        "background", "line-height", "style", "title", "lang", "charset", "meta", "max-width"
    )
}
