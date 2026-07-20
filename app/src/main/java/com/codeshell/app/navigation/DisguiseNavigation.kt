package com.codeshell.app.navigation

fun rootDestination(noDisguiseMode: Boolean, isLoggedIn: Boolean): String = when {
    noDisguiseMode && isLoggedIn -> Routes.home()
    noDisguiseMode -> Routes.LOGIN
    else -> Routes.CODING_LEARN
}
