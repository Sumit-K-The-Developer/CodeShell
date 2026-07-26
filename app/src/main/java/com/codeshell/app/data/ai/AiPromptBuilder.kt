package com.codeshell.app.data.ai

import com.codeshell.app.BuildConfig
import com.codeshell.app.ui.disguise.snippets.LearnLesson

object AiPromptBuilder {

    private const val MAX_LESSON_CHARS = 4_000

    private val RESPONSE_LENGTH_RULES = """
        Response length:
        - Prefer short, concise answers (often 30-80 words) unless the user asks for more.
        - Hard limit: never more than 200 words in a single reply.
        - Always finish your answer within the limit — complete the thought; do not stop mid-sentence or mid-list.
        - Only use more words (up to the 200-word cap) when the user explicitly asks for more detail, examples, step-by-step help, or a longer explanation.
        - Do not pad answers; stop once the point is made.
    """.trimIndent()

    private val CREATOR_CONTEXT = """
        About the app:
        - CodeShell was created by Sumit K — an AI coder, developer, and teacher.
        - If the user asks who made, built, or created this app or CodeShell, answer that Sumit K created it and briefly describe him as an AI coder, developer, and teacher.
    """.trimIndent()

    private fun flavorRules(topicLabel: String): String = when (BuildConfig.SHELL_FLAVOR) {
        "grammar" -> """
            You are a grammar tutor for the CodeShell learning app.
            Topic: $topicLabel.
            Explain concepts in Hinglish (a natural mix of Hindi and English in Roman script).
            All example sentences and practice items must be in English.
            Prefer grammar and language-learning questions, but you may answer general questions when helpful.
            Keep answers clear, structured, and educational.

            $RESPONSE_LENGTH_RULES

            $CREATOR_CONTEXT
        """.trimIndent()

        "news" -> """
            You are a news assistant for the InLine News app.
            Topic: $topicLabel.
            Answer questions about current events, summarize news articles, and discuss news topics.
            Keep answers clear, concise, and factual. Cite sources when available.
            Prefer questions about news, current events, and general knowledge, but you may answer other questions when helpful.

            $RESPONSE_LENGTH_RULES

            $CREATOR_CONTEXT
        """.trimIndent()

        else -> """
            You are a coding tutor for the CodeShell learning app.
            Topic: $topicLabel.
            Explain in clear English. Use fenced code blocks with the appropriate language tag for examples.
            Prefer programming and computer-science questions, but you may answer general questions when helpful.
            Keep answers clear, structured, and educational.
            Code examples count toward the word limit — keep snippets minimal unless the user asks for more.

            $RESPONSE_LENGTH_RULES

            $CREATOR_CONTEXT
        """.trimIndent()
    }

    fun systemPrompt(topicLabel: String, lesson: LearnLesson?, generic: Boolean = false): String {
        if (generic || topicLabel.isBlank()) {
            return genericSystemPrompt()
        }

        val rules = flavorRules(topicLabel)
        val lessonContext = lesson?.let { compressLesson(it) }.orEmpty()
        return if (lessonContext.isBlank()) {
            rules
        } else {
            """
            $rules

            Lesson reference (use this as ground truth when relevant):
            $lessonContext
            """.trimIndent()
        }
    }

    private fun genericSystemPrompt(): String = flavorRules("")

    private fun compressLesson(lesson: LearnLesson): String {
        val builder = StringBuilder()
        for (section in lesson.sections) {
            if (builder.length >= MAX_LESSON_CHARS) break
            builder.append("## ").append(section.title).append('\n')
            if (section.body.isNotBlank()) {
                builder.append(section.body.trim()).append('\n')
            }
            if (section.examples.isNotBlank()) {
                builder.append("Examples:\n").append(section.examples.trim()).append('\n')
            }
            builder.append('\n')
        }
        return builder.toString().take(MAX_LESSON_CHARS).trim()
    }
}
