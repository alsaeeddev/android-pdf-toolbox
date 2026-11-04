package com.example.core.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Tools : Screen("tools")
    data object Settings : Screen("settings")

    data object Viewer : Screen("viewer?uri={uri}") {
        fun createRoute(uri: String): String = "viewer?uri=${URLEncoder.encode(uri, "UTF-8")}"
    }

    data object Editor : Screen("editor?uri={uri}&page={page}&zoom={zoom}") {
        fun createRoute(uri: String, page: Int = 1, zoom: Float = 1f): String =
            "editor?uri=${URLEncoder.encode(uri, "UTF-8")}&page=$page&zoom=$zoom"
    }
}
