package com.example.aura.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.aura.data.local.OutfitHistoryManager
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.MessageRole
import com.example.aura.data.model.OutfitAnalysis
import com.example.aura.data.remote.AuraApiService
import com.example.aura.data.remote.AnalyzeRequest
import com.example.aura.data.remote.ChatRequest
import com.example.aura.data.remote.OutfitAnalysisDto
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
 * Made [open] so [MockRepository] can override for API-key-free testing.
 */
open class AuraRepository(
    private val apiService: AuraApiService?,
    private val historyManager: OutfitHistoryManager?
) {
    protected val _outfitAnalysis = MutableStateFlow<OutfitAnalysis?>(null)
    open val outfitAnalysis: StateFlow<OutfitAnalysis?> = _outfitAnalysis.asStateFlow()

    protected val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    open val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    protected val _isLoading = MutableStateFlow(false)
    open val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    protected val _error = MutableStateFlow<String?>(null)
    open val error: StateFlow<String?> = _error.asStateFlow()

    protected val _weather = MutableStateFlow<WeatherDto?>(null)
    open val weather: StateFlow<WeatherDto?> = _weather.asStateFlow()

    /** User's location (lat, lon) — set by LocationHelper. */
    var userLat: Double = 0.0
    var userLon: Double = 0.0

    /** Current outfit context (sent with each chat message). */
    private var currentOutfitDto: OutfitAnalysisDto? = null

    /**
     * Analyze an outfit image via the Cloud Run backend.
     */
    open suspend fun analyzeOutfit(image: Bitmap) {
        _isLoading.value = true
        _error.value = null

        try {
            val base64Image = bitmapToBase64(image)

            val response = apiService!!.analyzeOutfit(
                AnalyzeRequest(
                    imageBase64 = base64Image,
                    lat = userLat,
                    lon = userLon
                )
            )

            val analysis = OutfitAnalysis(
                items = response.outfitAnalysis.items.map { it.toModel() },
                overallStyle = response.outfitAnalysis.overallStyle,
                dominantColors = response.outfitAnalysis.dominantColors,
                summary = response.outfitAnalysis.summary
            )

            _outfitAnalysis.value = analysis
            currentOutfitDto = response.outfitAnalysis
            _weather.value = response.weather

            historyManager?.saveOutfit(analysis)

            val greeting = response.greeting.ifBlank { analysis.summary }
            _chatMessages.value = listOf(
                ChatMessage(role = MessageRole.ASSISTANT, content = greeting)
            )
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to analyze outfit"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Send a chat message to the ADK stylist agent on Cloud Run.
     */
    open suspend fun sendMessage(message: String) {
        val userMessage = ChatMessage(role = MessageRole.USER, content = message)
        _chatMessages.value = _chatMessages.value + userMessage

        _isLoading.value = true
        _error.value = null

        try {
            val response = apiService!!.chat(
                ChatRequest(
                    message = message,
                    outfitContext = currentOutfitDto,
                    outfitHistory = historyManager?.getRecentHistory() ?: emptyList(),
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
    open fun clearSession() {
        _outfitAnalysis.value = null
        _chatMessages.value = emptyList()
        _error.value = null
        _weather.value = null
        currentOutfitDto = null
    }

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
