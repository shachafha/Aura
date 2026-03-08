package com.example.aura.ui.live

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.data.live.LiveAudioPlayer
import com.example.aura.data.live.LiveAudioRecorder
import com.example.aura.data.live.LiveRepository
import com.example.aura.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the live stylist experience.
 *
 * Orchestrates real-time audio and vision streaming via the Gemini Live API.
 */
class LiveStylistViewModel : ViewModel() {

    private val liveRepo = LiveRepository()
    private val audioRecorder = LiveAudioRecorder()
    private val audioPlayer = LiveAudioPlayer()

    // ─── State ──────────────────────────────────────────

    /** All messages in the conversation (for transcript). */
    val chatMessages: StateFlow<List<ChatMessage>> = liveRepo.chatMessages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    val partialText: StateFlow<String> = liveRepo.partialText
    val isConnected: StateFlow<Boolean> = liveRepo.isConnected

    init {
        // Prepare audio playback
        liveRepo.onAudioReceived = { pcmChunk ->
            _isSpeaking.value = true
            audioPlayer.start()
            audioPlayer.playAudioChunk(pcmChunk)
            
            // Note: In a production app, we'd need a more precise way to know when 
            // the AI finishes speaking a full sentence vs streaming silence.
            // For now, we leave isSpeaking = true during the stream.
        }
    }

    /**
     * Connect to the Gemini Live API WebSocket.
     */
    fun connectLive() {
        liveRepo.connect()
    }

    /**
     * Start capturing microphone audio and streaming it to the backend.
     */
    fun startVoiceInput() {
        if (!liveRepo.isConnected.value) connectLive()
        
        _isListening.value = true
        _isSpeaking.value = false // Interrupt TTS if they start speaking
        audioPlayer.stop()

        viewModelScope.launch {
            audioRecorder.startRecording { pcmBytes ->
                liveRepo.sendAudio(pcmBytes)
            }
        }
    }

    /**
     * Stop capturing microphone audio.
     */
    fun stopVoiceInput() {
        _isListening.value = false
        audioRecorder.stopRecording()
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
     * Send a text message to the AI stylist.
     * The AI response is spoken aloud via TTS.
     */
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
        _isListening.value = false
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        liveRepo.disconnect()
    }
}
