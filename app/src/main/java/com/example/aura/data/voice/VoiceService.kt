package com.example.aura.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Unified voice service wrapping Android's [SpeechRecognizer] (STT) and [TextToSpeech] (TTS).
 *
 * Usage:
 * - Call [startListening] to begin speech recognition. Observe [recognizedText] for results.
 * - Call [speak] to have Aura speak text aloud. Observe [isSpeaking] for state.
 * - Call [shutdown] when done to release resources.
 *
 * @param context Application or Activity context
 */
class VoiceService(private val context: Context) {

    // ─── State ──────────────────────────────────────────

    private val _isListening = MutableStateFlow(false)
    /** True while the microphone is actively capturing speech. */
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    /** True while TTS is playing audio. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    /** The most recent fully recognized speech result. */
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    /** Partial (in-progress) recognition text for live display. */
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Error message from the most recent STT/TTS operation. */
    val error: StateFlow<String?> = _error.asStateFlow()

    // ─── Speech-to-Text ─────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null

    private val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            _partialText.value = ""
            _error.value = null
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(errorCode: Int) {
            _isListening.value = false
            _error.value = when (errorCode) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                else -> "Speech recognition error ($errorCode)"
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) {
                _recognizedText.value = text
                _partialText.value = ""
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            _partialText.value = matches?.firstOrNull()?.trim() ?: ""
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Start listening for speech input.
     * Results arrive via [recognizedText] StateFlow.
     * Must be called from the main thread.
     */
    fun startListening() {
        if (_isListening.value) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }

        _recognizedText.value = ""
        _partialText.value = ""
        speechRecognizer?.startListening(recognitionIntent)
    }

    /**
     * Stop listening for speech input.
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    // ─── Text-to-Speech ─────────────────────────────────

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Callback invoked when TTS finishes speaking. */
    var onSpeakingComplete: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.05f) // Slightly faster for natural feel
                ttsReady = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        onSpeakingComplete?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    /**
     * Speak text aloud using TTS.
     * Observe [isSpeaking] to know when speech completes.
     *
     * @param text The text to speak
     * @param interrupt If true, stops any current speech before starting
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (!ttsReady || text.isBlank()) return

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()

        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Stop any ongoing TTS playback.
     */
    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Release all resources. Call when the service is no longer needed.
     */
    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
