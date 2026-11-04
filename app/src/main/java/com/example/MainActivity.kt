package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.data.preferences.ThemeMode
import com.example.core.data.preferences.UserPreferences
import com.example.core.navigation.MyPrevNavGraph
import com.example.ui.theme.MyPrevTheme

class MainActivity : ComponentActivity() {

    private var incomingPdfUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android official SplashScreen before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract incoming intent URI (ACTION_VIEW, ACTION_SEND)
        handleIntent(intent)

        val appContainer = (application as MyPrevApplication).container

        setContent {
            val userPrefs by appContainer.userPreferencesRepository.userPreferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            val isDarkTheme = when (userPrefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MyPrevTheme(
                darkTheme = isDarkTheme,
                dynamicColor = userPrefs.useDynamicColor
            ) {
                MyPrevNavGraph(
                    appContainer = appContainer,
                    incomingUri = incomingPdfUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                incomingPdfUri = intent.data
            }
            Intent.ACTION_SEND -> {
                incomingPdfUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
        }
    }
}
