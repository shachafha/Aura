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
import com.example.aura.ui.navigation.AuraNavGraph
import com.example.aura.ui.theme.AuraTheme
import com.example.aura.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Main entry point for the Aura app.
 *
 * Sets up:
 * - Retrofit client pointing to Cloud Run backend
 * - AuraRepository with outfit history and API service
 * - Location helper for weather-aware styling
 * - Navigation graph
 *
 * For hackathon speed, dependencies are created manually.
 * In production, use Hilt for DI.
 */
class MainActivity : ComponentActivity() {
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
                    AuraNavGraph(repository = repository)
                }
            }
        }
    }
}