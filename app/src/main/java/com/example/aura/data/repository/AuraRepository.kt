package com.example.aura.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.aura.data.local.OutfitHistoryManager
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.ClothingItem
import com.example.aura.data.model.MessageRole
import com.example.aura.data.model.OutfitAnalysis
import com.example.aura.data.remote.AuraApiService
import com.example.aura.data.remote.AnalyzeRequest
import com.example.aura.data.remote.ChatRequest
import com.example.aura.data.remote.WeatherDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * Repository that communicates with the Cloud Run backend.
 *
 * Manages: outfit analysis, chat history, weather data,
 * outfit history, and loading states.
 *
 * All ViewModels go through this repository.
 */
class AuraRepository(
    private val apiService: AuraApiService,
    private val historyManager: OutfitHistoryManager
) {
    private val _outfitAnalysis = MutableStateFlow<OutfitAnalysis?>(null)
    val outfitAnalysis: StateFlow<OutfitAnalysis?> = _outfitAnalysis.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _weather = MutableStateFlow<WeatherDto?>(null)
    /** Current weather data from the backend. */
    val weather: StateFlow<WeatherDto?> = _weather.asStateFlow()

    /** User's location (lat, lon) — set by LocationHelper. */
    var userLat: Double = 0.0
    var userLon: Double = 0.0

    /** Current outfit context (sent with each chat message). */
    private var currentOutfitDto: com.example.aura.data.remote.OutfitAnalysisDto? = null

    /**
     * Analyze an outfit image via the Cloud Run backend.
     * Backend handles Gemini Vision + weather + initial greeting.
     */
    suspend fun analyzeOutfit(image: Bitmap) {
        _isLoading.value = true
        _error.value = null

        try {
            val base64Image = bitmapToBase64(image)

            val response = apiService.analyzeOutfit(
                AnalyzeRequest(
                    imageBase64 = base64Image,
                    lat = userLat,
                    lon = userLon
                )
            )

            // Convert DTO to domain model
            val analysis = OutfitAnalysis(
                items = response.outfitAnalysis.items.map { it.toModel() },
                overallStyle = response.outfitAnalysis.overallStyle,
                dominantColors = response.outfitAnalysis.dominantColors,
                summary = response.outfitAnalysis.summary
            )

            _outfitAnalysis.value = analysis
            currentOutfitDto = response.outfitAnalysis

            // Save weather
            _weather.value = response.weather

            // Save to outfit history
            historyManager.saveOutfit(analysis)

            // Add the AI greeting as the first message
            val greeting = response.greeting.ifBlank { analysis.summary }
            _chatMessages.value = listOf(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = greeting
                )
            )

        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to analyze outfit"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Send a chat message to the ADK stylist agent on Cloud Run.
     * Backend handles Google Search grounding, weather, and outfit context.
     */
    suspend fun sendMessage(message: String) {
        val userMessage = ChatMessage(role = MessageRole.USER, content = message)
        _chatMessages.value = _chatMessages.value + userMessage

        _isLoading.value = true
        _error.value = null

        try {
            val response = apiService.chat(
                ChatRequest(
                    message = message,
                    outfitContext = currentOutfitDto,
                    outfitHistory = historyManager.getRecentHistory(),
                    lat = userLat,
                    lon = userLon
                )
            )

            val aiMessage = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = response.message,
                recommendations = response.recommendations.map { it.toModel() }.ifEmpty { null }
            )
            _chatMessages.value = _chatMessages.value + aiMessage

        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to get response"
            _chatMessages.value = _chatMessages.value + ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Sorry, I had trouble connecting. Please try again."
            )
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Clear the current session.
     */
    fun clearSession() {
        _outfitAnalysis.value = null
        _chatMessages.value = emptyList()
        _error.value = null
        _weather.value = null
        currentOutfitDto = null
    }

    /**
     * Compress and base64-encode a bitmap for the API.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxDim > 1024) {
            val scale = 1024f / maxDim
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
