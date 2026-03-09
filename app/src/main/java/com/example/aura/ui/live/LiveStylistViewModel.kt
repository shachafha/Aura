package com.example.aura.ui.live

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream

/**
 * Robust LiveStylistViewModel integrating real backend API and perfect "Hey Aura" tracking.
 *
 * Architecture:
 * - PASSIVE: Uses on-device `SpeechRecognizer` main-thread loop for Wake Word detection.
 * - ACTIVE: Stops Recognizer and bridges `LiveAudioRecorder` -> WebSocket `LiveRepository`.
 * - RESPONSE: `LiveRepository` WebSocket triggers `LiveAudioPlayer` with raw PCM chunks.
 */
class LiveStylistViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "AuraViewModel"
        private const val DEBOUNCE_MS = 500L
    }

    private val liveRepo = LiveRepository()
    private val audioRecorder = LiveAudioRecorder()
    private val audioPlayer = LiveAudioPlayer()
    
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── UI State ──────────────────────────────────────────

    enum class AuraState { PASSIVE_LISTENING, ACTIVE_LISTENING, PROCESSING, SPEAKING }

    private val _auraState = MutableStateFlow(AuraState.PASSIVE_LISTENING)
    val auraState: StateFlow<AuraState> = _auraState.asStateFlow()

    // Pass-through state from LiveRepository
    val chatMessages: StateFlow<List<ChatMessage>> = liveRepo.chatMessages
    val partialText: StateFlow<String> = liveRepo.partialText
    val userTranscription: StateFlow<String> = liveRepo.userTranscription
    val isConnected: StateFlow<Boolean> = liveRepo.isConnected

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _captureTrigger = MutableStateFlow(0)
    val captureTrigger: StateFlow<Int> = _captureTrigger.asStateFlow()

    // ─── Internal State for Wake Word ───────────────────────

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var recognizerBusy = false
    @Volatile private var isShutdown = false
    @Volatile private var sessionActive = false
    @Volatile private var wakeWordFired = false
    private var pendingRestart: Runnable? = null

    // Sequential audio playback channel — guarantees chunk ordering
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        // 0. Launch single sequential audio consumer (FIFO ordering guaranteed)
        viewModelScope.launch(Dispatchers.IO) {
            for (chunk in audioChannel) {
                audioPlayer.start()
                audioPlayer.playAudioChunk(chunk)
            }
        }

        // 1. Setup API response audio playback — enqueue, never launch parallel coroutines
        liveRepo.onAudioReceived = { pcmChunk ->
            _auraState.value = AuraState.SPEAKING
            _isSpeaking.value = true
            _isListening.value = false
            audioChannel.trySend(pcmChunk)
        }

        // 2. Setup AI Turn completion
        liveRepo.onTurnComplete = {
            _isSpeaking.value = false
            audioPlayer.stop()
            
            // Continuous session: restart mic recording for next turn
            if (sessionActive && !isShutdown) {
                Log.d(TAG, "Turn complete: resuming active listening")
                _auraState.value = AuraState.ACTIVE_LISTENING
                _isListening.value = true
                // Restart the audio recorder so the user can continue talking
                viewModelScope.launch {
                    audioRecorder.startRecording { pcmBytes ->
                        liveRepo.sendAudio(pcmBytes)
                    }
                }
            } else if (!isShutdown) {
                Log.d(TAG, "Turn complete: returning to passive")
                goPassive()
            }
        }

        // 3. Audio Recorder Silence
        audioRecorder.onSilenceDetected = {
            // Keep the microphone running if we're in continuous chatting!
            Log.d(TAG, "User paused speaking...")
        }

        // 4. Connect WebSocket instantly
        liveRepo.connect()

        // 5. Start our bulletproof wake word detection loop
        mainHandler.postDelayed({ goPassive() }, 1000L)

        // 6. Monitor live user transcripts to auto-trigger camera capture
        viewModelScope.launch {
            liveRepo.userTranscription.collect { text ->
                val lower = text.lowercase()
                if (sessionActive && lower.isNotBlank()) {
                    // Check for camera action keywords in the streaming text
                    if (lower.contains("analyze") || lower.contains("look at this") || lower.contains("check this") || lower.contains("click picture")) {
                        Log.d(TAG, "📸 Auto-triggering camera analysis based on voice command: $text")
                        
                        // STOP recording so Gemini focuses on the image only
                        audioRecorder.stopRecording()
                        _auraState.value = AuraState.PROCESSING
                        _isListening.value = false
                        
                        // Increment trigger to tell UI to take a frame
                        _captureTrigger.value += 1
                        
                        // Clear the active transcription so we don't trigger multiple times for the same sentence
                        liveRepo.clearUserTranscription()
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ACTIVE STATE (Live API WebSocket Audio Streaming)
    // ══════════════════════════════════════════════════════════

    private fun goActive() {
        if (isShutdown) return
        cancelPendingRestart()
        killRecognizer()
        recognizerBusy = false

        sessionActive = true
        _auraState.value = AuraState.ACTIVE_LISTENING
        _isListening.value = true
        liveRepo.clearUserTranscription()
        
        // Ensure ws connection
        if (!liveRepo.isConnected.value) liveRepo.connect()

        // Stream bytes to WebSocket
        viewModelScope.launch {
            audioRecorder.startRecording { pcmBytes ->
                liveRepo.sendAudio(pcmBytes)
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  PASSIVE STATE (On-device "Hey Aura" SpeechRecognizer)
    // ══════════════════════════════════════════════════════════

    private fun goPassive() {
        if (isShutdown) return
        cancelPendingRestart()
        
        audioRecorder.stopRecording()
        audioPlayer.stop()

        sessionActive = false
        wakeWordFired = false
        _auraState.value = AuraState.PASSIVE_LISTENING
        _isListening.value = false

        scheduleRestart(DEBOUNCE_MS) {
            if (!isShutdown && !_isSpeaking.value) {
                startRecognizerOnMain()
            }
        }
    }

    private fun scheduleRestart(delayMs: Long, action: () -> Unit) {
        cancelPendingRestart()
        val r = Runnable { action() }
        pendingRestart = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    private fun killRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "killRecognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    private fun startRecognizerOnMain() {
        if (isShutdown) return
        if (recognizerBusy) {
            scheduleRestart(DEBOUNCE_MS * 2) { startRecognizerOnMain() }
            return
        }

        recognizerBusy = true
        killRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer!!.setRecognitionListener(PassiveListener())
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer!!.startListening(intent)
            Log.d(TAG, "🎙 Passive recognizer started")
        } catch (e: Exception) {
            recognizerBusy = false
            killRecognizer()
            scheduleRestart(DEBOUNCE_MS * 3) { goPassive() }
        }
    }

    private inner class PassiveListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            recognizerBusy = false
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            recognizerBusy = false
            if (isShutdown || wakeWordFired) return

            scheduleRestart(DEBOUNCE_MS) {
                if (isShutdown || wakeWordFired) return@scheduleRestart
                goPassive()
            }
        }

        override fun onPartialResults(partial: Bundle?) {
            if (wakeWordFired) return
            val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            
            if (isWakeWord(text)) {
                wakeWordFired = true
                mainHandler.post { onWakeWordDetected() }
            }
        }

        override fun onResults(results: Bundle?) {
            recognizerBusy = false
            if (wakeWordFired) return

            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            if (isWakeWord(text)) {
                wakeWordFired = true
                mainHandler.post { onWakeWordDetected() }
            } else if (!isShutdown) {
                goPassive()
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private val wakePatterns = listOf("hey aura", "aura", "hey ora", "aurora", "hey aurora", "a aura", "hey oura", "hey ora", "hey or a")
    
    private fun isWakeWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        return wakePatterns.any { lower.contains(it) }
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "🎯 Wake word activated!")
        liveRepo.sendText("User just said Hey Aura! Greet the user warmly and say you are ready to help them!")
        goActive()
    }

    // ─── Public UI Triggers ────────────────

    fun startVoiceInput() = goActive()

    fun stopVoiceInput() = goPassive()

    fun connectLive() = liveRepo.connect()

    fun sendCameraFrame(base64Image: String) {
        if (liveRepo.isConnected.value) liveRepo.sendImage(base64Image)
    }

    fun sendCameraFrameWithPrompt(base64Image: String, prompt: String) {
        if (liveRepo.isConnected.value) liveRepo.sendImageWithText(base64Image, prompt)
    }

    fun analyzeGalleryImage(bitmap: Bitmap) {
        if (!liveRepo.isConnected.value) liveRepo.connect()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        liveRepo.sendImage(base64)
        liveRepo.sendText("I just shared a photo from my gallery. Please analyze my outfit in this image and give me styling advice!")
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (!liveRepo.isConnected.value) liveRepo.connect()

        val lower = message.lowercase()
        if (lower.contains("click picture") || lower.contains("take picture")) {
            _captureTrigger.value += 1
            liveRepo.sendText("I'm successfully taking a picture with the camera. Tell the user you've captured it.")
            return
        }

        if (lower.contains("stop listening") || lower.contains("goodbye")) {
            liveRepo.sendText("I'm leaving now! Say a short friendly goodbye!")
            stopVoiceInput()
            return
        }

        liveRepo.sendText(message)
    }

    fun reset() {
        goPassive()
    }

    override fun onCleared() {
        super.onCleared()
        isShutdown = true
        cancelPendingRestart()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { killRecognizer(); recognizerBusy = false }
        
        audioRecorder.stopRecording()
        audioPlayer.stop()
        liveRepo.disconnect()
    }
}
