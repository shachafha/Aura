package com.example.aura.ui.live

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.data.live.LiveAudioPlayer
import com.example.aura.data.live.LiveAudioRecorder
import com.example.aura.data.live.LiveRepository
import com.example.aura.data.live.WakeWordDetector
import com.example.aura.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * ViewModel for the live stylist experience.
 *
 * Orchestrates real-time audio and vision streaming via the Gemini Live API.
 * Supports:
 * - "Hey Aura" wake word detection (always-on passive listening)
 * - Auto-stop after 5 seconds of silence
 * - Camera frame streaming
 * - Gallery image analysis
 */
class LiveStylistViewModel(context: Context) : ViewModel() {

    private val liveRepo = LiveRepository()
    private val audioRecorder = LiveAudioRecorder()
    private val audioPlayer = LiveAudioPlayer()
    val wakeWordDetector = WakeWordDetector(context)

    // ─── UI State ──────────────────────────────────────────

    enum class AuraState {
        PASSIVE_LISTENING,  // Waiting for "Hey Aura"
        ACTIVE_LISTENING,   // Streaming audio to Gemini
        PROCESSING,         // Gemini is thinking
        SPEAKING            // Gemini is speaking back
    }

    private val _auraState = MutableStateFlow(AuraState.PASSIVE_LISTENING)
    val auraState: StateFlow<AuraState> = _auraState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = liveRepo.chatMessages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    val partialText: StateFlow<String> = liveRepo.partialText
    val userTranscription: StateFlow<String> = liveRepo.userTranscription
    val isConnected: StateFlow<Boolean> = liveRepo.isConnected

    init {
        // Handle audio playback from Gemini
        liveRepo.onAudioReceived = { pcmChunk ->
            _auraState.value = AuraState.SPEAKING
            _isSpeaking.value = true
            _isListening.value = false
            audioPlayer.start()
            audioPlayer.playAudioChunk(pcmChunk)
        }

        // Handle end of Gemini's speaking turn → go back to passive listening
        liveRepo.onTurnComplete = {
            _isSpeaking.value = false
            _auraState.value = AuraState.PASSIVE_LISTENING
            audioPlayer.stop()
            // Restart passive wake word detection
            wakeWordDetector.startListening()
        }

        // When "Hey Aura" is detected → activate full mic streaming
        wakeWordDetector.onWakeWordDetected = {
            activateListening()
        }

        // When 5 seconds of silence → auto-stop mic
        audioRecorder.onSilenceDetected = {
            stopVoiceInput()
        }

        // Auto-start passive listening
        wakeWordDetector.startListening()

        // Pre-connect WebSocket immediately for zero-latency first interaction
        connectLive()
    }

    /**
     * Connect to the Gemini Live API WebSocket.
     */
    fun connectLive() {
        liveRepo.connect()
    }

    /**
     * Activate full microphone streaming (called after wake word or manual tap).
     */
    fun activateListening() {
        if (!liveRepo.isConnected.value) connectLive()

        wakeWordDetector.stopListening() // Stop passive wake word detection
        _auraState.value = AuraState.ACTIVE_LISTENING
        _isListening.value = true
        _isSpeaking.value = false
        audioPlayer.stop()
        liveRepo.clearUserTranscription()

        viewModelScope.launch {
            audioRecorder.startRecording { pcmBytes ->
                liveRepo.sendAudio(pcmBytes)
            }
        }
    }

    /**
     * Alias for activateListening (used by UI toggle).
     */
    fun startVoiceInput() {
        activateListening()
    }

    /**
     * Stop capturing microphone audio.
     */
    fun stopVoiceInput() {
        _isListening.value = false
        _auraState.value = AuraState.PROCESSING
        _isLoading.value = true
        audioRecorder.stopRecording()

        // After processing completes, Gemini will speak and onTurnComplete resets state
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _isLoading.value = false
        }
    }

    /**
     * Send a camera frame to the backend.
     */
    fun sendCameraFrame(base64Image: String) {
        if (liveRepo.isConnected.value) {
            liveRepo.sendImage(base64Image)
        }
    }

    /**
     * Analyze a gallery image by sending it over the WebSocket.
     */
    fun analyzeGalleryImage(bitmap: Bitmap) {
        if (!liveRepo.isConnected.value) connectLive()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        liveRepo.sendImage(base64)
        liveRepo.sendText("I just shared a photo from my gallery. Please analyze my outfit in this image and give me styling advice!")
    }

    /**
     * Send a text message to the AI stylist.
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (!liveRepo.isConnected.value) connectLive()
        liveRepo.sendText(message)
    }

    /**
     * Clear session and disconnect.
     */
    fun reset() {
        audioRecorder.stopRecording()
        audioPlayer.stop()
        liveRepo.disconnect()
        wakeWordDetector.stopListening()
        _isListening.value = false
        _isSpeaking.value = false
        _auraState.value = AuraState.PASSIVE_LISTENING
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        liveRepo.disconnect()
        wakeWordDetector.shutdown()
    }
}
