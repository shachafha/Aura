package com.example.aura.ui.navigation

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aura.data.repository.AuraRepository
import com.example.aura.ui.analysis.AnalysisScreen
import com.example.aura.ui.analysis.AnalysisViewModel
import com.example.aura.ui.camera.CameraScreen
import com.example.aura.ui.camera.CameraViewModel
import com.example.aura.ui.chat.ChatScreen
import com.example.aura.ui.chat.ChatViewModel

/**
 * Navigation routes.
 */
object AuraRoutes {
    const val CAMERA = "camera"
    const val ANALYSIS = "analysis"
    const val CHAT = "chat"
}

/**
 * Main navigation graph.
 *
 * Flow: Camera → Analysis (with weather) → Chat (with weather + search)
 *
 * @param repository Shared AuraRepository (backed by Cloud Run)
 */
@Composable
fun AuraNavGraph(
    repository: AuraRepository,
    navController: NavHostController = rememberNavController()
) {
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }

    // ViewModels — manual DI for hackathon speed
    val cameraViewModel = remember { CameraViewModel() }
    val analysisViewModel = remember { AnalysisViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }

    NavHost(
        navController = navController,
        startDestination = AuraRoutes.CAMERA
    ) {
        composable(AuraRoutes.CAMERA) {
            CameraScreen(
                viewModel = cameraViewModel,
                onImageCaptured = { bitmap ->
                    capturedImage = bitmap
                    navController.navigate(AuraRoutes.ANALYSIS)
                }
            )
        }

        composable(AuraRoutes.ANALYSIS) {
            capturedImage?.let { image ->
                AnalysisScreen(
                    outfitImage = image,
                    viewModel = analysisViewModel,
                    onStartChat = {
                        navController.navigate(AuraRoutes.CHAT)
                    },
                    onRetake = {
                        repository.clearSession()
                        cameraViewModel.retake()
                        navController.popBackStack(AuraRoutes.CAMERA, inclusive = false)
                    }
                )
            }
        }

        composable(AuraRoutes.CHAT) {
            val weather by repository.weather.collectAsState()
            ChatScreen(
                viewModel = chatViewModel,
                weather = weather,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
