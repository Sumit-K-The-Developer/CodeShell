package com.codeshell.app.data.model

sealed class UserSearchResult {
    data class Found(val profile: UserProfile) : UserSearchResult()
    data object NotFound : UserSearchResult()
    data object InBlockList : UserSearchResult()
}
