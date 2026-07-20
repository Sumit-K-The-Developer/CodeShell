package com.codeshell.app.ui.disguise.snippets

import kotlinx.serialization.Serializable

@Serializable
data class LearnSection(
    val title: String,
    val body: String,
    val examples: String,
)

@Serializable
data class LearnResourceLink(
    val title: String,
    val url: String,
)

@Serializable
data class LearnLesson(
    val sections: List<LearnSection>,
    val resources: List<LearnResourceLink> = emptyList(),
)

@Serializable
data class LearnTopic(
    val id: String,
    val label: String,
    val shortLabel: String,
)

@Serializable
data class LearnManifest(
    val version: Int,
    val topics: List<LearnTopic>,
    val lessons: Map<String, LearnLesson>,
) {
    fun lessonFor(topicId: String): LearnLesson? = lessons[topicId]

    fun randomOverlayTopic(): LearnTopic? = topics.dropLast(1).randomOrNull()
}
