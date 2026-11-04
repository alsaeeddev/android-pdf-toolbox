package com.example.core.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.di.AppContainer
import com.example.feature_home.HomeScreen
import com.example.feature_home.HomeViewModel
import com.example.feature_onboarding.OnboardingScreen
import com.example.feature_settings.SettingsScreen
import com.example.feature_splash.SplashScreen
import com.example.feature_viewer.ViewerScreen
import com.example.feature_viewer.ViewerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder

val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

/**
 * Navigation Compose query argument ko pehle hi ek baar decode kar deta hai.
 * Dobara URLDecoder lagane se content URI badal jata tha (e.g. "primary%3ADownload%2Fa.pdf" ->
 * "primary:Download/a.pdf"), jis se SAF permission match nahi hoti thi aur local copy ka key bhi
 * badal jata tha. Isliye sirf tab decode karo jab value abhi tak encoded ho.
 */
private fun decodeRouteUri(raw: String): String {
    if (raw.startsWith("content://") || raw.startsWith("file://")) return raw
    return try {
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        raw
    }
}

@Composable
fun MyPrevNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController(),
    incomingUri: Uri? = null
) {
    val coroutineScope = rememberCoroutineScope()

    // SAF File Picker Launcher for PDFs
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            coroutineScope.launch {
                val result = appContainer.pdfDocumentRepository.openPdf(selectedUri, forceRefresh = true)
                val targetUri = if (result.isSuccess) result.getOrThrow().uri else selectedUri.toString()
                navController.navigate(Screen.Viewer.createRoute(targetUri))
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // 1. Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    coroutineScope.launch {
                        val prefs = appContainer.preferencesDataStore.data.first()
                        val isCompleted = prefs[ONBOARDING_COMPLETED_KEY] ?: false

                        if (incomingUri != null) {
                            val result = appContainer.pdfDocumentRepository.openPdf(incomingUri)
                            val targetUri = if (result.isSuccess) result.getOrThrow().uri else incomingUri.toString()
                            navController.navigate(Screen.Viewer.createRoute(targetUri)) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else if (isCompleted) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        // 2. Onboarding Screen
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    coroutineScope.launch {
                        appContainer.preferencesDataStore.edit { preferences ->
                            preferences[ONBOARDING_COMPLETED_KEY] = true
                        }
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 3. Home Screen
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(
                    appContainer.pdfDocumentRepository,
                    appContainer.libraryRepository
                )
            )
            val toolsViewModel: com.example.feature_tools.ToolsViewModel = viewModel(
                factory = com.example.feature_tools.ToolsViewModel.provideFactory(
                    appContainer.context,
                    appContainer.pdfDocumentRepository
                )
            )

            HomeScreen(
                viewModel = homeViewModel,
                toolsViewModel = toolsViewModel,
                onNavigateToViewer = { uriString ->
                    navController.navigate(Screen.Viewer.createRoute(uriString))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onImagesToPdf = {
                    openPdfLauncher.launch(arrayOf("application/pdf"))
                },
                onScanToPdf = {
                    openPdfLauncher.launch(arrayOf("application/pdf"))
                }
            )
        }

        // 4. Viewer Screen
        composable(
            route = "viewer?uri={uri}",
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawUri = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = decodeRouteUri(rawUri)

            val viewerViewModel: ViewerViewModel = viewModel(
                key = decodedUri,
                factory = ViewerViewModel.provideFactory(
                    context = appContainer.context,
                    uriString = decodedUri,
                    pdfRepository = appContainer.pdfDocumentRepository,
                    bookmarkDao = appContainer.bookmarkDao,
                    preferencesRepository = appContainer.userPreferencesRepository
                )
            )

            ViewerScreen(
                viewModel = viewerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEditor = { page, zoom ->
                    navController.navigate(Screen.Editor.createRoute(decodedUri, page, zoom))
                }
            )
        }

        // 5. Editor Screen
        composable(
            route = "editor?uri={uri}&page={page}&zoom={zoom}",
            arguments = listOf(
                navArgument("uri") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 1
                },
                navArgument("zoom") {
                    type = NavType.FloatType
                    defaultValue = 1f
                }
            )
        ) { backStackEntry ->
            val rawUri = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = decodeRouteUri(rawUri)
            val targetPage = backStackEntry.arguments?.getInt("page") ?: 1
            val targetZoom = backStackEntry.arguments?.getFloat("zoom") ?: 1f

            val editorViewModel: com.example.feature_editor.EditorViewModel = viewModel(
                key = "editor_${decodedUri}_$targetPage",
                factory = com.example.feature_editor.EditorViewModel.provideFactory(
                    context = appContainer.context,
                    savedStateHandle = backStackEntry.savedStateHandle.apply {
                        set("uri", decodedUri)
                        set("page", targetPage)
                        set("zoom", targetZoom)
                    },
                    pdfRepository = appContainer.pdfDocumentRepository,
                    signatureDao = appContainer.signatureDao
                )
            )

            com.example.feature_editor.ui.EditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onDocumentSaved = { savedUri ->
                    navController.popBackStack()
                    navController.navigate(Screen.Viewer.createRoute(savedUri))
                }
            )
        }

        // 6. Settings Screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                preferencesRepository = appContainer.userPreferencesRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}