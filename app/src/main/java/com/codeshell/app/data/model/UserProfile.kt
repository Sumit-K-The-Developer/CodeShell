package com.codeshell.app.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val fcmToken: String = "",
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)
