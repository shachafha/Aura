package com.example.aura

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.aura.data.local.OutfitHistoryManager
import com.example.aura.data.remote.RetrofitClient
import com.example.aura.data.repository.AuraRepository
import com.example.aura.data.voice.VoiceService
import com.example.aura.ui.navigation.AuraNavGraph
import com.example.aura.ui.theme.AuraTheme
import com.example.aura.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Main entry point for the Aura app.
 *
 * Sets up:
 * - VoiceService for speech-to-text live agent
 * - Retrofit client pointing to Cloud Run backend
 * - AuraRepository with outfit history and API service
 * - Location helper for weather-aware styling
 * - Navigation graph
 * Requests CAMERA + RECORD_AUDIO permissions on launch.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voiceService: VoiceService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ─── Initialize dependencies ─────────────────────
        // Set backend URL (update after Cloud Run deploy)
        RetrofitClient.BASE_URL = BuildConfig.BACKEND_URL

        val apiService = RetrofitClient.instance
        val historyManager = OutfitHistoryManager(this)
        val repository = AuraRepository(apiService, historyManager)
        val locationHelper = LocationHelper(this)
        voiceService = VoiceService(this)

        // ─── Fetch location in background ────────────────
        lifecycleScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                repository.userLat = location.latitude
                repository.userLon = location.longitude
            }
        }

        // ─── Request permissions ─────────────────────────
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Permissions handled */ }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        // ─── Set up UI ───────────────────────────────────
        setContent {
            AuraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuraNavGraph(
                        repository = repository,
                        voiceService = voiceService
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceService.isInitialized) {
            voiceService.shutdown()
        }
    }
}