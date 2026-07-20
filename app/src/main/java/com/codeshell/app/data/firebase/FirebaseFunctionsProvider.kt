package com.codeshell.app.data.firebase

import com.google.firebase.functions.FirebaseFunctions

object FirebaseFunctionsProvider {
    private const val REGION = "us-central1"

    fun instance(): FirebaseFunctions = FirebaseFunctions.getInstance(REGION)
}
