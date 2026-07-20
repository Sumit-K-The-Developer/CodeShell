package com.codeshell.app.data.model

data class AppConfig(
    val minVersionCode: Long = 1,
    val latestVersionCode: Long = 1,
    val discontinued: Boolean = false,
    val updateTitle: String = "Update available",
    val updateMessage: String = "A newer version of CodeShell is available.",
    val storeUrl: String = ""
)
