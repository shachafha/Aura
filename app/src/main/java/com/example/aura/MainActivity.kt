package com.example.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.aura.data.local.OutfitHistoryManager
import com.example.aura.data.remote.RetrofitClient
import com.example.aura.data.repository.AuraRepository
import com.example.aura.data.repository.MockRepository
import com.example.aura.data.voice.VoiceService
import com.example.aura.ui.navigation.AuraNavGraph
import com.example.aura.ui.theme.AuraTheme
import com.example.aura.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Main entry point for the Aura app.
 *
 * If USE_MOCK is true (default), uses MockRepository for API-key-free testing.
 * Set USE_MOCK to false in build.gradle.kts when backend is deployed.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voiceService: VoiceService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ─── Initialize dependencies ─────────────────────
        val repository: AuraRepository = if (BuildConfig.USE_MOCK) {
            // Mock mode — no API keys needed, hardcoded fashion responses
            MockRepository()
        } else {
            // Real mode — connects to Cloud Run backend
            RetrofitClient.BASE_URL = BuildConfig.BACKEND_URL
            val apiService = RetrofitClient.instance
            val historyManager = OutfitHistoryManager(this)
            AuraRepository(apiService, historyManager)
        }

        voiceService = VoiceService(this)
        val locationHelper = LocationHelper(this)

        // ─── Fetch location in background ────────────────
        lifecycleScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                repository.userLat = location.latitude
                repository.userLon = location.longitude
            }
        }

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