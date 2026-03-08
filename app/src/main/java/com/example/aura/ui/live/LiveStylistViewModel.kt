package com.example.aura.ui.live

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.OutfitAnalysis
import com.example.aura.data.repository.AuraRepository
import com.example.aura.data.voice.VoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the live stylist experience.
 *
 * Orchestrates camera capture, Gemini analysis, voice input/output,
 * and conversation state into a single unified flow.
 */
class LiveStylistViewModel(
    private val repository: AuraRepository,
    val voiceService: VoiceService
) : ViewModel() {

    // ─── State ──────────────────────────────────────────

    /** All messages in the conversation (for transcript). */
    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages

    /** Current outfit analysis result. */
    val outfitAnalysis: StateFlow<OutfitAnalysis?> = repository.outfitAnalysis

    /** True while Gemini is processing. */
    val isLoading: StateFlow<Boolean> = repository.isLoading

    /** Error from last Gemini call. */
    val error: StateFlow<String?> = repository.error

    private val _hasAnalyzed = MutableStateFlow(false)
    /** True once the first outfit analysis is complete. */
    val hasAnalyzed: StateFlow<Boolean> = _hasAnalyzed.asStateFlow()

    private val _lastSpokenMessage = MutableStateFlow("")
    /** The most recent AI message (shown as floating transcript bubble). */
    val lastSpokenMessage: StateFlow<String> = _lastSpokenMessage.asStateFlow()

    /** Voice service state — exposed for UI. */
    val isListening: StateFlow<Boolean> = voiceService.isListening
    val isSpeaking: StateFlow<Boolean> = voiceService.isSpeaking
    val partialText: StateFlow<String> = voiceService.partialText

    init {
        // When voice recognition completes, auto-send the result to Gemini
        viewModelScope.launch {
            voiceService.recognizedText.collect { text ->
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            }
        }
    }

    /**
     * Capture and analyze an outfit image.
     * After analysis, Aura speaks the greeting aloud.
     */
    fun captureAndAnalyze(bitmap: Bitmap) {
        viewModelScope.launch {
            repository.analyzeOutfit(bitmap)

            val analysis = repository.outfitAnalysis.value
            if (analysis != null) {
                _hasAnalyzed.value = true
                val greeting = analysis.summary
                _lastSpokenMessage.value = greeting
                voiceService.speak(greeting)
            }
        }
    }

    /**
     * Start voice input (STT).
     * When recognition completes, the result is auto-sent to Gemini.
     */
    fun startVoiceInput() {
        voiceService.stopSpeaking() // Stop TTS if playing
        voiceService.startListening()
    }

    /**
     * Stop voice input.
     */
    fun stopVoiceInput() {
        voiceService.stopListening()
    }

    /**
     * Send a text message to the AI stylist.
     * The AI response is spoken aloud via TTS.
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(message.trim())

            // Speak the latest AI response
            val messages = repository.chatMessages.value
            val lastAiMessage = messages.lastOrNull {
                it.role == com.example.aura.data.model.MessageRole.ASSISTANT
            }
            if (lastAiMessage != null) {
                _lastSpokenMessage.value = lastAiMessage.content
                voiceService.speak(lastAiMessage.content)
            }
        }
    }

    /**
     * Clear session and reset for a new outfit.
     */
    fun reset() {
        repository.clearSession()
        _hasAnalyzed.value = false
        _lastSpokenMessage.value = ""
        voiceService.stopSpeaking()
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.shutdown()
    }
}
